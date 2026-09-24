import { readonly, ref } from 'vue';
import { workspaceApi } from '@/api/resources';

/**
 * 工作空间未提交数量徽标（模块级单例）：右上角入口的红点数字。
 * 计数 = 当前用户归属且管理状态为草稿（DRAFT）的 Item 数。
 * WorkspaceEntry 负责启动轮询；工作空间页内的提交/回退等变更操作后调用 refresh() 即时刷新。
 */
const POLL_INTERVAL_MS = 30_000;

const count = ref(0);
let timer: ReturnType<typeof setInterval> | null = null;
let pollers = 0;

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

export function useWorkspaceBadge() {
  return {
    count: readonly(count),
    refresh,
    startPolling,
    stopPolling,
  };
}
