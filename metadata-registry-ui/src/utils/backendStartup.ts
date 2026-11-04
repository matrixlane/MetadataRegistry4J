/**
 * 应用启动探测：Vue 应用挂载前确认后端启动完成（含数据初始化）。
 *
 * start.bat 启动后，后端需要数秒至数十秒完成 Spring Boot 启动；
 * 此期间任何业务请求都会失败并弹出"服务器内部错误"。本模块以裸 fetch
 * 轮询就绪探针（不经 axios 拦截器，不弹错误提示），就绪后由 main.ts
 * 挂载应用并移除 index.html 中的启动屏。
 *
 * 就绪语义：/api/v1/startup/status 返回 ready=true，即 ApplicationReadyEvent
 * 已发布（SpringApplication.run 全部阶段完成，含所有数据初始化 Runner）。
 *
 * 初始化失败语义（init-phase-database-free）：探针返回 ready=false 且携带
 * failure（阶段、原因码、可读描述）时，启动屏切换为失败面板，提供
 * 「重试初始化」与「退出应用」两个动作；不再按"仍在启动"无限等待。
 *
 * 向后兼容：旧版本后端无该端点（401/403/404），退化为探测
 * /api/v1/init-wizard/status 可达即视为就绪。
 */

const STARTUP_STATUS_URL = '/api/v1/startup/status';
const FALLBACK_STATUS_URL = '/api/v1/init-wizard/status';
const BOOTSTRAP_RETRY_URL = '/api/v1/bootstrap/retry';
const BOOTSTRAP_EXIT_URL = '/api/v1/bootstrap/exit';
const POLL_INTERVAL_MS = 1000;
const REQUEST_TIMEOUT_MS = 2500;
const TIMER_TICK_MS = 500;
const SLOW_THRESHOLD_MS = 60_000;

/** 引导失败信息（与后端 BootstrapFailure 对应） */
export interface StartupFailure {
  stage: string;
  code: string;
  message: string;
}

/** 项目统一响应包（ApiResponse）的就绪探针相关字段子集 */
interface StartupStatusBody {
  code?: number;
  data?: { ready?: boolean; failure?: StartupFailure | null };
}

interface ProbeResult {
  status: number;
  body: StartupStatusBody | null;
}

/** 就绪探测结论：ready 为已就绪；failure 为初始化失败（未失败时为 null） */
export interface ProbeOutcome {
  ready: boolean;
  failure: StartupFailure | null;
}

async function fetchJson(url: string): Promise<ProbeResult> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const response = await fetch(url, { signal: controller.signal, cache: 'no-store' });
    let body: StartupStatusBody | null = null;
    try {
      body = await response.json();
    } catch {
      // 非 JSON 响应（如代理 500 错误页），保持 body=null
    }
    return { status: response.status, body };
  } finally {
    clearTimeout(timer);
  }
}

