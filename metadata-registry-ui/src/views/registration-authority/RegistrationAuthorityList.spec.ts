import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus, { ElMessageBox } from 'element-plus';
import { createI18n } from 'vue-i18n';
import type { VueWrapper } from '@vue/test-utils';
import RegistrationAuthorityList from './RegistrationAuthorityList.vue';
import { registrationAuthorityApi } from '@/api/resources';
import request from '@/utils/request';
import zhCN from '@/i18n/zh-CN.json';

const raRow = vi.hoisted(() => ({
  id: 'ra-1',
  organizationIdentifier: 'RA-001',
  name: '示例机构',
  deletable: true,
}));

const raPrimaryRow = vi.hoisted(() => ({
  id: 'ra-primary',
  organizationIdentifier: 'RA-PRIMARY',
  name: '主要注册机构',
  deletable: false,
}));

const previewFixture = vi.hoisted(() => ({
  registrationAuthorityId: 'ra-1',
  administeredItemCount: 3,
  submissionRecordCount: 2,
  registrarCount: 1,
  namespaceCount: 4,
  stewardshipRecordCount: 0,
  transitionRuleCount: 5,
}));

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
  },
}));

vi.mock('@/api/resources', () => ({
  registrationAuthorityApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    deletionPreview: vi.fn(),
  },
}));

const mockedStatusGet = vi.mocked(request.get);
const mockedList = vi.mocked(registrationAuthorityApi.list);
const mockedDelete = vi.mocked(registrationAuthorityApi.delete);
const mockedPreview = vi.mocked(registrationAuthorityApi.deletionPreview);

function pageOf(content: any[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function mountList() {
  const i18n = createI18n({
    legacy: false,
    locale: 'zh-CN',
    messages: { 'zh-CN': zhCN as any },
  });
  return mount(RegistrationAuthorityList, {
    attachTo: document.body,
    global: { plugins: [ElementPlus, i18n] },
  });
}

const mounted: VueWrapper[] = [];

beforeEach(() => {
  vi.clearAllMocks();
  mockedList.mockResolvedValue(pageOf([raRow]) as any);
  mockedPreview.mockResolvedValue(previewFixture as any);
  mockedDelete.mockResolvedValue(undefined as any);
});

afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount();
  document.body.innerHTML = '';
});

describe('RegistrationAuthorityList（acg-submission）', () => {
  it('个人版恢复新增入口，对话框展示自动成为注册人员提示', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'PERSONAL' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    const createBtn = wrapper.find('[data-testid="data-table-create-btn"]');
    expect(createBtn.exists()).toBe(true);
    expect((createBtn.element as HTMLButtonElement).disabled).toBe(false);

    await createBtn.trigger('click');
    await flushPromises();

    const hint = document.body.querySelector('[data-testid="create-registrar-hint"]');
    expect(hint).not.toBeNull();
    expect(hint!.textContent).toContain('创建后您将自动成为该机构的注册人员');
  });

  it('新建对话框暴露标准复合标识三段（国际代码标识符/组织标识符必填，组织部件标识符选填）', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'TEAM' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    await wrapper.find('[data-testid="data-table-create-btn"]').trigger('click');
    await flushPromises();

    const icd = document.body.querySelector('[data-testid="ra-icd-input"]');
    const orgPart = document.body.querySelector('[data-testid="ra-org-part-input"]');
    expect(icd).not.toBeNull();
    expect(orgPart).not.toBeNull();
    const dialogText = document.body.querySelector('.el-dialog')?.textContent ?? '';
    expect(dialogText).toContain('国际代码标识符');
    expect(dialogText).toContain('组织标识符');
    expect(dialogText).toContain('组织部件标识符');
  });

  it('新建对话框陈列组织属性（通信地址/电子邮箱/电话/网址）', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'TEAM' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    await wrapper.find('[data-testid="data-table-create-btn"]').trigger('click');
    await flushPromises();

    expect(document.body.querySelector('[data-testid="ra-mail-input"]')).not.toBeNull();
    expect(document.body.querySelector('[data-testid="ra-email-input"]')).not.toBeNull();
    expect(document.body.querySelector('[data-testid="ra-phone-input"]')).not.toBeNull();
    expect(document.body.querySelector('[data-testid="ra-web-input"]')).not.toBeNull();
    const dialogText = document.body.querySelector('.el-dialog')?.textContent ?? '';
    expect(dialogText).toContain('通信地址');
    expect(dialogText).toContain('电子邮箱');
    expect(dialogText).toContain('电话号码');
    expect(dialogText).toContain('网址');
  });

  it('团队版已有注册机构时仍显示新建入口（无运行模式数量限制）', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'TEAM' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    const createBtn = wrapper.find('[data-testid="data-table-create-btn"]');
    expect(createBtn.exists()).toBe(true);
  });

  it('删除：先取删除预览，确认对话框展示 6 项计数与不可恢复警告，确认后才删除', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'PERSONAL' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    const confirmSpy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(undefined as never);

    await wrapper.find('[data-testid="row-delete"]').trigger('click');
    await flushPromises();

    expect(mockedPreview).toHaveBeenCalledWith('ra-1');
    expect(confirmSpy).toHaveBeenCalled();
    const message = confirmSpy.mock.calls[0][0] as string;
    expect(message).toContain('受管项: 3');
    expect(message).toContain('提交记录: 2');
    expect(message).toContain('注册人员: 1');
    expect(message).toContain('命名空间: 4');
    expect(message).toContain('主管记录: 0');
    expect(message).toContain('流转规则: 5');
    expect(message).toContain('删除该注册机构将同时删除其下全部受管项及关联数据，且不可恢复');

    await flushPromises();
    expect(mockedDelete).toHaveBeenCalledWith('ra-1');
    confirmSpy.mockRestore();
  });

  it('m6：个人版 Primary 行（deletable=false）删除入口禁用并给出说明', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'PERSONAL' } as any);
    mockedList.mockResolvedValue(pageOf([raRow, raPrimaryRow]) as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    const buttons = wrapper.findAll('[data-testid="row-delete"]');
    expect(buttons).toHaveLength(2);
    expect((buttons[0].element as HTMLButtonElement).disabled).toBe(false);
    expect((buttons[1].element as HTMLButtonElement).disabled).toBe(true);

    const tooltip = wrapper
      .findAllComponents({ name: 'ElTooltip' })
      .find((t) => (t.props('content') as string | undefined)?.includes('主要注册机构不允许删除'));
    expect(tooltip).toBeTruthy();
  });

  it('删除：取消确认后不调用删除接口', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'PERSONAL' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    const confirmSpy = vi
      .spyOn(ElMessageBox, 'confirm')
      .mockRejectedValue(new Error('cancel') as never);

    await wrapper.find('[data-testid="row-delete"]').trigger('click');
    await flushPromises();

    expect(mockedPreview).toHaveBeenCalledWith('ra-1');
    expect(mockedDelete).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });
});
