import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import type { VueWrapper } from '@vue/test-utils';
import AiAssistSettingsPage from './AiAssistSettingsPage.vue';

const mocks = vi.hoisted(() => ({
  getAgents: vi.fn(),
  saveAgents: vi.fn(),
  probeAgents: vi.fn(),
  settingsGet: vi.fn(),
  settingsSave: vi.fn(),
}));

const authState = vi.hoisted(() => ({ isAdmin: true, user: { username: 'admin' } as unknown }));

vi.mock('@/api/inference', () => ({
  inferenceAgentApi: {
    getAgents: mocks.getAgents,
    saveAgents: mocks.saveAgents,
    probeAgents: mocks.probeAgents,
  },
}));

vi.mock('@/api/assistant', () => ({
  assistantSettingsApi: { get: mocks.settingsGet, save: mocks.settingsSave },
}));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ isAdmin: authState.isAdmin, user: authState.user }),
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: any) => (params ? `${key} ${JSON.stringify(params)}` : key),
  }),
}));

const AGENTS_VIEW = {
  defaultProvider: 'claude',
  agents: [
    {
      id: 'claude', displayName: 'Claude Code', kind: 'acp',
      command: 'claude', args: ['-p'], enabled: true, available: true,
      detail: '1.0.0', isDefault: true, builtin: true,
    },
    {
      id: 'codex', displayName: 'Codex', kind: 'acp',
      command: '', args: [], enabled: false, available: false,
      detail: 'not installed', isDefault: false, builtin: true,
    },
  ],
};

const SETTINGS = {
  candidateCount: 4,
  hotkeys: { 'aiDebug.toggle': 'Ctrl+Shift+D' },
};

const mounted: VueWrapper[] = [];

afterEach(() => {
  while (mounted.length) {
    mounted.pop()!.unmount();
  }
});

function body(): HTMLElement {
  return document.body;
}

async function mountPage() {
  const wrapper = mount(AiAssistSettingsPage, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      mocks: {
        $t: (key: string, params?: any) => (params ? `${key} ${JSON.stringify(params)}` : key),
      },
    },
  });
  mounted.push(wrapper);
  await flushPromises();
  await wrapper.vm.$nextTick();
  return wrapper;
}

function pressKey(init: KeyboardEventInit) {
  window.dispatchEvent(new KeyboardEvent('keydown', { cancelable: true, ...init }));
}

beforeEach(() => {
  vi.clearAllMocks();
  authState.isAdmin = true;
  authState.user = { username: 'admin' } as unknown;
  mocks.getAgents.mockResolvedValue(AGENTS_VIEW);
  mocks.probeAgents.mockResolvedValue(AGENTS_VIEW);
  mocks.saveAgents.mockResolvedValue(AGENTS_VIEW);
  mocks.settingsGet.mockResolvedValue(SETTINGS);
  mocks.settingsSave.mockResolvedValue(SETTINGS);
});

describe('AiAssistSettingsPage Agent 管理', () => {
  it('加载并渲染探测表格与设置（候选数/快捷键绑定）', async () => {
    await mountPage();
    expect(mocks.getAgents).toHaveBeenCalled();
    const text = body().textContent || '';
    expect(text).toContain('Claude Code');
    expect(text).toContain('systemInference.available');
    expect(text).toContain('Ctrl+Shift+D');
    expect(body().querySelector('[data-test="pref-candidate-count"] input'))
      .toBeTruthy();
    expect((body().querySelector('[data-test="pref-candidate-count"] input') as HTMLInputElement).value)
      .toBe('4');
  });

  it('保存 Agent 提交勾选与默认状态', async () => {
    await mountPage();
    mocks.saveAgents.mockResolvedValue({
      defaultProvider: 'claude',
      agents: AGENTS_VIEW.agents,
    });
    (body().querySelector('[data-test="agents-save"]') as HTMLElement).click();
    await flushPromises();
    expect(mocks.saveAgents).toHaveBeenCalledWith(expect.objectContaining({
      defaultProvider: 'claude',
      agents: expect.arrayContaining([
        expect.objectContaining({ id: 'claude', enabled: true }),
      ]),
    }));
  });

  it('非超级管理员：Agent 只读提示且不加载；应用设置保存对登录用户可用', async () => {
    authState.isAdmin = false;
    await mountPage();
    expect(mocks.getAgents).not.toHaveBeenCalled();
    expect(body().textContent).toContain('systemInference.superAdminOnly');
    // 应用设置（AI 辅助偏好）属每位登录用户：保存按钮不再禁用（add-workspace-administrative-status）
    expect((body().querySelector('[data-test="settings-save"]') as HTMLButtonElement).disabled)
      .toBe(false);
  });

  it('未登录用户：应用设置保存按钮禁用', async () => {
    authState.user = null;
    await mountPage();
    expect((body().querySelector('[data-test="settings-save"]') as HTMLButtonElement).disabled)
      .toBe(true);
  });
});

