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
        itemType: 'dataElement',
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

  function inlineModeControls(wrapper: any) {
    return wrapper.findAllComponents({ name: 'ElSegmented' });
  }

  it('AUTO_WITH_OVERRIDE：显示生成方式单选，默认自动，标识符禁用并提示自动生成', async () => {
    const wrapper = await mountAndOpen('ns-awo');

    // 标识符 + 版本各一个内嵌切换控件
    expect(inlineModeControls(wrapper).length).toBe(2);

    const [identifierInput, versionInput] = inputs(wrapper);
    expect(identifierInput.props('disabled')).toBe(true);
    expect(identifierInput.props('placeholder')).toBe('保存后自动生成');
    // 默认选中自动生成，版本框同样禁用并提示自动生成
    expect(versionInput.props('disabled')).toBe(true);
    expect(versionInput.props('placeholder')).toBe('保存后自动生成');
  });

  it('AUTO_ONLY：不显示单选，标识符与版本输入框恒禁用', async () => {
    const wrapper = await mountAndOpen('ns-auto-only');

    expect(inlineModeControls(wrapper).length).toBe(0);

    const [identifierInput, versionInput] = inputs(wrapper);
    expect(identifierInput.props('disabled')).toBe(true);
    expect(identifierInput.props('placeholder')).toBe('保存后自动生成');
    expect(versionInput.props('disabled')).toBe(true);
  });

  it('MANUAL_ONLY：不显示单选，标识符与版本输入框启用可填', async () => {
    const wrapper = await mountAndOpen('ns-manual');

    expect(inlineModeControls(wrapper).length).toBe(0);

    const [identifierInput, versionInput] = inputs(wrapper);
    expect(identifierInput.props('disabled')).toBe(false);
    expect(identifierInput.props('placeholder')).toBe('唯一标识符');
    expect(versionInput.props('disabled')).toBe(false);
    // DATE 策略占位提示
    expect(versionInput.props('placeholder')).toBe('如 2026-01-31');
  });
});

describe('ItemFormDialog itemType 资源索引', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  async function mountWithItemType(itemType: string, submitFn?: any) {
    const wrapper = mount(ItemFormDialog, {
      props: {
        itemType,
        submitFn: submitFn ?? vi.fn().mockResolvedValue({}),
        showRegistration: true,
      },
      global: {
        plugins: [ElementPlus],
        mocks: {
          $t: (key: string, params?: any) => (params ? `${key} ${JSON.stringify(params)}` : key),
        },
      },
    });
    await (wrapper.vm as any).open({});
    await wrapper.vm.$nextTick();
    return wrapper;
  }

  it('主对话框标题按 itemType 通过 i18n 资源 key 索引', async () => {
    const wrapper = await mountWithItemType('dataElement');
    // mock t(key, fallback) 会把 fallback 当 params JSON 化；
    // toContain 验证主键是 meta.itemType.dataElement，且 fallback 也被传入。
    expect((wrapper.vm as any).resolvedTitle).toContain('meta.itemType.dataElement');
  });

  it('不同的 itemType 映射到不同的 i18n key', async () => {
    const wrapper = await mountWithItemType('valueDomain');
    expect((wrapper.vm as any).resolvedTitle).toContain('meta.itemType.valueDomain');
  });

  it('未识别的 itemType 落回原 key，避免显示空白', async () => {
    const wrapper = await mountWithItemType('unknownType');
    // 即使 i18n 资源里查不到，主键仍按 meta.itemType.<itemType> 查找，
    // 同时传入原 itemType 作为 fallback，确保运行时不会出现空白。
    const title = (wrapper.vm as any).resolvedTitle;
    expect(title).toContain('meta.itemType.unknownType');
    expect(title).toContain('unknownType');
  });

  it('指称与定义都为空时 handleSubmit 触发 emptyConfirm 对话框', async () => {
    const wrapper = await mountWithItemType('dataElement');
    await (wrapper.vm as any).handleSubmit();
    await wrapper.vm.$nextTick();
    expect((wrapper.vm as any).emptyConfirmVisible).toBe(true);
  });

  it('只有指称时不应触发 emptyConfirm', async () => {
    const wrapper = await mountWithItemType('dataElement');
    (wrapper.vm as any).form.designations = [{ value: '示例名称' }];
    await (wrapper.vm as any).handleSubmit();
    await wrapper.vm.$nextTick();
    expect((wrapper.vm as any).emptyConfirmVisible).toBe(false);
  });

  it('空确认取消时关闭弹窗并不提交', async () => {
    const submitFn = vi.fn().mockResolvedValue({});
    const wrapper = await mountWithItemType('dataElement', submitFn);
    await (wrapper.vm as any).handleSubmit();
    await wrapper.vm.$nextTick();
    expect((wrapper.vm as any).emptyConfirmVisible).toBe(true);

    // 取消等价于关闭弹窗，且不触发提交链
    (wrapper.vm as any).emptyConfirmVisible = false;
    await wrapper.vm.$nextTick();
    expect(submitFn).not.toHaveBeenCalled();
  });
});
