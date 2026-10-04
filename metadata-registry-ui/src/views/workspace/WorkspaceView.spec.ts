import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus, { ElTable, ElMessageBox } from 'element-plus';
import { createPinia } from 'pinia';
import { createI18n } from 'vue-i18n';
import type { VueWrapper } from '@vue/test-utils';
import WorkspaceView from './WorkspaceView.vue';
import {
  workspaceApi,
  myContactApi,
  activeRegistrarApi,
  registrationProcessApi,
  administeredItemApi,
  dataElementApi,
  valueDomainApi,
} from '@/api/resources';
import zhCN from '@/i18n/zh-CN.json';

const summaryFixture = vi.hoisted(() => ({ drafts: 3, pending: 2, rejected: 1 }));

const rowFixtures = vi.hoisted(() => ({
  draft: {
    itemId: 'item-1',
    itemType: 'DataElement',
    label: '草稿项',
    registrationStatus: 'INCOMPLETE',
    administrativeStatus: 'draft',
    version: '1.0',
    hasRollbackBaseline: false,
    flags: {
      canSubmit: true,
      canRequestCandidate: true,
      canRequestQualification: false,
      canApprove: false,
      canReject: false,
      canRollback: false,
    },
  },
  pending: {
    itemId: 'item-2',
    itemType: 'ValueDomain',
    label: '待审核项',
    registrationStatus: 'RECORDED',
    administrativeStatus: 'provisionally qualified',
    version: '1.0',
    hasRollbackBaseline: false,
    flags: {
      canSubmit: false,
      canRequestCandidate: false,
      canRequestQualification: false,
      canApprove: true,
      canReject: true,
      canRollback: false,
    },
  },
  rejected: {
    itemId: 'item-3',
    itemType: 'ObjectClass',
    label: '已拒绝项',
    registrationStatus: 'RECORDED',
    administrativeStatus: 'rejected',
    version: '1.0',
    hasRollbackBaseline: true,
    flags: {
      canSubmit: false,
      canRequestCandidate: false,
      canRequestQualification: false,
      canApprove: false,
      canReject: false,
      canRollback: true,
    },
  },
}));

function pageOf(content: any[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    user: { id: 'user-1', username: 'test' },
    isRegistrarUser: true,
    isAdmin: false,
  }),
}));

vi.mock('@/api/resources', () => ({
  workspaceApi: {
    summary: vi.fn(),
    items: vi.fn(),
    submit: vi.fn(),
    requestCandidate: vi.fn(),
    rollback: vi.fn(),
  },
  myContactApi: { list: vi.fn() },
  activeRegistrarApi: { current: vi.fn() },
  registrationProcessApi: {
    availableTransitions: vi.fn(),
    approve: vi.fn(),
  },
  administeredItemApi: {
    requestQualification: vi.fn(),
    reject: vi.fn(),
  },
  dataElementApi: { get: vi.fn(), create: vi.fn(), update: vi.fn() },
  dataElementConceptApi: { get: vi.fn(), create: vi.fn(), update: vi.fn() },
  conceptualDomainApi: { get: vi.fn(), create: vi.fn(), update: vi.fn() },
  valueDomainApi: { get: vi.fn(), create: vi.fn(), update: vi.fn() },
  objectClassApi: { get: vi.fn(), create: vi.fn(), update: vi.fn() },
  propertyApi: { get: vi.fn(), create: vi.fn(), update: vi.fn() },
  dataTypeApi: { get: vi.fn(), create: vi.fn(), update: vi.fn() },
  dataTypeSchemeApi: { get: vi.fn(), create: vi.fn(), update: vi.fn() },
  unitOfMeasureApi: { get: vi.fn(), create: vi.fn(), update: vi.fn() },
  measureClassApi: { get: vi.fn(), create: vi.fn(), update: vi.fn() },
  dimensionalityApi: { get: vi.fn(), create: vi.fn(), update: vi.fn() },
  valueMeaningApi: { get: vi.fn(), create: vi.fn(), update: vi.fn() },
  contextApi: { get: vi.fn(), create: vi.fn(), update: vi.fn() },
}));

