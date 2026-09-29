import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useWorkspaceBadge } from './useWorkspaceBadge';
import { useAuthStore } from '@/stores/auth';

const summaryMock = vi.hoisted(() => ({ value: vi.fn() }));

vi.mock('@/api/resources', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/resources')>();
  return {
    ...actual,
    workspaceApi: { ...actual.workspaceApi, summary: summaryMock.value },
  };
});

describe('useWorkspaceBadge', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    summaryMock.value.mockReset();
    setActivePinia(createPinia());
    useAuthStore().token = 'test-token';
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('notifyMutation 在去抖窗口结束后刷新计数', async () => {
    summaryMock.value.mockResolvedValue({ drafts: 7, pending: 0, rejected: 0 });
    const { notifyMutation, count } = useWorkspaceBadge();

    notifyMutation();
    // 去抖窗口内不立即请求
    await vi.advanceTimersByTimeAsync(299);
    expect(summaryMock.value).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(1);
    expect(summaryMock.value).toHaveBeenCalledTimes(1);
    expect(count.value).toBe(7);
  });

  it('去抖窗口内多次变更只触发一次 summary 请求', async () => {
    summaryMock.value.mockResolvedValue({ drafts: 2, pending: 0, rejected: 0 });
    const { notifyMutation } = useWorkspaceBadge();

    notifyMutation();
    await vi.advanceTimersByTimeAsync(100);
    notifyMutation();
    await vi.advanceTimersByTimeAsync(100);
    notifyMutation();
    await vi.advanceTimersByTimeAsync(300);

    expect(summaryMock.value).toHaveBeenCalledTimes(1);
  });

  it('未登录（无令牌）时跳过刷新，避免 summary 401 触发全局登出', async () => {
    useAuthStore().token = '';
    const { notifyMutation } = useWorkspaceBadge();

    notifyMutation();
    await vi.advanceTimersByTimeAsync(500);

    expect(summaryMock.value).not.toHaveBeenCalled();
  });

  it('summary 请求失败时保留上一次计数', async () => {
    summaryMock.value
      .mockResolvedValueOnce({ drafts: 4, pending: 0, rejected: 0 })
      .mockRejectedValueOnce(new Error('network'));
    const { notifyMutation, count } = useWorkspaceBadge();

    notifyMutation();
    await vi.advanceTimersByTimeAsync(300);
    expect(count.value).toBe(4);

    notifyMutation();
    await vi.advanceTimersByTimeAsync(300);
    expect(count.value).toBe(4);
  });
});
