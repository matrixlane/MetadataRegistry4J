import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus, { ElSelect, ElInput } from 'element-plus';
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

  it('allows starting from empty when create callbacks are provided', () => {
    const wrapper = mountComponent({
      designations: [],
      definitions: [],
      createDesignation: vi.fn(),
      createDefinition: vi.fn(),
    });
    expect(wrapper.find('.pairing-empty').exists()).toBe(false);
    expect(wrapper.find('.pairing-list').exists()).toBe(true);
  });

  it('creates and links a new designation line when text is typed with no link', async () => {
    const createDesignation = vi.fn().mockReturnValue('ck-new-1');
    const row = { designationId: '', definitionId: '', contextId: '', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({ modelValue: [row], createDesignation });
    await flushPromises();
    const inputs = wrapper.findAllComponents(ElInput);
    expect(inputs.length).toBeGreaterThan(0);
    inputs[0].vm.$emit('update:modelValue', '新指称文本');
    await flushPromises();
    expect(createDesignation).toHaveBeenCalledWith('新指称文本');
    expect(row.designationId).toBe('ck-new-1');
  });

  it('creates and links a new definition line when text is typed with no link', async () => {
    const createDefinition = vi.fn().mockReturnValue('ck-new-2');
    const row = { designationId: 'd1', definitionId: '', contextId: '', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({ modelValue: [row], createDefinition });
    await flushPromises();
    const inputs = wrapper.findAllComponents(ElInput);
    inputs[1].vm.$emit('update:modelValue', '  新定义文本  ');
    await flushPromises();
    expect(createDefinition).toHaveBeenCalledWith('新定义文本');
    expect(row.definitionId).toBe('ck-new-2');
  });

  it('edits an existing linked designation in place via updateDesignation', async () => {
    const createDesignation = vi.fn();
    const updateDesignation = vi.fn();
    const row = { designationId: 'd1', definitionId: 'f1', contextId: 'ctx-1', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({ modelValue: [row], createDesignation, updateDesignation });
    await flushPromises();
    const inputs = wrapper.findAllComponents(ElInput);
    // 文本框回显既有行文本
    expect((inputs[0].vm as any).modelValue).toBe('指称一');
    inputs[0].vm.$emit('update:modelValue', '指称一（修订）');
    await flushPromises();
    expect(updateDesignation).toHaveBeenCalledWith('d1', '指称一（修订）');
    expect(createDesignation).not.toHaveBeenCalled();
  });

  it('edits an existing linked definition in place via updateDefinition', async () => {
    const updateDefinition = vi.fn();
    const row = { designationId: 'd1', definitionId: 'f1', contextId: 'ctx-1', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({ modelValue: [row], updateDefinition });
    await flushPromises();
    const inputs = wrapper.findAllComponents(ElInput);
    expect((inputs[1].vm as any).modelValue).toBe('定义一');
    inputs[1].vm.$emit('update:modelValue', '定义一（修订）');
    await flushPromises();
    expect(updateDefinition).toHaveBeenCalledWith('f1', '定义一（修订）');
  });

  it('link select switches the row to an existing designation', async () => {
    const row = { designationId: '', definitionId: '', contextId: '', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({
      modelValue: [row],
      designations: [
        { id: 'd1', value: '指称一', languageCode: 'zh-CN' },
        { id: 'd2', value: '指称二', languageCode: 'zh-CN' },
      ],
    });
    await flushPromises();
    const selects = wrapper.findAllComponents(ElSelect);
    selects[0].vm.$emit('change', 'd2');
    await flushPromises();
    expect(row.designationId).toBe('d2');
  });

  it('clears the link when the link select is cleared', async () => {
    const row = { designationId: 'd1', definitionId: 'f1', contextId: '', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({ modelValue: [row] });
    await flushPromises();
    const selects = wrapper.findAllComponents(ElSelect);
    selects[0].vm.$emit('change', '');
    await flushPromises();
    expect(row.designationId).toBe('');
  });

  it('disables text inputs for legacy hosts without create/update callbacks', async () => {
    const row = { designationId: 'd1', definitionId: 'f1', contextId: '', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({ modelValue: [row] });
    await flushPromises();
    const inputs = wrapper.findAllComponents(ElInput);
    expect((inputs[0].vm as any).disabled).toBe(true);
    expect((inputs[1].vm as any).disabled).toBe(true);
  });
});