const mockedSummary = vi.mocked(workspaceApi.summary);
const mockedActiveCurrent = vi.mocked(activeRegistrarApi.current);
const mockedItems = vi.mocked(workspaceApi.items);
const mockedSubmit = vi.mocked(workspaceApi.submit);
const mockedRequestCandidate = vi.mocked(workspaceApi.requestCandidate);
const mockedRollback = vi.mocked(workspaceApi.rollback);
const mockedTransitions = vi.mocked(registrationProcessApi.availableTransitions);
const mockedApprove = vi.mocked(registrationProcessApi.approve);
const mockedReject = vi.mocked(administeredItemApi.reject);
const mockedValueDomainGet = vi.mocked(valueDomainApi.get);
const mockedDataElementGet = vi.mocked(dataElementApi.get);

function mountWorkspace() {
  const i18n = createI18n({
    legacy: false,
    locale: 'zh-CN',
    messages: { 'zh-CN': zhCN as any },
  });
  const pinia = createPinia();
  return mount(WorkspaceView, {
    attachTo: document.body,
    global: { plugins: [ElementPlus, i18n, pinia] },
  });
}

function selectRows(wrapper: VueWrapper, rows: any[]) {
  wrapper.findComponent(ElTable).vm.$emit('selection-change', rows);
}

const mounted: VueWrapper[] = [];

beforeEach(() => {
  vi.clearAllMocks();
  mockedSummary.mockResolvedValue(summaryFixture);
  // 默认无活动身份：预检按"任一身份组织均可"的放宽语义，保持既有用例行为
  mockedActiveCurrent.mockResolvedValue({ active: null, identities: [] });
  mockedItems.mockImplementation((view) => {
    if (view === 'DRAFTS') return Promise.resolve(pageOf([rowFixtures.draft]));
    if (view === 'PENDING') return Promise.resolve(pageOf([rowFixtures.pending]));
    return Promise.resolve(pageOf([rowFixtures.rejected]));
  });
  mockedSubmit.mockResolvedValue([]);
  mockedRequestCandidate.mockResolvedValue(undefined);
  mockedRollback.mockResolvedValue(undefined);
  mockedTransitions.mockResolvedValue([]);
  mockedApprove.mockResolvedValue({} as any);
  mockedReject.mockResolvedValue({} as any);
  mockedValueDomainGet.mockResolvedValue({ id: 'item-2', designations: [], definitions: [] });
  mockedDataElementGet.mockResolvedValue({ id: 'item-1', designations: [], definitions: [] });
});

afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount();
  document.body.innerHTML = '';
});

