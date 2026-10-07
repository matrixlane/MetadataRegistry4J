import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, type Pinia } from 'pinia';
import ElementPlus from 'element-plus';
import type { VueWrapper } from '@vue/test-utils';
import AppLayout from './AppLayout.vue';
import { useAiChatStore } from '@/stores/aiChat';
import { activeRegistrarApi } from '@/api/resources';
import { useActiveRegistrar } from '@/composables/useActiveRegistrar';

const state = vi.hoisted(() => ({
  isRegistrarUser: true,
  bypassLogin: false,
  user: { id: 'user-1', username: 'registrar-1', displayName: '注册员' } as any,
}));

const mocks = vi.hoisted(() => ({
  settingsGet: vi.fn(),
}));

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/', query: {} }),
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    user: state.user,
    isRegistrarUser: state.isRegistrarUser,
    isAdmin: false,
    get bypassLogin() {
      return state.bypassLogin;
    },
    logout: vi.fn(),
  }),
}));

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

vi.mock('@/api/resources', () => ({
  activeRegistrarApi: {
    current: vi.fn().mockResolvedValue({ defaultRegistrarId: null, identities: [] }),
  },
}));

vi.mock('@/api/assistant', () => ({
  assistantApi: {
    tasks: vi.fn(),
    listDebugExchanges: vi.fn(),
    createJob: vi.fn(),
    getJob: vi.fn(),
    cancelJob: vi.fn(),
  },
  pollAssistantJob: vi.fn(),
  assistantSettingsApi: { get: mocks.settingsGet },
}));

vi.mock('@/composables/useRegistryDefaultLanguage', () => ({
  useRegistryDefaultLanguage: () => ({
    load: vi.fn(),
    defaultLanguageId: { value: null },
    error: { value: null },
    loading: { value: false },
  }),
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

const mounted: VueWrapper[] = [];

beforeEach(() => {
  // useActiveRegistrar 为模块级单例：任何挂载 AppLayout 的用例前都重置，防顺序依赖
  useActiveRegistrar().reset();
  localStorage.removeItem('activeRegistrarId');
});

afterEach(() => {
  while (mounted.length) {
    mounted.pop()!.unmount();
  }
});

function body(): HTMLElement {
  return document.body;
}

function pressKey(init: KeyboardEventInit): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { cancelable: true, ...init });
  window.dispatchEvent(event);
  return event;
}

async function mountLayout() {
  const pinia: Pinia = createPinia();
  const wrapper = mount(AppLayout, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus, pinia],
      mocks: { $t: (key: string) => key },
      stubs: {
        AiDebugDrawer: {
          props: ['modelValue'],
          template: '<div v-if="modelValue" data-test="ai-debug-stub">open</div>',
        },
      },
    },
  });
  mounted.push(wrapper);
  await flushPromises();
  return { wrapper, pinia };
}

beforeEach(() => {
  vi.clearAllMocks();
  state.isRegistrarUser = true;
  mocks.settingsGet.mockResolvedValue({
    candidateCount: 3,
    hotkeys: { 'aiDebug.toggle': 'Ctrl+Alt+J' },
  });
});

describe('AppLayout AI 调试侧栏快捷键（按设置绑定注册）', () => {
  it('按服务端绑定开合：Ctrl+Alt+J 触发，原默认 Ctrl+Shift+D 不再触发', async () => {
    await mountLayout();
    expect(body().querySelector('[data-test="ai-debug-stub"]')).toBeNull();
    const stale = pressKey({ ctrlKey: true, shiftKey: true, code: 'KeyD', key: 'D' });
    await flushPromises();
    expect(stale.defaultPrevented).toBe(false);
    expect(body().querySelector('[data-test="ai-debug-stub"]')).toBeNull();

    const event = pressKey({ ctrlKey: true, altKey: true, code: 'KeyJ', key: 'J' });
    await flushPromises();
    expect(event.defaultPrevented).toBe(true);
    expect(body().querySelector('[data-test="ai-debug-stub"]')).toBeTruthy();

    pressKey({ ctrlKey: true, altKey: true, code: 'KeyJ', key: 'J' });
    await flushPromises();
    expect(body().querySelector('[data-test="ai-debug-stub"]')).toBeNull();
  });

  it('设置读取失败时回退默认绑定 Ctrl+Shift+D', async () => {
    mocks.settingsGet.mockRejectedValue(new Error('boom'));
    await mountLayout();
    pressKey({ ctrlKey: true, shiftKey: true, code: 'KeyD', key: 'D' });
    await flushPromises();
    expect(body().querySelector('[data-test="ai-debug-stub"]')).toBeTruthy();
  });

  it('其它按键不触发侧栏', async () => {
    await mountLayout();
    pressKey({ ctrlKey: true, altKey: true, code: 'KeyE', key: 'E' });
    await flushPromises();
    expect(body().querySelector('[data-test="ai-debug-stub"]')).toBeNull();
  });

  it('非 Registrar 用户：不渲染侧栏、不注册快捷键', async () => {
    state.isRegistrarUser = false;
    await mountLayout();
    expect(mocks.settingsGet).not.toHaveBeenCalled();
    pressKey({ ctrlKey: true, altKey: true, code: 'KeyJ', key: 'J' });
    await flushPromises();
    expect(body().querySelector('[data-test="ai-debug-stub"]')).toBeNull();
  });
});

