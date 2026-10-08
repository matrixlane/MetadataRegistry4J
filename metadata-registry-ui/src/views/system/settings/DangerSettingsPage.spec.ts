import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, DOMWrapper } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import DangerSettingsPage from './DangerSettingsPage.vue';
import { systemApi } from '@/api/system';
import { useAuthStore } from '@/stores/auth';

// 可变的版本桩：reactive 包裹以模拟 pinia store 的响应式 edition（computed 依赖它刷新）
const authHolder = vi.hoisted(() => ({ state: null as any }));

const mockedCheckInitialized = vi.hoisted(() =>
  vi.fn(async () => {
    // 默认不写入 edition（版本未知场景）
    return true;
  })
);

vi.mock('@/stores/auth', async () => {
  const { reactive } = await import('vue');
  const state = reactive({ edition: 'PERSONAL' as string | null });
  authHolder.state = state;
  return {
    useAuthStore: () => ({
      get edition() {
        return state.edition;
      },
      user: null,
      setInitialized: vi.fn(),
      checkInitialized: mockedCheckInitialized,
    }),
  };
});

vi.mock('@/api/system', () => ({
  systemApi: {
    factoryReset: vi.fn().mockResolvedValue(undefined),
  },
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

const mockedFactoryReset = vi.mocked(systemApi.factoryReset);

// ElDialog 经 Teleport 渲染到 document.body，对话框作用域元素需在 body 上查询。
// 注意：不可 stub Teleport——VTU 对 Teleport 的桩会破坏 ElDialog 懒渲染（对话框不出现）。
function bodyButtons() {
  return Array.from(document.body.querySelectorAll('button')).map((el) => new DOMWrapper(el));
}

function bodyInputs() {
  return Array.from(document.body.querySelectorAll('input')).map((el) => new DOMWrapper(el));
}

async function openVerifyDialog(wrapper: ReturnType<typeof mount>) {
  const trigger = wrapper
    .findAll('button')
    .find((b) => b.text().includes('恢复出厂设置'));
  await trigger!.trigger('click');
  await flushPromises();
  const proceed = bodyButtons().find((b) => b.text().includes('我已了解风险，继续'));
  await proceed!.trigger('click');
  await flushPromises();
}

function confirmButton() {
  return bodyButtons().find((b) => b.text().includes('确认重置'))!;
}

describe('DangerSettingsPage — 恢复出厂设置二次确认', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authHolder.state.edition = 'PERSONAL';
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('个人版：渲染确认短语输入框，无密码输入框', async () => {
    const wrapper = mount(DangerSettingsPage, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    });
    await openVerifyDialog(wrapper);

    expect(bodyInputs().some((i) => i.attributes('type') === 'password')).toBe(false);
    // 短语常量以「恢复出厂设置」字面量呈现（与后端 FactoryResetService.CONFIRMATION_PHRASE 同步的漂移护栏）
    expect(document.body.textContent).toContain('恢复出厂设置');
    expect(bodyInputs().length).toBeGreaterThan(0);
    // 版本已知时不请求初始化状态
    expect(mockedCheckInitialized).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('个人版：短语完全匹配前确认按钮禁用，匹配后启用并携带 confirmationPhrase 提交', async () => {
    const wrapper = mount(DangerSettingsPage, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    });
    await openVerifyDialog(wrapper);

    expect(confirmButton().attributes('disabled')).toBeDefined();

    const input = bodyInputs()[0];
    await input.setValue('恢复出厂');
    expect(confirmButton().attributes('disabled')).toBeDefined();

    await input.setValue('  恢复出厂设置  ');
    const enabled = confirmButton();
    expect(enabled.attributes('disabled')).toBeUndefined();

    await enabled.trigger('click');
    await flushPromises();
    expect(mockedFactoryReset).toHaveBeenCalledWith({
      confirmationPhrase: '恢复出厂设置',
    });
    wrapper.unmount();
  });

  it('团队版：渲染密码输入框并携带 password 提交', async () => {
    authHolder.state.edition = 'TEAM';
    const wrapper = mount(DangerSettingsPage, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    });
    await openVerifyDialog(wrapper);

    const pwd = bodyInputs().find((i) => i.attributes('type') === 'password');
    expect(pwd).toBeDefined();
    expect(confirmButton().attributes('disabled')).toBeDefined();

    await pwd!.setValue('Admin@123');
    const btn = confirmButton();
    expect(btn.attributes('disabled')).toBeUndefined();
    await btn.trigger('click');
    await flushPromises();
    expect(mockedFactoryReset).toHaveBeenCalledWith({ password: 'Admin@123' });
    wrapper.unmount();
  });

  it('版本未知：经 store 补全后仍未知则按团队版渲染', async () => {
    authHolder.state.edition = null;
    const wrapper = mount(DangerSettingsPage, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    });
    await flushPromises();

    expect(mockedCheckInitialized).toHaveBeenCalled();
    await openVerifyDialog(wrapper);
    expect(bodyInputs().some((i) => i.attributes('type') === 'password')).toBe(true);
    wrapper.unmount();
  });

  it('版本未知：store 补全返回 PERSONAL 时按个人版渲染', async () => {
    authHolder.state.edition = null;
    mockedCheckInitialized.mockImplementation(async () => {
      authHolder.state.edition = 'PERSONAL';
      return true;
    });
    const wrapper = mount(DangerSettingsPage, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    });
    await flushPromises();
    await openVerifyDialog(wrapper);

    expect(bodyInputs().some((i) => i.attributes('type') === 'password')).toBe(false);
    wrapper.unmount();
  });
});
