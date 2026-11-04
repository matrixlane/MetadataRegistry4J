import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises } from '@vue/test-utils';
import type { DatasourceRestartStatus } from '@/api/datasourceRestart';
import {
  DATASOURCE_RESTART_SESSION_KEY,
  DATASOURCE_RESTART_TIMEOUT_MS,
  clearRestartSession,
  createDatasourceRestartMonitor,
  createRestartId,
  getOrCreateRestartSession,
  readRestartSession,
  writeRestartSession,
  type DatasourceRestartMonitor,
  type DatasourceRestartSnapshot,
} from './datasourceRestart';

function restartStatus(
  state: DatasourceRestartStatus['state'],
  verification: DatasourceRestartStatus['verification'] = 'PENDING',
  reasonCode: string | null = null
): DatasourceRestartStatus {
  return { restartId: 'restart-1', state, verification, reasonCode };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe('数据源重启会话标记', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('使用浏览器原生 UUID 生成 restartId', () => {
    const randomUUID = vi.fn().mockReturnValue('550e8400-e29b-41d4-a716-446655440000');
    vi.stubGlobal('crypto', { randomUUID });

    expect(createRestartId()).toBe('550e8400-e29b-41d4-a716-446655440000');
    expect(randomUUID).toHaveBeenCalledTimes(1);
  });

  it('请求前只保存四项非敏感恢复信息，同一操作复用原 ID', () => {
    const first = getOrCreateRestartSession('PERSONAL', {
      storage: sessionStorage,
      now: () => 1234,
      createId: () => 'restart-1',
    });
    const second = getOrCreateRestartSession('PERSONAL', {
      storage: sessionStorage,
      now: () => 9999,
      createId: () => 'restart-2',
    });

    expect(first).toEqual({
      restartId: 'restart-1',
      edition: 'PERSONAL',
      step: 1,
      startedAt: 1234,
    });
    expect(second).toEqual(first);
    expect(readRestartSession(sessionStorage)).toEqual(first);
    expect(sessionStorage.getItem(DATASOURCE_RESTART_SESSION_KEY)).toBe(JSON.stringify(first));
    expect(sessionStorage.getItem(DATASOURCE_RESTART_SESSION_KEY)).not.toMatch(
      /password|jdbc|backup|secret|sqlserver/i
    );
  });

  it('写入时忽略配置字段，终态后清除标记', () => {
    writeRestartSession({
      restartId: 'restart-1',
      edition: 'PERSONAL',
      step: 1,
      startedAt: 1234,
      password: 'secret',
      jdbcUrl: 'jdbc:sqlserver://db',
    } as never, sessionStorage);

    expect(Object.keys(JSON.parse(sessionStorage.getItem(DATASOURCE_RESTART_SESSION_KEY)!))).toEqual([
      'restartId',
      'edition',
      'step',
      'startedAt',
    ]);
    clearRestartSession(sessionStorage);
    expect(readRestartSession(sessionStorage)).toBeNull();
  });

  it('损坏或缺字段的标记不会触发恢复', () => {
    sessionStorage.setItem(DATASOURCE_RESTART_SESSION_KEY, '{broken');
    expect(readRestartSession(sessionStorage)).toBeNull();
    sessionStorage.setItem(DATASOURCE_RESTART_SESSION_KEY, JSON.stringify({ restartId: 'restart-1' }));
    expect(readRestartSession(sessionStorage)).toBeNull();
  });
});