describe('AppLayout 全局划词「问 AI」', () => {
  function stubSelection(text: string, parent: HTMLElement) {
    const node = document.createTextNode(text);
    parent.appendChild(node);
    vi.stubGlobal('getSelection', () => ({
      isCollapsed: !text,
      anchorNode: node,
      toString: () => text,
      getRangeAt: () => ({
        getBoundingClientRect: () => ({ left: 10, width: 60, bottom: 24, right: 70, top: 0, height: 24, x: 10, y: 0, toJSON: () => ({}) }),
      }),
    }));
  }

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('只读文本选区 ≥2 字符：浮出问 AI 按钮，点击开窗并写入引用', async () => {
    const { pinia } = await mountLayout();
    const host = document.createElement('p');
    host.textContent = '';
    document.body.appendChild(host);
    stubSelection('患者年龄', host);
    host.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    await new Promise((r) => setTimeout(r, 10));
    await flushPromises();

    const btn = body().querySelector('[data-test="ai-ask-button"]') as HTMLElement;
    expect(btn).toBeTruthy();
    btn.click();
    await flushPromises();
    const chat = useAiChatStore(pinia);
    expect(chat.open).toBe(true);
    expect(chat.quote?.text).toBe('患者年龄');
    expect(body().querySelector('[data-test="ai-chat-window"]')).toBeTruthy();
    host.remove();
  });

  it('选区在输入控件内不浮现按钮', async () => {
    await mountLayout();
    const input = document.createElement('textarea');
    document.body.appendChild(input);
    stubSelection('患者年龄', input);
    input.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    await new Promise((r) => setTimeout(r, 10));
    await flushPromises();
    expect(body().querySelector('[data-test="ai-ask-button"]')).toBeNull();
    input.remove();
  });

  it('选区折叠或不足 2 字符不浮现按钮', async () => {
    await mountLayout();
    const host = document.createElement('p');
    document.body.appendChild(host);
    stubSelection('短', host);
    host.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    await new Promise((r) => setTimeout(r, 10));
    await flushPromises();
    expect(body().querySelector('[data-test="ai-ask-button"]')).toBeNull();
    host.remove();
  });

  it('非 Registrar 划词不浮现按钮', async () => {
    state.isRegistrarUser = false;
    await mountLayout();
    const host = document.createElement('p');
    document.body.appendChild(host);
    stubSelection('患者年龄', host);
    host.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    await new Promise((r) => setTimeout(r, 10));
    await flushPromises();
    expect(body().querySelector('[data-test="ai-ask-button"]')).toBeNull();
    host.remove();
  });
});

