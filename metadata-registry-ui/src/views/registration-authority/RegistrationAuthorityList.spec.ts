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
    mockedStatusGet.mockResolvedValue({ edition: 'PERSONAL', runningMode: 'SINGLE' } as any);
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

  it('团队版 SINGLE 且已有注册机构时隐藏新建入口', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'TEAM', runningMode: 'SINGLE' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    const createBtn = wrapper.find('[data-testid="data-table-create-btn"]');
    expect(createBtn.exists()).toBe(false);
  });

  it('删除：先取删除预览，确认对话框展示计数与不可恢复警告，确认后才删除', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'PERSONAL', runningMode: 'SINGLE' } as any);
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
    expect(message).toContain('注册人员: 1');
    expect(message).toContain('命名空间: 4');
    expect(message).toContain('提交记录: 2');
    expect(message).toContain('不可恢复');

    await flushPromises();
    expect(mockedDelete).toHaveBeenCalledWith('ra-1');
    confirmSpy.mockRestore();
  });

  it('删除：取消确认后不调用删除接口', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'PERSONAL', runningMode: 'SINGLE' } as any);
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
