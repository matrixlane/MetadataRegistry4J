import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import request from './request';
import { useAuthStore } from '@/stores/auth';

const summaryMock = vi.hoisted(() => ({ value: vi.fn() }));

vi.mock('@/api/resources', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/resources')>();
  return {
    ...actual,
    workspaceApi: { ...actual.workspaceApi, summary: summaryMock.value },
  };
});

/** 构造一个直接返回成功业务包体的 axios adapter，避免真实网络请求 */
function adapterWith(body: unknown): AxiosAdapter {
  return (config) =>
    Promise.resolve({
      data: body,
      status: 200,
      statusText: 'OK',
      headers: {},
      config: config as InternalAxiosRequestConfig,
    } as AxiosResponse);
}

describe('utils/request 徽标联动', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    summaryMock.value.mockReset();
    summaryMock.value.mockResolvedValue({ drafts: 0, pending: 0, rejected: 0 });
    setActivePinia(createPinia());
    useAuthStore().token = 'test-token';
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('成功的写操作响应后去抖刷新工作空间徽标', async () => {
    await request.post('/data-elements', {}, { adapter: adapterWith({ code: 0, data: {} }) });
    await vi.advanceTimersByTimeAsync(300);
    expect(summaryMock.value).toHaveBeenCalledTimes(1);
  });

  it('成功的读操作不触发徽标刷新', async () => {
    await request.get('/data-elements', { adapter: adapterWith({ code: 0, data: {} }) });
    await vi.advanceTimersByTimeAsync(300);
    expect(summaryMock.value).not.toHaveBeenCalled();
  });

  it('业务错误响应（code 非 0）不触发徽标刷新', async () => {
    await expect(
      request.post('/data-elements', {}, { adapter: adapterWith({ code: 1, message: '失败' }) }),
    ).rejects.toThrow('失败');
    await vi.advanceTimersByTimeAsync(300);
    expect(summaryMock.value).not.toHaveBeenCalled();
  });
});
