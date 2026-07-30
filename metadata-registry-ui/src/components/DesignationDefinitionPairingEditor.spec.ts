import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import DesignationDefinitionPairingEditor from './DesignationDefinitionPairingEditor.vue';

vi.mock('@/api/resources', () => ({
  contextApi: { options: vi.fn().mockResolvedValue([{ id: 'ctx-1', label: '默认上下文' }]) },
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: any) => (params ? `${key} ${JSON.stringify(params)}` : key),
  }),
}));

describe('DesignationDefinitionPairingEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  function mountComponent(props: any = {}) {
    return mount(DesignationDefinitionPairingEditor, {
      props: {
        modelValue: [],
        designations: [{ id: 'd1', value: '指称一', languageCode: 'zh-CN' }],
        definitions: [{ id: 'f1', value: '定义一', languageCode: 'zh-CN' }],
        ...props,
      },
      global: {
        plugins: [ElementPlus],
      },
    });
  }

  it('renders empty prompt when no designations or definitions', () => {
    const wrapper = mountComponent({ designations: [], definitions: [] });
    expect(wrapper.find('.pairing-empty').exists()).toBe(true);
  });

  it('emits update:modelValue when addRow is clicked', async () => {
    const wrapper = mountComponent();
    await flushPromises();
    expect(wrapper.find('.pairing-list').exists()).toBe(true);
    await wrapper.find('.add-pairing-button').trigger('click');
    const events = wrapper.emitted('update:modelValue');
    expect(events).toHaveLength(1);
    const next = (events![0] as any[])[0];
    expect(next).toHaveLength(1);
    expect(next[0].acceptability).toBe('ADMITTED');
  });

  it('validates contextId is required', async () => {
    const wrapper = mountComponent({
      modelValue: [{ designationId: 'd1', definitionId: 'f1', contextId: '', acceptability: 'ADMITTED' }],
    });
    const exposed = wrapper.vm as any;
    expect(exposed.validate()).toBe(false);
    expect(exposed.hasError(0)).toBe(true);
  });

  it('detects duplicate pairings', async () => {
    const wrapper = mountComponent({
      modelValue: [
        { designationId: 'd1', definitionId: 'f1', contextId: 'ctx-1', acceptability: 'ADMITTED' },
        { designationId: 'd1', definitionId: 'f1', contextId: 'ctx-1', acceptability: 'PREFERRED' },
      ],
    });
    const exposed = wrapper.vm as any;
    expect(exposed.isDuplicate(0)).toBe(false);
    expect(exposed.isDuplicate(1)).toBe(true);
    expect(exposed.validate()).toBe(false);
  });
});
