import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import DefinitionEditor from './DefinitionEditor.vue';

vi.mock('@/api/resources', () => ({
  languageApi: { list: vi.fn().mockResolvedValue({ content: [{ id: 'lang-1', languageIdentifier: 'zh-CN' }] }) },
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
});
