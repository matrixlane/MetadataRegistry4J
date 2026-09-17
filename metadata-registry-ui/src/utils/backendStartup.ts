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
 * 向后兼容：旧版本后端无该端点（401/403/404），退化为探测
 * /api/v1/init-wizard/status 可达即视为就绪。
 */

const STARTUP_STATUS_URL = '/api/v1/startup/status';
const FALLBACK_STATUS_URL = '/api/v1/init-wizard/status';
const POLL_INTERVAL_MS = 1000;
const REQUEST_TIMEOUT_MS = 2500;
const TIMER_TICK_MS = 500;
const SLOW_THRESHOLD_MS = 60_000;

/** 项目统一响应包（ApiResponse）的就绪探针相关字段子集 */
interface StartupStatusBody {
  code?: number;
  data?: { ready?: boolean };
}

interface ProbeResult {
  status: number;
  body: StartupStatusBody | null;
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

/** 返回 true 表示后端已就绪；任何异常或否定信号都返回 false（继续等待）。 */
async function probeOnce(): Promise<boolean> {
  let startup: ProbeResult;
  try {
    startup = await fetchJson(STARTUP_STATUS_URL);
  } catch {
    return false;
  }

  if (startup.status === 200) {
    return startup.body?.code === 0 && startup.body?.data?.ready === true;
  }

  // 旧版本后端未提供该端点：security entry point 拒绝匿名访问，退化为探测向导状态端点
  if (startup.status === 401 || startup.status === 403 || startup.status === 404) {
    try {
      return (await fetchJson(FALLBACK_STATUS_URL)).status === 200;
    } catch {
      return false;
    }
  }

  // 500（后端未监听、代理失败）等一律视为未就绪
  return false;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * 轮询直至后端就绪。期间每 500ms 更新启动屏计时，
 * 超过 60s 显示慢启动排查提示；启动屏 DOM 不存在时静默跳过。
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
      if (await probeOnce()) {
        return;
      }
      await sleep(POLL_INTERVAL_MS);
    }
  } finally {
    clearInterval(timer);
  }
}
