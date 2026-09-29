import { readonly, ref } from 'vue';
import { workspaceApi } from '@/api/resources';
import { useAuthStore } from '@/stores/auth';

/**
 * 工作空间未提交数量徽标（模块级单例）：右上角入口的红点数字。
 * 计数 = 当前用户归属且管理状态为草稿（DRAFT）的 Item 数。
 * WorkspaceEntry 负责启动轮询；HTTP 层在任意成功的写操作响应后调用 notifyMutation()
 * 去抖即时刷新（覆盖列表页新建/删除草稿等工作空间页之外的变更入口）。
 */
const POLL_INTERVAL_MS = 30_000;
/** 连续写操作（批量删除、保存后配对同步等）的去抖窗口：窗口内多次变更只触发一次 summary 请求 */
const MUTATION_REFRESH_DEBOUNCE_MS = 300;

const count = ref(0);
let timer: ReturnType<typeof setInterval> | null = null;
let pollers = 0;
let mutationTimer: ReturnType<typeof setTimeout> | null = null;

async function refresh(): Promise<void> {
  try {
    const summary = await workspaceApi.summary();
    count.value = summary.drafts ?? 0;
  } catch {
    // 网络/未登录等失败时保留上一次计数，不打断用户操作
  }
}

function startPolling(): void {
  pollers += 1;
  if (timer === null) {
    void refresh();
    timer = setInterval(() => void refresh(), POLL_INTERVAL_MS);
  }
}

function stopPolling(): void {
  pollers = Math.max(0, pollers - 1);
  if (pollers === 0 && timer !== null) {
    clearInterval(timer);
    timer = null;
  }
}

/**
 * 任意成功写操作后由 HTTP 层调用：去抖结束后重新从服务端同步计数。
 * 去抖窗口结束时尚无登录令牌（如登录/初始化向导请求本身）则跳过，
 * 避免 summary 请求 401 触发全局登出与跳转。
 */
function notifyMutation(): void {
  if (mutationTimer) clearTimeout(mutationTimer);
  mutationTimer = setTimeout(() => {
    mutationTimer = null;
    if (!useAuthStore().token) return;
    void refresh();
  }, MUTATION_REFRESH_DEBOUNCE_MS);
}

export function useWorkspaceBadge() {
  return {
    count: readonly(count),
    refresh,
    notifyMutation,
    startPolling,
    stopPolling,
  };
}