describe('AppLayout 首登完善联系人提示', () => {
  beforeEach(() => {
    localStorage.clear();
    state.user = { id: 'user-1', username: 'registrar-1', displayName: '注册员' };
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('profilePrompt=true 时显示提示弹窗', async () => {
    state.user = { id: 'user-1', username: 'registrar-1', displayName: '注册员', profilePrompt: true };
    await mountLayout();
    expect(body().querySelector('[data-testid="profile-prompt-dialog"]')).toBeTruthy();
  });

  it('profilePrompt=false 时不显示提示弹窗', async () => {
    state.user = { id: 'user-1', username: 'registrar-1', displayName: '注册员', profilePrompt: false };
    await mountLayout();
    expect(body().querySelector('[data-testid="profile-prompt-dialog"]')).toBeNull();
  });

  it('点击「跳过」后记录 localStorage 并不再显示', async () => {
    state.user = { id: 'user-1', username: 'registrar-1', displayName: '注册员', profilePrompt: true };
    const { wrapper } = await mountLayout();
    const skipBtn = body().querySelector('[data-testid="profile-prompt-skip"]') as HTMLElement;
    expect(skipBtn).toBeTruthy();
    skipBtn.click();
    await flushPromises();
    expect((wrapper.vm as any).showProfilePromptDialog).toBe(false);
    expect(localStorage.getItem('profilePromptDismissed:user-1')).toBe('true');
  });

  it('改密弹窗优先：forceChangePassword=true 时不显示提示弹窗', async () => {
    state.user = { id: 'user-1', username: 'registrar-1', displayName: '注册员', profilePrompt: true, forceChangePassword: true };
    await mountLayout();
    expect(body().querySelector('[data-testid="profile-prompt-dialog"]')).toBeNull();
  });
});

const mockedActiveCurrent = vi.mocked(activeRegistrarApi.current);

describe('AppLayout 退出按钮（个人版免登录隐藏）', () => {
  beforeEach(() => {
    state.bypassLogin = false;
  });

  afterEach(() => {
    state.bypassLogin = false;
  });

  it('团队版（bypassLogin=false）渲染退出按钮', async () => {
    const { wrapper } = await mountLayout();
    expect(wrapper.find('[data-testid="header-logout-btn"]').exists()).toBe(true);
  });

  it('个人版（bypassLogin=true）不渲染退出按钮', async () => {
    state.bypassLogin = true;
    const { wrapper } = await mountLayout();
    expect(wrapper.find('[data-testid="header-logout-btn"]').exists()).toBe(false);
  });
});

describe('AppLayout 活动 Registrar（浏览器端激活，personal-no-login-registrar-default）', () => {
  const identityA = { registrarId: 'reg-1', registrationAuthorityId: 'ra-1', registrationAuthorityName: '甲注册机构' };
  const identityB = { registrarId: 'reg-2', registrationAuthorityId: 'ra-2', registrationAuthorityName: '乙注册机构' };

  beforeEach(() => {
    // useActiveRegistrar 为模块级单例：每个用例前重置并清掉浏览器端选择
    useActiveRegistrar().reset();
    localStorage.removeItem('activeRegistrarId');
  });

  afterEach(() => {
    mockedActiveCurrent.mockResolvedValue({ defaultRegistrarId: null, identities: [] });
  });

  it('多身份响应下标签可点击弹出切换菜单，默认注册人员自动激活', async () => {
    mockedActiveCurrent.mockResolvedValue({ defaultRegistrarId: 'reg-1', identities: [identityA, identityB] });
    const { wrapper } = await mountLayout();
    const tag = wrapper.find('[data-testid="header-active-registrar"]');
    expect(tag.exists()).toBe(true);
    expect(tag.text()).toContain('甲注册机构');
    const menu = wrapper.find('[data-testid="header-active-registrar-menu"]');
    expect(menu.exists()).toBe(true);
    await tag.trigger('click');
    await flushPromises();
    const options = document.body.querySelectorAll('[data-testid^="registrar-option-"]');
    expect(options.length).toBe(2);
    expect(options[0].textContent).toContain('甲注册机构');
    expect(options[1].textContent).toContain('乙注册机构');
  });

  it('选择菜单项仅写浏览器端状态（不发任何服务端请求），标签随之更新', async () => {
    mockedActiveCurrent.mockResolvedValue({ defaultRegistrarId: 'reg-1', identities: [identityA, identityB] });
    const { wrapper } = await mountLayout();
    const tag = wrapper.find('[data-testid="header-active-registrar"]');
    await tag.trigger('click');
    await flushPromises();
    const option = document.body.querySelector('[data-testid="registrar-option-reg-2"]');
    expect(option).not.toBeNull();
    (option as HTMLElement).click();
    await flushPromises();
    // 切换 = 纯客户端动作：PUT 端点已删除，写 localStorage 并即时生效
    expect(mockedActiveCurrent).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem('activeRegistrarId')).toBe('reg-2');
    expect(wrapper.find('[data-testid="header-active-registrar"]').text()).toContain('乙注册机构');
  });

  it('单身份响应渲染纯只读 tag（无切换菜单）', async () => {
    mockedActiveCurrent.mockResolvedValue({ defaultRegistrarId: 'reg-1', identities: [identityA] });
    const { wrapper } = await mountLayout();
    const tag = wrapper.find('[data-testid="header-active-registrar"]');
    expect(tag.exists()).toBe(true);
    expect(tag.text()).toContain('甲注册机构');
    expect(wrapper.find('[data-testid="header-active-registrar-menu"]').exists()).toBe(false);
  });

  it('无身份响应不渲染 tag', async () => {
    mockedActiveCurrent.mockResolvedValue({ defaultRegistrarId: null, identities: [] });
    const { wrapper } = await mountLayout();
    expect(wrapper.find('[data-testid="header-active-registrar"]').exists()).toBe(false);
  });
});
