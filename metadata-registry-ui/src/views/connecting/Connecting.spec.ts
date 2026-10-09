import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import Connecting from './Connecting.vue';

const state = vi.hoisted(() => ({
  initialized: null as boolean | null,
  bypassLogin: false,
  isLoggedIn: false,
}));
const routerReplace = vi.hoisted(() => vi.fn());
const checkInitialized = vi.hoisted(() => vi.fn());

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/connecting', query: { redirect: '/dashboard' } }),
  useRouter: () => ({ replace: routerReplace }),
}));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    get initialized() {
      return state.initialized;
    },
    get bypassLogin() {
      return state.bypassLogin;
    },
    get isLoggedIn() {
      return state.isLoggedIn;
    },
    checkInitialized,
  }),
}));

// 个人版彻底绕过登录页（personal-no-login）：状态未知时守卫进本页重试分流，
// 个人版已初始化 → 直达目标页；未初始化 → 向导；仅团队版未登录 → 登录页
describe('Connecting 连接恢复页分流', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.clearAllMocks();
    state.initialized = null;
    state.bypassLogin = false;
    state.isLoggedIn = false;
    checkInitialized.mockResolvedValue(true);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('个人版已初始化（bypassLogin=true）→ 免登录直达 redirect 目标页', async () => {
    state.initialized = true;
    state.bypassLogin = true;
    const wrapper = mount(Connecting);
    await flushPromises();
    expect(routerReplace).toHaveBeenCalledWith('/dashboard');
    wrapper.unmount();
  });

  it('未初始化系统 → 初始化向导', async () => {
    state.initialized = false;
    const wrapper = mount(Connecting);
    await flushPromises();
    expect(routerReplace).toHaveBeenCalledWith('/init-wizard');
    wrapper.unmount();
  });

  it('团队版未登录 → 登录页（带 redirect 回跳）', async () => {
    state.initialized = true;
    state.bypassLogin = false;
    state.isLoggedIn = false;
    const wrapper = mount(Connecting);
    await flushPromises();
    expect(routerReplace).toHaveBeenCalledWith({ path: '/login', query: { redirect: '/dashboard' } });
    wrapper.unmount();
  });

  it('状态先未知、重试后已知 → 继续分流（个人版直达目标页）', async () => {
    checkInitialized
      .mockImplementationOnce(() => {
        state.initialized = null; // 首次仍未知
        return Promise.resolve(true);
      })
      .mockImplementationOnce(() => {
        state.initialized = true;
        state.bypassLogin = true;
        return Promise.resolve(true);
      });
    const wrapper = mount(Connecting);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(1500); // 跨过 1s 轮询间隔
    expect(checkInitialized).toHaveBeenCalledTimes(2);
    expect(routerReplace).toHaveBeenCalledWith('/dashboard');
    wrapper.unmount();
  });

  it('持续未知时留在本页继续重试，不跳任何路由', async () => {
    const wrapper = mount(Connecting);
    await flushPromises();
    await vi.advanceTimersByTimeAsync(3500);
    expect(checkInitialized.mock.calls.length).toBeGreaterThanOrEqual(3);
    expect(routerReplace).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
