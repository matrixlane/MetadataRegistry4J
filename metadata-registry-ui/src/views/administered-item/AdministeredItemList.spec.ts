import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { createI18n } from 'vue-i18n';
import AdministeredItemList from './AdministeredItemList.vue';
import { administeredItemApi, registrationProcessApi } from '@/api/resources';
import zhCN from '@/i18n/zh-CN.json';

const transitionsFixture = vi.hoisted(() => [
  // 无需审核：当前用户可执行
  { to: 'RETIRED', displayName: null, requiresApproval: false, approvedCount: 0, quorum: 0, canExecute: true, canApprove: false, approvedByMe: false },
  // 需审核：当前用户可批准（未批准过）
  { to: 'QUALIFIED', displayName: '提交合格审定', requiresApproval: true, approvedCount: 0, quorum: 1, canExecute: false, canApprove: true, approvedByMe: false },
  // 需审核：当前用户已批准过（置灰）
  { to: 'STANDARD', displayName: null, requiresApproval: true, approvedCount: 1, quorum: 2, canExecute: false, canApprove: false, approvedByMe: true },
  // 需审核：当前用户无权限（只读）
  { to: 'HISTORICAL', displayName: null, requiresApproval: true, approvedCount: 0, quorum: 1, canExecute: false, canApprove: false, approvedByMe: false },
] as any[]);

vi.mock('@/api/resources', () => ({
  administeredItemApi: {
    list: vi.fn(),
    delete: vi.fn(),
  },
  registrationProcessApi: {
    availableTransitions: vi.fn(),
    execute: vi.fn(),
    approve: vi.fn(),
  },
}));

const mockedList = vi.mocked(administeredItemApi.list);
const mockedTransitions = vi.mocked(registrationProcessApi.availableTransitions);
const mockedExecute = vi.mocked(registrationProcessApi.execute);
const mockedApprove = vi.mocked(registrationProcessApi.approve);

function mountList() {
  const i18n = createI18n({
    legacy: false,
    locale: 'zh-CN',
    messages: { 'zh-CN': zhCN as any },
  });
  return mount(AdministeredItemList, {
    global: { plugins: [ElementPlus, i18n] },
    attachTo: document.body,
  });
}

const row = { id: 'item-1', identifier: 'DE.00001', registrationStatus: 'RECORDED', effectiveDate: '2026-09-18T00:00:00' };

async function openProcessDrawer(wrapper: any) {
  await wrapper.find('[data-test="open-process"]').trigger('click');
  await flushPromises();
}

describe('AdministeredItemList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedList.mockResolvedValue({ content: [row], page: 0, size: 20, totalElements: 1, totalPages: 1 } as any);
    mockedTransitions.mockResolvedValue(transitionsFixture);
    mockedExecute.mockResolvedValue({} as any);
    mockedApprove.mockResolvedValue({} as any);
  });

  it('列表加载注册项数据', async () => {
    const wrapper = mountList();
    await flushPromises();
    expect(mockedList).toHaveBeenCalled();
    expect(wrapper.text()).toContain('DE.00001');
  });

  it('选择注册状态过滤后，fetchFn 携带 status 参数查询', async () => {
    const wrapper = mountList();
    await flushPromises();
    const select = wrapper.findComponent({ name: 'ElSelect' });
    select.vm.$emit('update:modelValue', 'QUALIFIED');
    select.vm.$emit('change', 'QUALIFIED');
    await flushPromises();
    expect(mockedList).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'QUALIFIED' }));

    // 清空过滤后不再携带 status
    select.vm.$emit('update:modelValue', '');
    select.vm.$emit('change', '');
    await flushPromises();
    const lastCall = mockedList.mock.calls.at(-1)![0] as Record<string, unknown>;
    expect(lastCall.status).toBeUndefined();
  });

  it('推进流程面板：逐条展示操作名、目标状态、审核标记与审批进度', async () => {
    const wrapper = mountList();
    await flushPromises();
    await openProcessDrawer(wrapper);

    expect(mockedTransitions).toHaveBeenCalledWith('item-1');
    const items = wrapper.findAll('[data-test="transition-item"]');
    expect(items).toHaveLength(4);

    const text = (i: number) => items[i].text();
    // displayName 优先；无 displayName 使用默认"状态推进到 X"
    expect(text(0)).toContain('状态推进到 已退役');
    expect(text(1)).toContain('提交合格审定');
    // 目标状态与需审核标记
    expect(text(1)).toContain('已合格');
    expect(text(1)).toContain('需审核');
    // 审批进度 approvedCount/quorum
    expect(text(1)).toContain('0/1');
    expect(text(2)).toContain('1/2');
  });

  it('无需审核且可执行：点击执行弹出备注对话框，确认后调用 execute 并刷新', async () => {
    const wrapper = mountList();
    await flushPromises();
    await openProcessDrawer(wrapper);

    const items = wrapper.findAll('[data-test="transition-item"]');
    await items[0].find('[data-test="execute-btn"]').trigger('click');
    await flushPromises();

    const dialog = wrapper.find('[data-test="execute-dialog"]');
    expect(dialog.exists()).toBe(true);
    await wrapper.find('[data-test="execute-note"]').setValue('按计划退役');
    await wrapper.find('[data-test="execute-confirm"]').trigger('click');
    await flushPromises();

    expect(mockedExecute).toHaveBeenCalledWith('item-1', 'RETIRED', '按计划退役');
    // 操作成功后刷新面板与列表
    expect(mockedTransitions).toHaveBeenCalledTimes(2);
    expect(mockedList.mock.calls.length).toBeGreaterThan(1);
  });

  it('需审核且可批准：点击批准调用 approve 并刷新进度', async () => {
    const wrapper = mountList();
    await flushPromises();
    await openProcessDrawer(wrapper);

    const items = wrapper.findAll('[data-test="transition-item"]');
    await items[1].find('[data-test="approve-btn"]').trigger('click');
    await flushPromises();

    expect(mockedApprove).toHaveBeenCalledWith('item-1', 'QUALIFIED');
    expect(mockedTransitions).toHaveBeenCalledTimes(2);
  });

  it('已批准过的迁移显示置灰的"已批准"按钮', async () => {
    const wrapper = mountList();
    await flushPromises();
    await openProcessDrawer(wrapper);

    const items = wrapper.findAll('[data-test="transition-item"]');
    const approvedBtn = items[2].find('[data-test="approved-btn"]');
    expect(approvedBtn.exists()).toBe(true);
    expect(approvedBtn.attributes('disabled')).toBeDefined();
    expect(approvedBtn.text()).toContain('已批准');
  });

  it('无权限用户只看到只读进度文本', async () => {
    const wrapper = mountList();
    await flushPromises();
    await openProcessDrawer(wrapper);

    const items = wrapper.findAll('[data-test="transition-item"]');
    expect(items[3].find('[data-test="execute-btn"]').exists()).toBe(false);
    expect(items[3].find('[data-test="approve-btn"]').exists()).toBe(false);
    expect(items[3].text()).toContain('无操作权限（只读）');
  });

  it('当前状态无可用迁移时展示空态', async () => {
    mockedTransitions.mockResolvedValue([]);
    const wrapper = mountList();
    await flushPromises();
    await openProcessDrawer(wrapper);
    expect(wrapper.find('[data-test="transition-item"]').exists()).toBe(false);
  });
});
