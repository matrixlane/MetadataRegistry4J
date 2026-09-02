import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus, { ElSelect } from 'element-plus';
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
        designations: [{ id: 'd1', value: '指称一', languageCode: 'zh-CN', persisted: true }],
        definitions: [{ id: 'f1', value: '定义一', languageCode: 'zh-CN', persisted: true }],
        ...props,
      },
      global: {
        plugins: [ElementPlus],
      },
    });
  }

  it('renders empty prompt when no designations or definitions (legacy host)', () => {
    const wrapper = mountComponent({ designations: [], definitions: [] });
    expect(wrapper.find('.pairing-empty').exists()).toBe(true);
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

  it('creates and links a new designation when new text is typed in the merged control', async () => {
    const createDesignation = vi.fn().mockReturnValue('ck-new-1');
    const row = { designationId: '', definitionId: '', contextId: '', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({ modelValue: [row], createDesignation });
    await flushPromises();
    const selects = wrapper.findAllComponents(ElSelect);
    selects[0].vm.$emit('change', '新指称文本');
    await flushPromises();
    expect(createDesignation).toHaveBeenCalledWith('新指称文本');
    expect(row.designationId).toBe('ck-new-1');
  });

  it('trims whitespace when creating a new definition from typed text', async () => {
    const createDefinition = vi.fn().mockReturnValue('ck-new-2');
    const row = { designationId: 'd1', definitionId: '', contextId: '', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({ modelValue: [row], createDefinition });
    await flushPromises();
    const selects = wrapper.findAllComponents(ElSelect);
    selects[1].vm.$emit('change', '  新定义文本  ');
    await flushPromises();
    expect(createDefinition).toHaveBeenCalledWith('新定义文本');
    expect(row.definitionId).toBe('ck-new-2');
  });

  it('links an existing option id without invoking the create callback', async () => {
    const createDesignation = vi.fn();
    const row = { designationId: '', definitionId: '', contextId: '', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({ modelValue: [row], createDesignation });
    await flushPromises();
    const selects = wrapper.findAllComponents(ElSelect);
    selects[0].vm.$emit('change', 'd1');
    await flushPromises();
    expect(createDesignation).not.toHaveBeenCalled();
    expect(row.designationId).toBe('d1');
  });

  it('ignores typed text when create callback is absent (legacy hosts)', async () => {
    const row = { designationId: '', definitionId: '', contextId: '', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({ modelValue: [row] });
    await flushPromises();
    const selects = wrapper.findAllComponents(ElSelect);
    selects[0].vm.$emit('change', '任意文本');
    await flushPromises();
    expect(row.designationId).toBe('');
  });

  it('unlinks an orphan inline-created designation line when the row is cleared', async () => {
    const unlinkDesignation = vi.fn();
    const row = { designationId: 'ck-1', definitionId: 'f1', contextId: 'ctx-1', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({
      modelValue: [row],
      designations: [
        { id: 'd1', value: '指称一', persisted: true },
        { id: 'ck-1', value: '内联新指称', persisted: false },
      ],
      unlinkDesignation,
    });
    await flushPromises();
    const selects = wrapper.findAllComponents(ElSelect);
    selects[0].vm.$emit('change', '');
    await flushPromises();
    expect(row.designationId).toBe('');
    expect(unlinkDesignation).toHaveBeenCalledWith('ck-1');
  });

  it('removes the orphan line when switching from an inline-created line to an existing one', async () => {
    const unlinkDesignation = vi.fn();
    const row = { designationId: 'ck-1', definitionId: 'f1', contextId: 'ctx-1', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({
      modelValue: [row],
      designations: [
        { id: 'd1', value: '指称一', persisted: true },
        { id: 'ck-1', value: '内联新指称', persisted: false },
      ],
      unlinkDesignation,
    });
    await flushPromises();
    const selects = wrapper.findAllComponents(ElSelect);
    selects[0].vm.$emit('change', 'd1');
    await flushPromises();
    expect(row.designationId).toBe('d1');
    expect(unlinkDesignation).toHaveBeenCalledWith('ck-1');
  });

  it('keeps the inline line when another pairing still references it', async () => {
    const unlinkDesignation = vi.fn();
    const rows = [
      { designationId: 'ck-1', definitionId: 'f1', contextId: 'ctx-1', acceptability: 'ADMITTED' },
      { designationId: 'ck-1', definitionId: 'f1', contextId: 'ctx-2', acceptability: 'ADMITTED' },
    ];
    const wrapper = mountComponent({
      modelValue: rows,
      designations: [
        { id: 'd1', value: '指称一', persisted: true },
        { id: 'ck-1', value: '内联新指称', persisted: false },
      ],
      unlinkDesignation,
    });
    await flushPromises();
    const selects = wrapper.findAllComponents(ElSelect);
    selects[0].vm.$emit('change', '');
    await flushPromises();
    expect(rows[0].designationId).toBe('');
    expect(unlinkDesignation).not.toHaveBeenCalled();
  });

  it('never unlinks a persisted designation instance', async () => {
    const unlinkDesignation = vi.fn();
    const row = { designationId: 'd1', definitionId: 'f1', contextId: 'ctx-1', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({ modelValue: [row], unlinkDesignation });
    await flushPromises();
    const selects = wrapper.findAllComponents(ElSelect);
    selects[0].vm.$emit('change', '');
    await flushPromises();
    expect(row.designationId).toBe('');
    expect(unlinkDesignation).not.toHaveBeenCalled();
  });

  it('saved pairings are read-only except deletion', async () => {
    const row = { id: 'p1', designationId: 'd1', definitionId: 'f1', contextId: 'ctx-1', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({
      modelValue: [row],
      createDesignation: vi.fn(),
      createDefinition: vi.fn(),
      unlinkDesignation: vi.fn(),
      unlinkDefinition: vi.fn(),
    });
    await flushPromises();
    const selects = wrapper.findAllComponents(ElSelect);
    // 指称 / 定义 / 上下文 / 可接受性 全部禁用
    expect((selects[0].vm as any).disabled).toBe(true);
    expect((selects[1].vm as any).disabled).toBe(true);
    expect((selects[2].vm as any).disabled).toBe(true);
    expect((selects[3].vm as any).disabled).toBe(true);
    // 已保存标记可见，删除按钮仍可用
    expect(wrapper.find('.pairing-saved-tag').exists()).toBe(true);
    const del = wrapper.find('.pairing-delete-button');
    expect(del.exists()).toBe(true);
    expect(del.attributes('disabled')).toBeUndefined();
  });

  it('removes the pairing row and unlinks orphan inline lines on delete', async () => {
    const unlinkDesignation = vi.fn();
    const row = { designationId: 'ck-1', definitionId: 'f1', contextId: 'ctx-1', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({
      modelValue: [row],
      designations: [
        { id: 'd1', value: '指称一', persisted: true },
        { id: 'ck-1', value: '内联新指称', persisted: false },
      ],
      unlinkDesignation,
    });
    await flushPromises();
    await wrapper.find('.pairing-delete-button').trigger('click');
    const events = wrapper.emitted('update:modelValue');
    expect((events!.at(-1) as any[])[0]).toHaveLength(0);
    expect(unlinkDesignation).toHaveBeenCalledWith('ck-1');
  });

  it('deleting a saved pairing emits deleteSavedRow instead of filtering the list', async () => {
    const unlinkDesignation = vi.fn();
    const row = { id: 'p1', designationId: 'd1', definitionId: 'f1', contextId: 'ctx-1', acceptability: 'ADMITTED' };
    const wrapper = mountComponent({
      modelValue: [row],
      unlinkDesignation,
    });
    await flushPromises();
    await wrapper.find('.pairing-delete-button').trigger('click');
    // 已保存配对不直接移除：交给宿主弹删除确认（仅删关联 / 连同指称定义一并删除）
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();
    const events = wrapper.emitted('deleteSavedRow');
    expect(events).toHaveLength(1);
    expect((events![0] as any[])[0]).toStrictEqual(row);
    expect((events![0] as any[])[1]).toBe(0);
    expect(unlinkDesignation).not.toHaveBeenCalled();
  });
});
