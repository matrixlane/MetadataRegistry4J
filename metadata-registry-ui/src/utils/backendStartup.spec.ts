import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { waitForBackendReady } from './backendStartup';

function jsonResponse(status: number, body: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response;
}

const READY_BODY = { code: 0, message: 'OK', data: { ready: true } };
const NOT_READY_BODY = { code: 0, message: 'OK', data: { ready: false } };

describe('backendStartup 启动探测', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
  });

  it('后端已就绪时立即返回，仅探测一次', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, READY_BODY));
    vi.stubGlobal('fetch', fetchMock);

    const promise = waitForBackendReady();
    await expect(promise).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/startup/status');
  });

  it('未就绪时按 1s 间隔轮询，直至就绪', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(200, NOT_READY_BODY))
      .mockResolvedValueOnce(jsonResponse(200, NOT_READY_BODY))
      .mockResolvedValueOnce(jsonResponse(200, READY_BODY));
    vi.stubGlobal('fetch', fetchMock);

    const promise = waitForBackendReady();
    await vi.advanceTimersByTimeAsync(2000);
    await expect(promise).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('启动端点 500（后端未监听）视为未就绪并继续轮询', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(500, {}))
      .mockResolvedValueOnce(jsonResponse(200, READY_BODY));
    vi.stubGlobal('fetch', fetchMock);

    const promise = waitForBackendReady();
    await vi.advanceTimersByTimeAsync(1000);
    await expect(promise).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('网络错误（fetch reject）视为未就绪并继续轮询', async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(jsonResponse(200, READY_BODY));
    vi.stubGlobal('fetch', fetchMock);

    const promise = waitForBackendReady();
    await vi.advanceTimersByTimeAsync(1000);
    await expect(promise).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('旧版后端（401/403/404）退化探测 /init-wizard/status 可达即就绪', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: 401 }))
      .mockResolvedValueOnce(jsonResponse(200, { code: 0, data: { initialized: true } }));
    vi.stubGlobal('fetch', fetchMock);

    const promise = waitForBackendReady();
    await expect(promise).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/startup/status');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/init-wizard/status');
  });

  it('等待超过 60s 显示慢启动提示', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, NOT_READY_BODY));
    vi.stubGlobal('fetch', fetchMock);

    document.body.innerHTML = `
      <div id="boot-screen">
        <span id="boot-timer"></span>
        <div id="boot-slow-tip" hidden></div>
      </div>`;

    const promise = waitForBackendReady();
    await vi.advanceTimersByTimeAsync(30_000);
    expect(document.getElementById('boot-slow-tip')!.hidden).toBe(true);
    await vi.advanceTimersByTimeAsync(31_000);
    expect(document.getElementById('boot-slow-tip')!.hidden).toBe(false);
    expect(document.getElementById('boot-timer')!.textContent).toContain('61');

    // 未就绪期间 promise 不应解决；清理挂起的轮询
    let settled = false;
    promise.then(() => { settled = true; });
    await vi.advanceTimersByTimeAsync(1000);
    expect(settled).toBe(false);
  });

  it('就绪后 promise 解决，即使启动屏 DOM 不存在也不报错', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, READY_BODY));
    vi.stubGlobal('fetch', fetchMock);

    await expect(waitForBackendReady()).resolves.toBeUndefined();
  });
});