/** 单轮探测：就绪 / 初始化失败 / 未就绪（继续等待）三种结论。 */
export async function probeOnce(): Promise<ProbeOutcome> {
  let startup: ProbeResult;
  try {
    startup = await fetchJson(STARTUP_STATUS_URL);
  } catch {
    return { ready: false, failure: null };
  }

  if (startup.status === 200) {
    const data = startup.body?.data;
    if (startup.body?.code === 0 && data?.ready === true) {
      return { ready: true, failure: null };
    }
    const failure = data?.failure ?? null;
    return { ready: false, failure };
  }

  // 旧版本后端未提供该端点：security entry point 拒绝匿名访问，退化为探测向导状态端点
  if (startup.status === 401 || startup.status === 403 || startup.status === 404) {
    try {
      const fallbackReady = (await fetchJson(FALLBACK_STATUS_URL)).status === 200;
      return { ready: fallbackReady, failure: null };
    } catch {
      return { ready: false, failure: null };
    }
  }

  // 500（后端未监听、代理失败）等一律视为未就绪
  return { ready: false, failure: null };
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** 重试初始化：调用后端引导入口（仅本机回环可用）。失败静默（下一轮探测会再暴露）。 */
async function requestBootstrapRetry(): Promise<void> {
  try {
    await fetch(BOOTSTRAP_RETRY_URL, { method: 'POST', cache: 'no-store' });
  } catch {
    // 静默：后端可能尚未监听，下一轮探测继续
  }
}

/** 确认退出：调度后端退出（启动器随之结束托管）。 */
async function requestBootstrapExit(): Promise<void> {
  try {
    await fetch(BOOTSTRAP_EXIT_URL, { method: 'POST', cache: 'no-store' });
  } catch {
    // 静默：尽力退出
  }
}

function ensureFailureStyles(): void {
  if (document.getElementById('boot-failure-styles')) return;
  const style = document.createElement('style');
  style.id = 'boot-failure-styles';
  style.textContent = `
    #boot-screen .boot-failure-title { margin: 18px 0 8px; font-size: 20px; font-weight: 700; color: #b91c1c; }
    #boot-screen .boot-failure-reason { margin: 0 0 6px; font-size: 14px; color: #374151; line-height: 1.6; word-break: break-all; }
    #boot-screen .boot-failure-hint { margin: 0 0 18px; font-size: 12px; color: #6b7280; line-height: 1.6; }
    #boot-screen .boot-actions { display: flex; gap: 12px; justify-content: center; }
    #boot-screen .boot-actions button {
      padding: 9px 20px; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; border: 1px solid transparent;
    }
    #boot-screen #boot-retry { background: #16a34a; color: #fff; }
    #boot-screen #boot-retry:disabled { background: #9ca3af; cursor: not-allowed; }
    #boot-screen #boot-exit { background: #fff; color: #374151; border-color: #d1d5db; }
    #boot-screen .boot-exited { margin: 18px 0 0; font-size: 14px; color: #374151; }
  `;
  document.head.appendChild(style);
}

/**
 * 展示"初始化系统失败"面板，等待用户选择「重试初始化」或「退出应用」。
 * 返回用户动作；退出后展示"已退出"提示并保持页面不卸载应用。
 */
function presentFailureScreen(failure: StartupFailure): Promise<'retry' | 'exit'> {
  ensureFailureStyles();
  return new Promise((resolve) => {
    const screen = document.getElementById('boot-screen');
    if (!screen) {
      resolve('retry');
      return;
    }
    screen.innerHTML = `
      <div class="boot-card">
        <img class="boot-logo" src="/favicon.svg" width="56" height="56" alt="logo" />
        <h2 class="boot-failure-title">初始化系统失败</h2>
        <p class="boot-failure-reason">${escapeHtml(failure.message || failure.code || '未知原因')}</p>
        <p class="boot-failure-hint">数据库初始化未完成，应用暂时无法使用。<br/>可重试初始化，或退出应用后离线处理。</p>
        <div class="boot-actions">
          <button id="boot-retry" type="button">重试初始化</button>
          <button id="boot-exit" type="button">退出应用</button>
        </div>
      </div>`;
    const retryButton = document.getElementById('boot-retry');
    const exitButton = document.getElementById('boot-exit');
    retryButton?.addEventListener('click', () => {
      retryButton.setAttribute('disabled', 'disabled');
      retryButton.textContent = '正在重试…';
      resolve('retry');
    });
    exitButton?.addEventListener('click', () => {
      resolve('exit');
      requestBootstrapExit().then(() => {
        const card = screen.querySelector('.boot-card');
        if (card) {
          card.innerHTML = `
            <img class="boot-logo" src="/favicon.svg" width="56" height="56" alt="logo" />
            <h2 class="boot-failure-title">应用已退出</h2>
            <p class="boot-exited">后端正在停止，稍后请关闭本页面；问题处理后重新运行 start.bat 即可。</p>`;
        }
      });
    });
  });
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * 轮询直至后端就绪。期间每 500ms 更新启动屏计时，
 * 超过 60s 显示慢启动排查提示；初始化失败时切换到失败面板等待用户动作；
 * 启动屏 DOM 不存在时静默跳过。
 */
export async function waitForBackendReady(): Promise<void> {
  const startedAt = Date.now();
  const timerEl = document.getElementById('boot-timer');
  const slowTipEl = document.getElementById('boot-slow-tip');

  const timer = setInterval(() => {
    const elapsed = Date.now() - startedAt;
    if (timerEl) {
      timerEl.textContent = `已等待 ${Math.floor(elapsed / 1000)} 秒`;
    }
    if (slowTipEl && elapsed >= SLOW_THRESHOLD_MS) {
      slowTipEl.hidden = false;
    }
  }, TIMER_TICK_MS);

  try {
    while (true) {
      const probe = await probeOnce();
      if (probe.ready) {
        return;
      }
      if (probe.failure) {
        // 已知失败原因：展示失败面板，等用户重试或退出，不再无限等待
        const action = await presentFailureScreen(probe.failure);
        if (action === 'exit') {
          return;
        }
        await requestBootstrapRetry();
        // 重试后回到轮询：就绪或再次失败（失败会重新弹出面板）
        continue;
      }
      await sleep(POLL_INTERVAL_MS);
    }
  } finally {
    clearInterval(timer);
  }
}
