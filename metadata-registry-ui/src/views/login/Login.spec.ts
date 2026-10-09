import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import Login from './Login.vue';

const state = vi.hoisted(() => ({
  bypassLogin: false,
}));
const routerPush = vi.hoisted(() => vi.fn());
const checkInitialized = vi.hoisted(() => vi.fn());

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/login', query: { redirect: '/dashboard' } }),
  useRouter: () => ({ push: routerPush }),
}));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    get bypassLogin() {
      return state.bypassLogin;
    },
    checkInitialized,
    login: vi.fn(),
  }),
}));

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn().mockResolvedValue(true) },
}));

vi.mock('@/composables/useActiveRegistrar', () => ({
  useActiveRegistrar: () => ({ reset: vi.fn() }),
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

function mountLogin() {
  return mount(Login, {
    global: {
      plugins: [ElementPlus],
      mocks: { $t: (key: string) => key },
    },
  });
}

// 个人版免登录兜底（重启后登录页事故回归）：守卫因瞬时网络错误把个人版
// 误判为未登录而落到登录页时，挂载后强制重查初始化状态并自动回目标页
describe('Login 个人版免登录兜底（recoverPersonalSession）', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    state.bypassLogin = false;
    checkInitialized.mockResolvedValue(true);
  });

  it('PERSONAL 已初始化（bypassLogin=true）时自动离开登录页回 redirect 目标', async () => {
    state.bypassLogin = true;
    const wrapper = mountLogin();
    await flushPromises();
    expect(checkInitialized).toHaveBeenCalledWith(true);
    expect(routerPush).toHaveBeenCalledWith('/dashboard');
    wrapper.unmount();
  });

  it('团队版（bypassLogin=false）停留在登录页', async () => {
    state.bypassLogin = false;
    const wrapper = mountLogin();
    await flushPromises();
    expect(checkInitialized).toHaveBeenCalledWith(true);
    expect(routerPush).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('重查抛错（后端不可达）时停留在登录页，不崩溃', async () => {
    checkInitialized.mockRejectedValue(new Error('Network Error'));
    const wrapper = mountLogin();
    await flushPromises();
    expect(routerPush).not.toHaveBeenCalled();
    wrapper.unmount();
  });
});
