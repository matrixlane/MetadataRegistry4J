import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import DesignationEditor from './DesignationEditor.vue';

vi.mock('@/api/resources', () => ({
  languageApi: { list: vi.fn().mockResolvedValue({ content: [{ id: 'lang-1', languageIdentifier: 'zh-CN' }] }) },
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

describe('DesignationEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  function mountComponent(props: any = {}) {
    return mount(DesignationEditor, {
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

  it('无默认语言时新增行语言为空', async () => {
    const wrapper = mountComponent();
    await wrapper.find('.el-button').trigger('click');
    const events = wrapper.emitted('update:modelValue');
    expect((events![0] as any[])[0][0].languageId).toBe('');
  });
});
