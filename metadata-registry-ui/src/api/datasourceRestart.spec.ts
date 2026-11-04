import { beforeEach, describe, expect, it, vi } from 'vitest';
import { datasourceRestartApi, type DatasourceConfigRequest } from './datasourceRestart';

const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));
const withSilentErrorsMock = vi.hoisted(() =>
  vi.fn(<T>(operation: () => Promise<T>): Promise<T> => operation())
);

vi.mock('@/utils/request', () => ({
  default: requestMock,
  withSilentErrors: withSilentErrorsMock,
}));

const config: DatasourceConfigRequest = {
  vendor: 'sqlserver',
  host: 'localhost',
  port: 1433,
  database: 'metadata_registry',
  filePath: '',
  username: null,
  password: 'secret',
  edition: 'PERSONAL',
};

describe('datasourceRestartApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('查询自动重启能力且不经过全局错误提示', async () => {
    requestMock.get.mockResolvedValue({ supported: true, reasonCode: null });

    await expect(datasourceRestartApi.getCapability()).resolves.toEqual({
      supported: true,
      reasonCode: null,
    });
    expect(requestMock.get).toHaveBeenCalledWith('/datasource/restart-capability');
    expect(withSilentErrorsMock).toHaveBeenCalledTimes(1);
  });

  it('以客户端 restartId 和嵌套配置提交自动应用', async () => {
    requestMock.post.mockResolvedValue({ restartId: 'restart-1' });

    await expect(datasourceRestartApi.apply('restart-1', config)).resolves.toEqual({
      restartId: 'restart-1',
    });
    expect(requestMock.post).toHaveBeenCalledWith('/datasource/config/apply', {
      restartId: 'restart-1',
      config,
    });
    expect(withSilentErrorsMock).toHaveBeenCalledTimes(1);
  });

  it('按原 restartId 静默查询状态并传递取消信号', async () => {
    const controller = new AbortController();
    requestMock.get.mockResolvedValue({
      restartId: 'restart-1',
      state: 'VERIFYING',
      verification: 'PENDING',
      reasonCode: null,
    });

    await datasourceRestartApi.getRestartStatus('restart-1', controller.signal);

    expect(requestMock.get).toHaveBeenCalledWith('/datasource/restarts/restart-1', {
      signal: controller.signal,
    });
    expect(withSilentErrorsMock).toHaveBeenCalledTimes(1);
  });
});
