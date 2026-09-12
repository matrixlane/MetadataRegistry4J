import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, type Pinia } from 'pinia';
import ElementPlus from 'element-plus';
import type { VueWrapper } from '@vue/test-utils';
import AppLayout from './AppLayout.vue';
import { useAiChatStore } from '@/stores/aiChat';

const state = vi.hoisted(() => ({
  isRegistrarUser: true,
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
    user: { username: 'registrar-1', displayName: '注册员' },
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
