import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import ItemFormDialog from './ItemFormDialog.vue';

const namespaces = vi.hoisted(() => [
  {
    id: 'ns-auto-only',
    identifier: 'ns-ao',
    identifierInputMode: 'AUTO_ONLY',
    versionInputMode: 'AUTO_ONLY',
    versioningPolicy: 'SEMVER',
  },
  {
    id: 'ns-awo',
    identifier: 'ns-awo',
    identifierInputMode: 'AUTO_WITH_OVERRIDE',
    versionInputMode: 'AUTO_WITH_OVERRIDE',
    versioningPolicy: 'SEQUENTIAL',
  },
  {
    id: 'ns-manual',
    identifier: 'ns-manual',
    identifierInputMode: 'MANUAL_ONLY',
    versionInputMode: 'MANUAL_ONLY',
    versioningPolicy: 'DATE',
  },
]);

vi.mock('@/api/resources', () => ({
  namespaceApi: {
    list: vi.fn().mockImplementation(() => Promise.resolve({ content: namespaces })),
    detail: vi.fn().mockResolvedValue({ namingConventionIds: [] }),
  },
  registrationAuthorityApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
  languageApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
  contextApi: { options: vi.fn().mockResolvedValue([]) },
  referenceDocumentApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
  namingConventionApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
  pairingApi: {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    delete: vi.fn(),
  },
  readinessApi: { raNamespaceExists: vi.fn().mockResolvedValue({ exists: true }) },
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: any) => (params ? `${key} ${JSON.stringify(params)}` : key),
  }),
}));

describe('ItemFormDialog 命名空间输入模式联动', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  async function mountAndOpen(namespaceId: string) {
    const wrapper = mount(ItemFormDialog, {
      props: {
        title: '测试',
        submitFn: vi.fn().mockResolvedValue({}),
        showRegistration: true,
      },
      global: {
        plugins: [ElementPlus],
        mocks: {
          $t: (key: string, params?: any) => (params ? `${key} ${JSON.stringify(params)}` : key),
        },
      },
    });
    await (wrapper.vm as any).open({ registration: { namespaceId } });
    await wrapper.vm.$nextTick();
    return wrapper;
  }

  function inputs(wrapper: any) {
    return wrapper.findAllComponents({ name: 'ElInput' });
  }

  function radioButtons(wrapper: any) {
    return wrapper.findAllComponents({ name: 'ElRadioButton' });
  }

  it('AUTO_WITH_OVERRIDE：显示生成方式单选，默认自动，标识符禁用并提示自动生成', async () => {
    const wrapper = await mountAndOpen('ns-awo');

    // 标识符 + 版本各一组 auto/manual 单选
    expect(radioButtons(wrapper).length).toBe(4);

    const [identifierInput, versionInput] = inputs(wrapper);
    expect(identifierInput.props('disabled')).toBe(true);
    expect(identifierInput.props('placeholder')).toBe('保存后自动生成');
    // 默认选中自动生成，版本框同样禁用并提示自动生成
    expect(versionInput.props('disabled')).toBe(true);
    expect(versionInput.props('placeholder')).toBe('保存后自动生成');
  });

  it('AUTO_ONLY：不显示单选，标识符与版本输入框恒禁用', async () => {
    const wrapper = await mountAndOpen('ns-auto-only');

    expect(radioButtons(wrapper).length).toBe(0);

    const [identifierInput, versionInput] = inputs(wrapper);
    expect(identifierInput.props('disabled')).toBe(true);
    expect(identifierInput.props('placeholder')).toBe('保存后自动生成');
    expect(versionInput.props('disabled')).toBe(true);
  });

  it('MANUAL_ONLY：不显示单选，标识符与版本输入框启用可填', async () => {
    const wrapper = await mountAndOpen('ns-manual');

    expect(radioButtons(wrapper).length).toBe(0);

    const [identifierInput, versionInput] = inputs(wrapper);
    expect(identifierInput.props('disabled')).toBe(false);
    expect(identifierInput.props('placeholder')).toBe('唯一标识符');
    expect(versionInput.props('disabled')).toBe(false);
    // DATE 策略占位提示
    expect(versionInput.props('placeholder')).toBe('如 2026-01-31');
  });
});
