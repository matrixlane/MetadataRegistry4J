import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import MultilingualTextEditor from './MultilingualTextEditor.vue';

vi.mock('@/api/resources', () => ({
  languageApi: {
    list: vi.fn().mockResolvedValue({
      content: [
        { id: 'lang-1', languageIdentifier: 'zh-CN', languageName: '简体中文' },
        { id: 'lang-2', languageIdentifier: 'xx-YY' },
      ],
    }),
  },
  contextApi: { options: vi.fn().mockResolvedValue([{ id: 'ctx-1', label: '默认上下文' }]) },
  referenceDocumentApi: { list: vi.fn().mockResolvedValue({ content: [{ id: 'doc-1', title: '参考文档', identifier: 'REF-1' }] }) },
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: any) => (params ? `${key} ${JSON.stringify(params)}` : key),
  }),
}));

describe('MultilingualTextEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  function mountComponent(props: any = {}) {
    return mount(MultilingualTextEditor, {
      props: {
        modelValue: [],
        label: '指称',
        ...props,
      },
      global: {
        plugins: [ElementPlus],
        mocks: {
          $t: (key: string, params?: any) => (params ? `${key} ${JSON.stringify(params)}` : key),
        },
      },
    });
  }

  it('renders empty editor and add row button', () => {
    const wrapper = mountComponent();
    expect(wrapper.find('.multilingual-editor').exists()).toBe(true);
    expect(wrapper.find('.multilingual-card').exists()).toBe(false);
  });

  it('emits update:modelValue when addRow is clicked', async () => {
    const wrapper = mountComponent();
    await wrapper.find('.add-row-button').trigger('click');
    const events = wrapper.emitted('update:modelValue');
    expect(events).toHaveLength(1);
    expect((events![0] as any[])[0]).toHaveLength(1);
  });

  it('uses defaultLanguageId when addRow is clicked', async () => {
    const wrapper = mountComponent({ defaultLanguageId: 'lang-1' });
    await wrapper.find('.add-row-button').trigger('click');
    const events = wrapper.emitted('update:modelValue');
    expect((events![0] as any[])[0][0].languageId).toBe('lang-1');
  });

  it('detects PREFERRED conflict across rows', async () => {
    const wrapper = mountComponent({
      modelValue: [
        { id: 'd1', languageId: 'lang-1', value: 'A', contexts: [{ contextId: 'ctx-1', acceptability: 'PREFERRED' }] },
        { id: 'd2', languageId: 'lang-1', value: 'B', contexts: [{ contextId: 'ctx-1', acceptability: 'PREFERRED' }] },
      ],
    });
    await new Promise(r => setTimeout(r, 0));
    const exposed = wrapper.vm as any;
    expect(exposed.preferredConflicts()).toHaveLength(2);
  });

  it('detects isolated rows without context or pairing', async () => {
    const wrapper = mountComponent({
      modelValue: [{ id: 'd1', languageId: 'lang-1', value: 'A', contexts: [] }],
    });
    await new Promise(r => setTimeout(r, 0));
    const exposed = wrapper.vm as any;
    expect(exposed.isolatedWarnings()).toHaveLength(1);
  });

  it('toggles isolated exception and emits updated model', async () => {
    const wrapper = mountComponent({
      modelValue: [{ id: 'd1', languageId: 'lang-1', value: 'A', contexts: [] }],
    });
    await new Promise(r => setTimeout(r, 0));
    await wrapper.find('[data-testid="isolated-toggle"]').trigger('click');
    const events = wrapper.emitted('update:modelValue');
    expect(events).toBeTruthy();
    expect((events![events!.length - 1] as any[])[0][0].isolatedException).toBe(true);
  });

  // 回归：新建行（无 id）与后端 validateIsolatedLines 口径一致，应被判定为孤立行
  it('flags newly added rows without id as isolated', async () => {
    const wrapper = mountComponent({
      modelValue: [{ languageId: 'lang-1', value: 'Test', contexts: [] }],
    });
    await new Promise(r => setTimeout(r, 0));
    const exposed = wrapper.vm as any;
    expect(exposed.isolatedWarnings()).toHaveLength(1);
  });

  it('does not flag rows with blank value', async () => {
    const wrapper = mountComponent({
      modelValue: [{ languageId: 'lang-1', value: '  ', contexts: [] }],
    });
    await new Promise(r => setTimeout(r, 0));
    const exposed = wrapper.vm as any;
    expect(exposed.isolatedWarnings()).toHaveLength(0);
  });

  // 上下文自引用：传入 selfContextOption 时，上下文选择器常驻"当前上下文（自身）"选项，
  // 不依赖远程搜索（新建上下文尚无 id，以哨兵 NIL UUID 表示）
  it('renders self-context option when selfContextOption is provided', async () => {
    const wrapper = mountComponent({
      modelValue: [{ languageId: 'lang-1', value: '根上下文', contexts: [{ contextId: '00000000-0000-0000-0000-000000000000', acceptability: 'PREFERRED' }] }],
      selfContextOption: { id: '00000000-0000-0000-0000-000000000000', label: '当前上下文（自身）' },
    });
    await new Promise(r => setTimeout(r, 0));
    const options = wrapper.findAllComponents({ name: 'ElOption' });
    const self = options.find((o) => o.props('value') === '00000000-0000-0000-0000-000000000000');
    expect(self).toBeDefined();
    expect(self!.props('label')).toBe('当前上下文（自身）');
  });

  it('omits self-context option when selfContextOption is not provided', async () => {
    const wrapper = mountComponent({
      modelValue: [{ languageId: 'lang-1', value: 'A', contexts: [] }],
    });
    await new Promise(r => setTimeout(r, 0));
    const options = wrapper.findAllComponents({ name: 'ElOption' });
    expect(options.find((o) => o.props('value') === '00000000-0000-0000-0000-000000000000')).toBeUndefined();
  });

  it('语言下拉显示「中文名（标识符）」，无名称时只显示标识符', async () => {
    const wrapper = mountComponent({
      modelValue: [{ languageId: 'lang-1', value: 'A', contexts: [] }],
    });
    await new Promise(r => setTimeout(r, 0));
    const options = wrapper.findAllComponents({ name: 'ElOption' });
    expect(options.find((o) => o.props('value') === 'lang-1')!.props('label')).toBe('简体中文（zh-CN）');
    expect(options.find((o) => o.props('value') === 'lang-2')!.props('label')).toBe('xx-YY');
  });
});
