import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import MultilingualTextEditor from './MultilingualTextEditor.vue';

vi.mock('@/api/resources', () => ({
  languageApi: { list: vi.fn().mockResolvedValue({ content: [{ id: 'lang-1', languageIdentifier: 'zh-CN' }] }) },
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
});
