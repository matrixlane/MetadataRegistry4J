import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import DefinitionEditor from './DefinitionEditor.vue';

vi.mock('@/api/resources', () => ({
  languageApi: {
    list: vi.fn().mockResolvedValue({
      content: [
        { id: 'lang-1', languageIdentifier: 'zh-CN', languageName: '简体中文' },
        { id: 'lang-2', languageIdentifier: 'xx-YY' },
      ],
    }),
  },
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

describe('DefinitionEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  function mountComponent(props: any = {}) {
    return mount(DefinitionEditor, {
      props: {
        modelValue: [],
        ...props,
      },
      global: {
        plugins: [ElementPlus],
        mocks: { $t: (key: string) => key },
      },
    });
  }

  it('新增行使用默认语言', async () => {
    const wrapper = mountComponent({ defaultLanguageId: 'lang-1' });
    await wrapper.find('.el-button').trigger('click');
    const events = wrapper.emitted('update:modelValue');
    expect(events).toHaveLength(1);
    expect((events![0] as any[])[0][0].languageId).toBe('lang-1');
  });

  it('语言下拉显示「中文名（标识符）」，无名称时只显示标识符', async () => {
    const wrapper = mountComponent({
      modelValue: [{ id: 'd1', languageId: 'lang-1', value: 'A' }],
    });
    await new Promise((r) => setTimeout(r, 0));
    const options = wrapper.findAllComponents({ name: 'ElOption' });
    expect(options.find((o) => o.props('value') === 'lang-1')!.props('label')).toBe('简体中文（zh-CN）');
    expect(options.find((o) => o.props('value') === 'lang-2')!.props('label')).toBe('xx-YY');
  });
});
