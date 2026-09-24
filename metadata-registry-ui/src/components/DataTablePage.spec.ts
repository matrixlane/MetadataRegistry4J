import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus, { ElTable, ElMessage, ElMessageBox } from 'element-plus';
import { createI18n } from 'vue-i18n';
import type { VueWrapper } from '@vue/test-utils';
import DataTablePage from './DataTablePage.vue';
import zhCN from '@/i18n/zh-CN.json';

vi.mock('@/utils/request', () => ({
  withSilentErrors: vi.fn((fn: () => unknown) => fn()),
}));

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': zhCN },
});

const confirmSpy = vi.spyOn(ElMessageBox, 'confirm');
const successSpy = vi.spyOn(ElMessage, 'success');
const warningSpy = vi.spyOn(ElMessage, 'warning');

const PAGE = {
  content: [
    { id: '1', name: '甲' },
    { id: '2', name: '乙' },
  ],
  totalElements: 2,
  number: 0,
  size: 20,
};

const mounted: VueWrapper[] = [];

function mountTable(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  const wrapper = mount(DataTablePage, {
    attachTo: document.body,
    props: { fetchFn: vi.fn().mockResolvedValue(PAGE), ...props },
    global: { plugins: [ElementPlus, i18n] },
    slots: {
      columns: `<el-table-column prop="name" label="名称" />`,
      ...slots,
    },
  });
  mounted.push(wrapper);
  return wrapper;
}

/** 直接驱动 el-table 的 selection-change（等价于勾选行） */
function selectRows(wrapper: VueWrapper, rows: Record<string, unknown>[]) {
  wrapper.findComponent(ElTable).vm.$emit('selection-change', rows);
}

async function clickBatchDelete(wrapper: VueWrapper) {
  const btn = wrapper.findAll('button').find((b) => b.text().includes('批量删除'));
  expect(btn).toBeTruthy();
  await btn!.trigger('click');
  await flushPromises();
}

beforeEach(() => {
  vi.clearAllMocks();
  confirmSpy.mockResolvedValue(undefined as never);
});

afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount();
  document.body.innerHTML = '';
});

describe('DataTablePage 批量删除', () => {
  it('未传 deleteFn：不渲染多选列与批量入口', async () => {
    const wrapper = mountTable();
    await flushPromises();

    expect(wrapper.find('.batch-bar').exists()).toBe(false);
    // 表头无 selection 列的复选框
    expect(wrapper.find('.el-table__header-wrapper .el-checkbox').exists()).toBe(false);
  });

  it('传入 selectable：渲染多选列但不渲染批量删除条，勾选后向父组件抛出 selection-change', async () => {
    const wrapper = mountTable({ selectable: true });
    await flushPromises();

    expect(wrapper.find('.batch-bar').exists()).toBe(false);
    expect(wrapper.find('.el-table__header-wrapper .el-checkbox').exists()).toBe(true);

    const rows = [{ id: '1' }, { id: '2' }];
    selectRows(wrapper, rows);
    await wrapper.vm.$nextTick();

    expect(wrapper.emitted('selection-change')?.[0]).toEqual([rows]);
    expect((wrapper.vm as any).selectedRows).toEqual(rows);
  });

  it('传入 deleteFn：渲染多选列与批量条，勾选后确认删除→按 id 调用→成功汇总→重新取数', async () => {
    const fetchFn = vi.fn().mockResolvedValue(PAGE);
    const deleteFn = vi.fn().mockResolvedValue(undefined);
    const wrapper = mountTable({ fetchFn, deleteFn });
    await flushPromises();

    expect(wrapper.find('.batch-bar').exists()).toBe(true);
    expect(wrapper.find('.el-table__header-wrapper .el-checkbox').exists()).toBe(true);

    const btn = wrapper.findAll('button').find((b) => b.text().includes('批量删除'));
    expect((btn!.element as HTMLButtonElement).disabled).toBe(true);

    selectRows(wrapper, [{ id: '1' }, { id: '2' }]);
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.batch-bar-text').text()).toContain('已选 2 条');

    await clickBatchDelete(wrapper);

    expect(confirmSpy).toHaveBeenCalledWith('确认删除选中的 2 条记录？', '提示', { type: 'warning' });
    expect(deleteFn.mock.calls.map((c) => c[0]).sort()).toEqual(['1', '2']);
    expect(successSpy).toHaveBeenCalledWith('已删除 2 条记录');
    expect(fetchFn).toHaveBeenCalledTimes(2);
  });

  it('部分失败：warning 汇总首条原因且仍重新取数', async () => {
    const fetchFn = vi.fn().mockResolvedValue(PAGE);
    const deleteFn = vi
      .fn()
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(new Error('被引用，无法删除'));
    const wrapper = mountTable({ fetchFn, deleteFn });
    await flushPromises();

    selectRows(wrapper, [{ id: '1' }, { id: '2' }]);
    await wrapper.vm.$nextTick();
    await clickBatchDelete(wrapper);

    expect(warningSpy).toHaveBeenCalledWith('成功 1 条，失败 1 条：被引用，无法删除');
    expect(successSpy).not.toHaveBeenCalled();
    expect(fetchFn).toHaveBeenCalledTimes(2);
  });

  it('用户取消确认：不调用 deleteFn，不重新取数', async () => {
    const fetchFn = vi.fn().mockResolvedValue(PAGE);
    const deleteFn = vi.fn().mockResolvedValue(undefined);
    confirmSpy.mockRejectedValue('cancel' as never);
    const wrapper = mountTable({ fetchFn, deleteFn });
    await flushPromises();

    selectRows(wrapper, [{ id: '1' }]);
    await wrapper.vm.$nextTick();
    await clickBatchDelete(wrapper);

    expect(deleteFn).not.toHaveBeenCalled();
    expect(fetchFn).toHaveBeenCalledTimes(1);
  });
});

describe('DataTablePage 自定义批量条', () => {
  it('未传 #batch-actions：不渲染批量条', async () => {
    const wrapper = mountTable({ selectable: true });
    await flushPromises();

    expect(wrapper.find('.batch-bar').exists()).toBe(false);

    selectRows(wrapper, [{ id: '1' }]);
    await wrapper.vm.$nextTick();

    expect(wrapper.find('.batch-bar').exists()).toBe(false);
  });

  it('传 #batch-actions 插槽：勾选后渲染批量条，表格仍然可见且插槽内容出现', async () => {
    const wrapper = mountTable(
      { selectable: true },
      {
        'batch-actions': `<el-button data-testid="custom-batch-btn">批量提交</el-button>`,
      }
    );
    await flushPromises();

    expect(wrapper.find('.batch-bar').exists()).toBe(false);

    selectRows(wrapper, [{ id: '1' }, { id: '2' }]);
    await wrapper.vm.$nextTick();

    expect(wrapper.find('.batch-bar').exists()).toBe(true);
    expect(wrapper.find('.batch-bar-text').text()).toContain('已选 2 条');
    expect(wrapper.find('[data-testid="custom-batch-btn"]').exists()).toBe(true);
    expect(wrapper.find('.el-table').exists()).toBe(true);
  });

  it('批量条显隐跟随选中行变化：取消勾选后批量条隐藏', async () => {
    const wrapper = mountTable(
      { selectable: true },
      {
        'batch-actions': `<el-button data-testid="custom-batch-btn">批量提交</el-button>`,
      }
    );
    await flushPromises();

    selectRows(wrapper, [{ id: '1' }]);
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.batch-bar').exists()).toBe(true);

    selectRows(wrapper, []);
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.batch-bar').exists()).toBe(false);
  });
});