describe('AiAssistSettingsPage 快捷键录制与保存', () => {
  it('录制：进入捕获态，按键后更新绑定', async () => {
    await mountPage();
    (body().querySelector('[data-test="hotkey-record"]') as HTMLElement).click();
    await flushPromises();
    expect(body().querySelector('[data-test="hotkey-capturing"]')).toBeTruthy();
    // 仅修饰键不结束录制
    pressKey({ key: 'Control', ctrlKey: true });
    await flushPromises();
    expect(body().querySelector('[data-test="hotkey-capturing"]')).toBeTruthy();
    pressKey({ key: 'k', ctrlKey: true, shiftKey: true });
    await flushPromises();
    const binding = body().querySelector('[data-test="hotkey-binding"]');
    expect(binding!.textContent).toBe('Ctrl+Shift+K');
  });

  it('Esc 取消录制', async () => {
    await mountPage();
    (body().querySelector('[data-test="hotkey-record"]') as HTMLElement).click();
    await flushPromises();
    pressKey({ key: 'Escape' });
    await flushPromises();
    expect(body().querySelector('[data-test="hotkey-capturing"]')).toBeNull();
    expect(body().querySelector('[data-test="hotkey-binding"]')!.textContent).toBe('Ctrl+Shift+D');
  });

  it('恢复默认：修改后恢复默认绑定', async () => {
    await mountPage();
    (body().querySelector('[data-test="hotkey-record"]') as HTMLElement).click();
    pressKey({ key: 'k', ctrlKey: true, shiftKey: true });
    await flushPromises();
    expect(body().querySelector('[data-test="hotkey-binding"]')!.textContent).toBe('Ctrl+Shift+K');
    (body().querySelector('[data-test="hotkey-reset"]') as HTMLElement).click();
    await flushPromises();
    expect(body().querySelector('[data-test="hotkey-binding"]')!.textContent).toBe('Ctrl+Shift+D');
  });

  it('保存 PUT 携带候选数与全部快捷键，成功提示并刷新视图', async () => {
    const wrapper = await mountPage();
    // 改候选数 5 + 改绑定
    const num = body().querySelector('[data-test="pref-candidate-count"] input') as HTMLInputElement;
    num.value = '5';
    num.dispatchEvent(new Event('input'));
    await flushPromises();
    (wrapper.vm as any).candidateCount = 5;
    (body().querySelector('[data-test="hotkey-record"]') as HTMLElement).click();
    pressKey({ key: 'j', ctrlKey: true, altKey: true });
    await flushPromises();

    mocks.settingsSave.mockResolvedValue({
      candidateCount: 5,
      hotkeys: { 'aiDebug.toggle': 'Ctrl+Alt+J' },
    });
    (body().querySelector('[data-test="settings-save"]') as HTMLElement).click();
    await flushPromises();
    expect(mocks.settingsSave).toHaveBeenCalledWith({
      candidateCount: 5,
      hotkeys: { 'aiDebug.toggle': 'Ctrl+Alt+J' },
    });
    expect(body().querySelector('[data-test="hotkey-binding"]')!.textContent).toBe('Ctrl+Alt+J');
  });
});