describe('数据源重启静默轮询', () => {
  const monitors: DatasourceRestartMonitor[] = [];
  let updates: DatasourceRestartSnapshot[];
  let onUpdate: (snapshot: DatasourceRestartSnapshot) => void;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(0);
    updates = [];
    onUpdate = (snapshot) => updates.push(snapshot);
  });

  afterEach(() => {
    monitors.forEach((monitor) => monitor.stop());
    monitors.length = 0;
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  function monitor(
    getStatus: (restartId: string, signal?: AbortSignal) => Promise<DatasourceRestartStatus>,
    probeStartup: (signal?: AbortSignal) => Promise<'READY' | 'PREPARING' | 'UNAVAILABLE'>,
    options: { now?: () => number; startedAt?: number; timeoutMs?: number } = {}
  ) {
    const instance = createDatasourceRestartMonitor({
      restartId: 'restart-1',
      startedAt: options.startedAt ?? 0,
      timeoutMs: options.timeoutMs ?? 10_000,
      pollIntervalMs: 1000,
      now: options.now,
      getStatus,
      probeStartup,
      onUpdate,
    });
    monitors.push(instance);
    return instance;
  }

  it('ready=true 后仍等待核对，只有 SUCCEEDED + MATCHED 才成功', async () => {
    const getStatus = vi.fn()
      .mockResolvedValueOnce(restartStatus('VERIFYING'))
      .mockResolvedValueOnce(restartStatus('SUCCEEDED', 'MATCHED'));
    const probeStartup = vi.fn().mockResolvedValue('READY');
    const instance = monitor(getStatus, probeStartup);

    instance.start();
    await flushPromises();
    expect(updates.at(-1)?.phase).toBe('VERIFYING');

    await vi.advanceTimersByTimeAsync(1000);
    expect(updates.at(-1)?.phase).toBe('SUCCEEDED');
    expect(getStatus).toHaveBeenCalledTimes(2);
  });

  it('网络拒绝和暂时 5xx 继续等待，ready=false 展示数据库准备', async () => {
    const getStatus = vi.fn()
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockRejectedValueOnce({ response: { status: 503 } })
      .mockResolvedValueOnce(restartStatus('STARTING'));
    const probeStartup = vi.fn()
      .mockResolvedValueOnce('UNAVAILABLE')
      .mockResolvedValueOnce('PREPARING')
      .mockResolvedValueOnce('PREPARING');
    const instance = monitor(getStatus, probeStartup);

    instance.start();
    await flushPromises();
    expect(updates.at(-1)?.phase).toBe('RESTARTING');
    await vi.advanceTimersByTimeAsync(1000);
    expect(updates.at(-1)?.phase).toBe('PREPARING');
    await vi.advanceTimersByTimeAsync(1000);
    expect(updates.at(-1)?.phase).toBe('PREPARING');
    expect(getStatus).toHaveBeenCalledTimes(3);
  });

  it('MISMATCHED 不会误报成功，随后进入恢复并返回 ROLLED_BACK', async () => {
    const getStatus = vi.fn()
      .mockResolvedValueOnce(restartStatus('VERIFYING', 'MISMATCHED'))
      .mockResolvedValueOnce(restartStatus('ROLLING_BACK', 'MISMATCHED'))
      .mockResolvedValueOnce(restartStatus('ROLLED_BACK', 'NOT_REQUIRED', 'TARGET_MISMATCHED'));
    const probeStartup = vi.fn().mockResolvedValue('READY');
    const instance = monitor(getStatus, probeStartup);

    instance.start();
    await flushPromises();
    expect(updates.at(-1)?.phase).toBe('RECOVERING');
    await vi.advanceTimersByTimeAsync(1000);
    expect(updates.at(-1)?.phase).toBe('RECOVERING');
    await vi.advanceTimersByTimeAsync(1000);
    expect(updates.at(-1)).toMatchObject({ phase: 'ROLLED_BACK', reasonCode: 'TARGET_MISMATCHED' });
  });

  it('RECOVERY_FAILED 终止等待并保留机器原因码', async () => {
    const getStatus = vi.fn().mockResolvedValue(
      restartStatus('RECOVERY_FAILED', 'NOT_REQUIRED', 'OLD_CONFIG_START_FAILED')
    );
    const probeStartup = vi.fn().mockResolvedValue('UNAVAILABLE');
    const instance = monitor(getStatus, probeStartup);

    instance.start();
    await flushPromises();

    expect(updates.at(-1)).toMatchObject({ phase: 'FAILED', reasonCode: 'OLD_CONFIG_START_FAILED' });
    await vi.advanceTimersByTimeAsync(5000);
    expect(getStatus).toHaveBeenCalledTimes(1);
  });

  it('总等待窗口覆盖持久化 startedAt，超时后可用同一 ID 重试连接', async () => {
    let now = 0;
    const getStatus = vi.fn().mockResolvedValue(restartStatus('STARTING'));
    const probeStartup = vi.fn().mockResolvedValue('PREPARING');
    const instance = monitor(getStatus, probeStartup, {
      now: () => now,
      timeoutMs: 5000,
    });

    instance.start();
    await flushPromises();
    now = 5000;
    await vi.advanceTimersByTimeAsync(1000);
    expect(updates.at(-1)).toMatchObject({ phase: 'FAILED', reasonCode: 'WAIT_TIMEOUT' });

    now = 10_000;
    getStatus.mockResolvedValue(restartStatus('SUCCEEDED', 'MATCHED'));
    instance.retry(now);
    await flushPromises();
    expect(updates.at(-1)?.phase).toBe('SUCCEEDED');
    expect(getStatus.mock.calls.every(([id]) => id === 'restart-1')).toBe(true);
  });

  it('恢复标记不会借刷新获得新的总等待窗口', async () => {
    const getStatus = vi.fn();
    const probeStartup = vi.fn();
    const instance = monitor(getStatus, probeStartup, {
      now: () => DATASOURCE_RESTART_TIMEOUT_MS,
      startedAt: 0,
      timeoutMs: DATASOURCE_RESTART_TIMEOUT_MS,
    });

    instance.start();
    await flushPromises();

    expect(updates.at(-1)).toMatchObject({ phase: 'FAILED', reasonCode: 'WAIT_TIMEOUT' });
    expect(getStatus).not.toHaveBeenCalled();
    expect(probeStartup).not.toHaveBeenCalled();
  });

  it('请求悬挂时也由独立截止计时器结束总等待并中止请求', async () => {
    let statusSignal: AbortSignal | undefined;
    const getStatus = vi.fn().mockImplementation((_id: string, signal?: AbortSignal) => {
      statusSignal = signal;
      return new Promise<DatasourceRestartStatus>(() => {});
    });
    const probeStartup = vi.fn().mockReturnValue(new Promise<'READY'>(() => {}));
    const instance = monitor(getStatus, probeStartup, { timeoutMs: 5000 });

    instance.start();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(5000);

    expect(updates.at(-1)).toMatchObject({ phase: 'FAILED', reasonCode: 'WAIT_TIMEOUT' });
    expect(statusSignal?.aborted).toBe(true);
  });

  it('stop 后忽略未完成请求且不再安排轮询', async () => {
    const statusDeferred = deferred<DatasourceRestartStatus>();
    const probeDeferred = deferred<'READY' | 'PREPARING' | 'UNAVAILABLE'>();
    const getStatus = vi.fn().mockReturnValue(statusDeferred.promise);
    const probeStartup = vi.fn().mockReturnValue(probeDeferred.promise);
    const instance = monitor(getStatus, probeStartup);

    instance.start();
    const updateCount = updates.length;
    instance.stop();
    statusDeferred.resolve(restartStatus('SUCCEEDED', 'MATCHED'));
    probeDeferred.resolve('READY');
    await flushPromises();
    await vi.advanceTimersByTimeAsync(5000);

    expect(updates).toHaveLength(updateCount);
    expect(getStatus).toHaveBeenCalledTimes(1);
    expect(probeStartup).toHaveBeenCalledTimes(1);
  });
});
