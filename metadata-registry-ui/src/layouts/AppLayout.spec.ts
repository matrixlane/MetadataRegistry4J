import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, type Pinia } from 'pinia';
import ElementPlus from 'element-plus';
import type { VueWrapper } from '@vue/test-utils';
import AppLayout from './AppLayout.vue';
import { useAiChatStore } from '@/stores/aiChat';
import { activeRegistrarApi } from '@/api/resources';

const state = vi.hoisted(() => ({
  isRegistrarUser: true,
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
    logout: vi.fn(),
  }),
}));

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

vi.mock('@/api/resources', () => ({
  activeRegistrarApi: {
    current: vi.fn().mockResolvedValue({ active: null, identities: [] }),
    select: vi.fn(),
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

describe('AppLayout 活动 Registrar 只读展示（default-registrar-auto-activation）', () => {
  const identityA = { registrarId: 'reg-1', registrationAuthorityId: 'ra-1', registrationAuthorityName: '甲注册机构' };
  const identityB = { registrarId: 'reg-2', registrationAuthorityId: 'ra-2', registrationAuthorityName: '乙注册机构' };

  afterEach(() => {
    mockedActiveCurrent.mockResolvedValue({ active: null, identities: [] });
  });

  it('多身份响应下渲染只读 tag，无下拉切换交互且不调用 select', async () => {
    mockedActiveCurrent.mockResolvedValue({ active: identityA, identities: [identityA, identityB] });
    const { wrapper } = await mountLayout();
    const tag = wrapper.find('[data-testid="header-active-registrar"]');
    expect(tag.exists()).toBe(true);
    expect(tag.text()).toContain('甲注册机构');
    expect(wrapper.find('.el-dropdown').exists()).toBe(false);
    expect(wrapper.find('.el-dropdown-menu').exists()).toBe(false);
    expect(activeRegistrarApi.select).not.toHaveBeenCalled();
  });

  it('单身份响应同样渲染只读 tag 展示 registrationAuthorityName', async () => {
    mockedActiveCurrent.mockResolvedValue({ active: identityA, identities: [identityA] });
    const { wrapper } = await mountLayout();
    const tag = wrapper.find('[data-testid="header-active-registrar"]');
    expect(tag.exists()).toBe(true);
    expect(tag.text()).toContain('甲注册机构');
  });

  it('active 为 null 时不渲染 tag', async () => {
    mockedActiveCurrent.mockResolvedValue({ active: null, identities: [identityA, identityB] });
    const { wrapper } = await mountLayout();
    expect(wrapper.find('[data-testid="header-active-registrar"]').exists()).toBe(false);
  });
});