describe('WorkspaceView', () => {
  it('挂载时加载 summary 并在 Tab 上显示计数', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    expect(mockedSummary).toHaveBeenCalled();
    expect(wrapper.text()).toContain('草稿');
    expect(wrapper.text()).toContain('3');
    expect(wrapper.text()).toContain('待审核');
    expect(wrapper.text()).toContain('2');
  });

  it('默认展示草稿视图，切换 Tab 后重新拉取 items', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    expect(mockedItems).toHaveBeenLastCalledWith('DRAFTS', expect.any(Number), expect.any(Number));

    const tabs = wrapper.findAll('.el-tabs__item');
    const pendingTab = tabs.find((t) => t.text().includes('待审核'));
    expect(pendingTab).toBeTruthy();
    await pendingTab!.trigger('click');
    await flushPromises();

    expect(mockedItems).toHaveBeenLastCalledWith('PENDING', expect.any(Number), expect.any(Number));
  });

  it('按 flags 渲染行操作按钮', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    expect(wrapper.find('[data-testid="row-submit"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="row-request-candidate"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="row-approve"]').exists()).toBe(false);

    const tabs = wrapper.findAll('.el-tabs__item');
    const pendingTab = tabs.find((t) => t.text().includes('待审核'));
    await pendingTab!.trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="row-approve"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="row-reject"]').exists()).toBe(true);
  });

  it('多选草稿项后显示批量提交按钮，点击打开 picker', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    vi.mocked(myContactApi.list).mockResolvedValue([
      {
        id: 'contact-1',
        organizationId: 'org-1',
        organizationName: '组织 A',
        individualName: '张三',
        isRegistrar: false,
        isDefault: true,
      },
    ] as any);

    selectRows(wrapper, [rowFixtures.draft]);
    await wrapper.vm.$nextTick();
    await flushPromises();

    const batchBtn = wrapper.find('[data-testid="batch-submit-btn"]');
    expect(batchBtn.exists()).toBe(true);

    await batchBtn.trigger('click');
    await flushPromises();

    expect(wrapper.find('[data-testid="submission-organization-select"]').exists()).toBe(true);
  });

  it('回归：勾选行后表格仍然可见（不被批量条覆盖）', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    expect(wrapper.find('.el-table').exists()).toBe(true);

    selectRows(wrapper, [rowFixtures.draft]);
    await wrapper.vm.$nextTick();
    await flushPromises();

    expect(wrapper.find('.batch-bar').exists()).toBe(true);
    expect(wrapper.find('[data-testid="batch-submit-btn"]').exists()).toBe(true);
    expect(wrapper.find('.el-table').exists()).toBe(true);

    selectRows(wrapper, [
      rowFixtures.draft,
      { ...rowFixtures.draft, itemId: 'item-1b', label: '草稿项 B', flags: { ...rowFixtures.draft.flags, canSubmit: false } },
    ]);
    await wrapper.vm.$nextTick();
    await flushPromises();

    expect(wrapper.find('.batch-bar').exists()).toBe(true);
    expect(wrapper.find('.el-table').exists()).toBe(true);
    expect((wrapper.find('[data-testid="batch-submit-btn"]').element as HTMLButtonElement).disabled).toBe(false);
  });

  it('行级提交打开 picker，确认后调用 workspaceApi.submit', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    vi.mocked(myContactApi.list).mockResolvedValue([
      {
        id: 'contact-1',
        organizationId: 'org-1',
        organizationName: '组织 A',
        individualName: '张三',
        isRegistrar: false,
        isDefault: true,
      },
    ] as any);

    await wrapper.find('[data-testid="row-submit"]').trigger('click');
    await flushPromises();

    await wrapper.find('[data-testid="submission-confirm"]').trigger('click');
    await flushPromises();

    expect(mockedSubmit).toHaveBeenCalledWith(['item-1'], { contactId: 'contact-1', organizationId: 'org-1' });
  });

  it('请求成为候选打开 picker并调用 requestCandidate', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    vi.mocked(myContactApi.list).mockResolvedValue([
      {
        id: 'contact-1',
        organizationId: 'org-1',
        organizationName: '组织 A',
        individualName: '张三',
        isRegistrar: false,
        isDefault: true,
      },
    ] as any);

    await wrapper.find('[data-testid="row-request-candidate"]').trigger('click');
    await flushPromises();

    await wrapper.find('[data-testid="submission-confirm"]').trigger('click');
    await flushPromises();

    expect(mockedRequestCandidate).toHaveBeenCalledWith('item-1', { contactId: 'contact-1', organizationId: 'org-1' });
  });

  it('批准操作查询可用迁移后调用 approve', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    const tabs = wrapper.findAll('.el-tabs__item');
    const pendingTab = tabs.find((t) => t.text().includes('待审核'));
    await pendingTab!.trigger('click');
    await flushPromises();

    mockedTransitions.mockResolvedValue([
      { to: 'QUALIFIED', displayName: null, requiresApproval: true, approvedCount: 0, quorum: 1, canExecute: false, canApprove: true, approvedByMe: false },
    ] as any);

    await wrapper.find('[data-testid="row-approve"]').trigger('click');
    await flushPromises();

    expect(mockedTransitions).toHaveBeenCalledWith('item-2');
    expect(mockedApprove).toHaveBeenCalledWith('item-2', 'QUALIFIED');
  });

  it('回退操作确认后调用 workspaceApi.rollback', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    const tabs = wrapper.findAll('.el-tabs__item');
    const rejectedTab = tabs.find((t) => t.text().includes('已拒绝'));
    await rejectedTab!.trigger('click');
    await flushPromises();

    const confirmSpy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(undefined as never);

    await wrapper.find('[data-testid="row-rollback"]').trigger('click');
    await flushPromises();

    expect(confirmSpy).toHaveBeenCalled();
    expect(mockedRollback).toHaveBeenCalledWith('item-3');
  });

  // ===== acg-submission：提交入口联系人身份预检 =====

  it('无任何联系人身份时，行级提交被阻止并提示', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    vi.mocked(myContactApi.list).mockResolvedValue([] as any);
    const alertSpy = vi.spyOn(ElMessageBox, 'alert').mockResolvedValue(undefined as never);

    await wrapper.find('[data-testid="row-submit"]').trigger('click');
    await flushPromises();

    expect(alertSpy).toHaveBeenCalled();
    expect(alertSpy.mock.calls[0][0]).toContain('创建联系人身份');
    expect(wrapper.find('[data-testid="submission-organization-select"]').exists()).toBe(false);
    alertSpy.mockRestore();
  });

  it('无任何联系人身份时，批量提交被阻止并提示', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    vi.mocked(myContactApi.list).mockResolvedValue([] as any);
    const alertSpy = vi.spyOn(ElMessageBox, 'alert').mockResolvedValue(undefined as never);

    selectRows(wrapper, [rowFixtures.draft]);
    await wrapper.vm.$nextTick();
    await flushPromises();

    await wrapper.find('[data-testid="batch-submit-btn"]').trigger('click');
    await flushPromises();

    expect(alertSpy).toHaveBeenCalled();
    expect(wrapper.find('[data-testid="submission-organization-select"]').exists()).toBe(false);
    alertSpy.mockRestore();
  });

  it('无任何联系人身份时，请求成为候选被阻止并提示', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    vi.mocked(myContactApi.list).mockResolvedValue([] as any);
    const alertSpy = vi.spyOn(ElMessageBox, 'alert').mockResolvedValue(undefined as never);

    await wrapper.find('[data-testid="row-request-candidate"]').trigger('click');
    await flushPromises();

    expect(alertSpy).toHaveBeenCalled();
    expect(wrapper.find('[data-testid="submission-organization-select"]').exists()).toBe(false);
    alertSpy.mockRestore();
  });

  it('M1：身份仅在其它组织时（活动 Registrar 机构下无身份），提交被阻止并提示且不打开 picker', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    vi.mocked(myContactApi.list).mockResolvedValue([
      {
        id: 'contact-1',
        organizationId: 'org-1',
        organizationName: '组织 A',
        individualName: '张三',
        isRegistrar: false,
        isDefault: true,
      },
    ] as any);
    mockedActiveCurrent.mockResolvedValue({
      active: {
        registrarId: 'contact-ra-2',
        registrationAuthorityId: 'ra-2',
        registrationAuthorityName: '机构 B',
      },
      identities: [
        {
          registrarId: 'contact-ra-2',
          registrationAuthorityId: 'ra-2',
          registrationAuthorityName: '机构 B',
        },
      ],
    } as any);
    const alertSpy = vi.spyOn(ElMessageBox, 'alert').mockResolvedValue(undefined as never);

    await wrapper.find('[data-testid="row-submit"]').trigger('click');
    await flushPromises();

    expect(mockedActiveCurrent).toHaveBeenCalled();
    expect(alertSpy).toHaveBeenCalled();
    expect(alertSpy.mock.calls[0][0]).toContain('创建联系人身份');
    expect(wrapper.find('[data-testid="submission-organization-select"]').exists()).toBe(false);
    alertSpy.mockRestore();
  });

  it('M1：存在活动 Registrar 身份时，行级提交正常打开 picker', async () => {
    const wrapper = mountWorkspace();
    mounted.push(wrapper);
    await flushPromises();

    vi.mocked(myContactApi.list).mockResolvedValue([
      {
        id: 'contact-1',
        organizationId: 'org-1',
        organizationName: '组织 A',
        individualName: '张三',
        isRegistrar: false,
        isDefault: true,
      },
      {
        id: 'contact-2',
        organizationId: 'org-2',
        organizationName: '组织 B',
        individualName: '李四',
        isRegistrar: true,
        registrationAuthorityId: 'ra-2',
        registrationAuthorityName: '机构 B',
        isDefault: false,
      },
    ] as any);
    mockedActiveCurrent.mockResolvedValue({
      active: {
        registrarId: 'contact-2',
        registrationAuthorityId: 'ra-2',
        registrationAuthorityName: '机构 B',
      },
      identities: [
        {
          registrarId: 'contact-2',
          registrationAuthorityId: 'ra-2',
          registrationAuthorityName: '机构 B',
        },
      ],
    } as any);
    const alertSpy = vi.spyOn(ElMessageBox, 'alert').mockResolvedValue(undefined as never);

    await wrapper.find('[data-testid="row-submit"]').trigger('click');
    await flushPromises();

    expect(alertSpy).not.toHaveBeenCalled();
    expect(wrapper.find('[data-testid="submission-organization-select"]').exists()).toBe(true);
    alertSpy.mockRestore();
  });
});
