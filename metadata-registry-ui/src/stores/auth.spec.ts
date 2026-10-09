import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import request from '@/utils/request';
import { useAuthStore } from './auth';

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn() },
  withSilentErrors: vi.fn((fn: () => unknown) => fn()),
}));

const mockedGet = vi.mocked(request.get);

// 免登录与版本状态语义（personal-no-login-registrar-default）
describe('stores/auth 个人版免登录', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.removeItem('token');
  });

  it('token 存在时 isLoggedIn 为 true（团队版语义不变）', () => {
    const auth = useAuthStore();
    auth.token = 'jwt-token';
    expect(auth.isLoggedIn).toBe(true);
    expect(auth.bypassLogin).toBe(false);
  });

  it('PERSONAL 已初始化时 bypassLogin 为 true 且免登录', () => {
    const auth = useAuthStore();
    auth.setInitialized(true);
    auth.setEdition('PERSONAL');
    expect(auth.bypassLogin).toBe(true);
    expect(auth.isLoggedIn).toBe(true);
  });

  it('edition 非 PERSONAL 时不 bypass（团队版仍须登录）', () => {
    const auth = useAuthStore();
    auth.setInitialized(true);
    auth.setEdition('TEAM');
    expect(auth.bypassLogin).toBe(false);
    expect(auth.isLoggedIn).toBe(false);
  });

  it('未初始化 PERSONAL 不 bypass（应先走向导）', () => {
    const auth = useAuthStore();
    auth.setInitialized(false);
    auth.setEdition('PERSONAL');
    expect(auth.bypassLogin).toBe(false);
  });

  it('logout 清理 token 与活动 Registrar 本地选择', () => {
    localStorage.setItem('token', 'jwt-token');
    localStorage.setItem('activeRegistrarId', 'reg-1');
    const auth = useAuthStore();
    auth.token = 'jwt-token';
    auth.logout();
    expect(auth.token).toBe('');
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('activeRegistrarId')).toBeNull();
  });
});

// 重启后登录页事故回归：checkInitialized 的错误降级绝不能把"已初始化+版本未知"写进缓存
describe('stores/auth checkInitialized 初始化状态探测', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.removeItem('token');
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mockedGet.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('成功时缓存 initialized 与 edition（个人版据此免登录）', async () => {
    mockedGet.mockResolvedValueOnce({ initialized: true, edition: 'PERSONAL' } as never);
    const auth = useAuthStore();
    expect(await auth.checkInitialized()).toBe(true);
    expect(await auth.checkInitialized()).toBe(true);
    expect(mockedGet).toHaveBeenCalledTimes(1);
    expect(auth.edition).toBe('PERSONAL');
    expect(auth.bypassLogin).toBe(true);
  });

  it('重启窗口首次请求瞬时失败时重试成功并自愈（不出现登录页）', async () => {
    // 半开连接上的瞬时网络错误：后端实际已就绪，换新连接重试即成功
    mockedGet
      .mockRejectedValueOnce(new Error('Network Error'))
      .mockResolvedValueOnce({ initialized: true, edition: 'PERSONAL' } as never);
    const auth = useAuthStore();
    expect(await auth.checkInitialized()).toBe(true);
    expect(mockedGet).toHaveBeenCalledTimes(2);
    expect(auth.bypassLogin).toBe(true);
  });

  it('后端持续不可达时降级返回 true，但不缓存错误状态（下次导航重试）', async () => {
    mockedGet.mockRejectedValue(new Error('Network Error'));
    const auth = useAuthStore();
    expect(await auth.checkInitialized()).toBe(true);
    // 关键不变式：错误降级绝不写缓存——initialized/edition 保持 null，
    // 否则个人版 isLoggedIn 恒 false，页面会话被钉死在登录页
    expect(auth.initialized).toBeNull();
    expect(auth.edition).toBeNull();
    expect(await auth.checkInitialized()).toBe(true);
    expect(mockedGet).toHaveBeenCalledTimes(4); // 每次调用失败重试一次
  });

  it('代理/扩展异常返回 200 但体非法（HTML→undefined）时视为失败并重试', async () => {
    mockedGet
      .mockResolvedValueOnce(undefined as never) // 200 但非 JSON 响应体被解成 undefined
      .mockResolvedValueOnce({ initialized: true, edition: 'PERSONAL' } as never);
    const auth = useAuthStore();
    expect(await auth.checkInitialized()).toBe(true);
    expect(mockedGet).toHaveBeenCalledTimes(2);
    expect(auth.bypassLogin).toBe(true);
  });

  it('持续返回非法响应时降级返回 true 且不缓存', async () => {
    mockedGet.mockResolvedValue('<html>proxy error</html>' as never);
    const auth = useAuthStore();
    expect(await auth.checkInitialized()).toBe(true);
    expect(auth.initialized).toBeNull();
    expect(auth.edition).toBeNull();
    expect(mockedGet).toHaveBeenCalledTimes(2);
  });
});
