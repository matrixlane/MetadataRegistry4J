import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia } from 'pinia';
import ElementPlus, { ElMessage, ElMessageBox, ElSelect } from 'element-plus';
import MyProfile from './MyProfile.vue';
import type { MyContactResponse } from '@/types/common';

type ProfileVm = InstanceType<typeof MyProfile> & {
  contactForm: { organizationId: string; roleId?: string; kind: string };
  editingContact: MyContactResponse | null;
};

const authState = vi.hoisted(() => ({
  user: { id: 'user-1', username: 'test', displayName: 'Test User' } as Record<string, unknown>,
  edition: null as string | null,
  fetchMe: vi.fn(),
}));

const contactsState = vi.hoisted(() => ([
  {
    id: 'contact-1',
    organizationId: 'org-1',
    organizationName: 'Org One',
    individualId: 'ind-1',
    individualName: '张三',
    roleId: 'role-1',
    roleName: '管理员',
    isRegistrar: false,
    registrationAuthorityId: undefined,
    registrationAuthorityName: undefined,
    isDefault: true,
  },
  {
    id: 'contact-2',
    organizationId: 'org-2',
    organizationName: 'Org Two',
    individualId: 'ind-1',
    individualName: '张三',
    roleId: 'role-2',
    roleName: '提交人',
    isRegistrar: false,
    registrationAuthorityId: undefined,
    registrationAuthorityName: undefined,
    isDefault: false,
  },
]));

const myContactApi = vi.hoisted(() => ({
  list: vi.fn().mockResolvedValue(contactsState),
  create: vi.fn(),
  update: vi.fn(),
  delete: vi.fn(),
  setDefault: vi.fn(),
}));

const organizationApi = vi.hoisted(() => ({
  list: vi.fn().mockResolvedValue({
    content: [
      { id: 'org-1', name: 'Org One', identifier: 'org-one' },
      { id: 'org-2', name: 'Org Two', identifier: 'org-two' },
    ],
  }),
}));

const roleApi = vi.hoisted(() => ({
  options: vi.fn().mockResolvedValue([
    { id: 'role-1', roleName: '管理员' },
    { id: 'role-2', roleName: '提交人' },
  ]),
}));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => authState,
}));

vi.mock('@/api/resources', () => ({
  myContactApi,
  organizationApi,
  roleApi,
}));

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn().mockResolvedValue({}),
    put: vi.fn().mockResolvedValue({}),
  },
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

function mountProfile() {
  return mount(MyProfile, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus, createPinia()],
      mocks: { $t: (key: string) => key },
    },
  });
}

describe('MyProfile 联系人身份管理', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authState.user = { id: 'user-1', username: 'test', displayName: 'Test User' };
    authState.edition = null;
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('加载并展示联系人身份列表', async () => {
    const wrapper = mountProfile();
    await flushPromises();
    const items = wrapper.findAll('[data-testid="contact-identity-item"]');
    expect(items.length).toBe(2);
    expect(wrapper.findAll('[data-testid="contact-default-badge"]').length).toBe(1);
  });

  it('Registrar 身份显示编辑入口与删除入口（删除门控移至点击时按版本裁决）', async () => {
    myContactApi.list.mockResolvedValueOnce([
      {
        id: 'contact-3',
        organizationId: 'org-3',
        organizationName: 'Org Three',
        individualId: 'ind-1',
        individualName: '张三',
        roleId: null,
        roleName: null,
        isRegistrar: true,
        registrationAuthorityId: 'ra-1',
        registrationAuthorityName: 'RA One',
        isDefault: true,
      },
    ]);
    const wrapper = mountProfile();
    await flushPromises();
    const items = wrapper.findAll('[data-testid="contact-identity-item"]');
    expect(items.length).toBe(1);
    expect(wrapper.find('[data-testid="contact-edit-btn"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="contact-delete-btn"]').exists()).toBe(true);
  });

  it('编辑 Registrar 身份时组织选择器禁用（仅角色可改）', async () => {
    myContactApi.list.mockResolvedValueOnce([
      {
        id: 'contact-3',
        organizationId: 'org-3',
        organizationName: 'Org Three',
        individualId: 'ind-1',
        individualName: '张三',
        roleId: null,
        roleName: null,
        isRegistrar: true,
        registrationAuthorityId: 'ra-1',
        registrationAuthorityName: 'RA One',
        isDefault: true,
      },
    ]);
    const wrapper = mountProfile();
    await flushPromises();
    await wrapper.find('[data-testid="contact-edit-btn"]').trigger('click');
    await flushPromises();
    const selects = wrapper.findAllComponents(ElSelect);
    expect(selects[0].props('disabled')).toBe(true);
    expect(selects[1].props('disabled')).toBeFalsy();
  });

  it('点击「设为默认」调用 setDefault 并刷新列表', async () => {
    const wrapper = mountProfile();
    await flushPromises();
    const setDefaultBtns = wrapper.findAll('[data-testid="contact-set-default-btn"]');
    expect(setDefaultBtns.length).toBe(1);
    await setDefaultBtns[0].trigger('click');
    await flushPromises();
    expect(myContactApi.setDefault).toHaveBeenCalledWith('contact-2');
    expect(myContactApi.list).toHaveBeenCalledTimes(2);
  });

  it('打开新建弹窗并保存', async () => {
    const wrapper = mountProfile();
    await flushPromises();
    await wrapper.find('[data-testid="contact-add-btn"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="contact-dialog"]').exists()).toBe(true);

    const vm = wrapper.vm as unknown as ProfileVm;
    vm.contactForm.organizationId = 'org-2';
    vm.contactForm.roleId = 'role-2';
    await flushPromises();

    await wrapper.find('[data-testid="contact-save-btn"]').trigger('click');
    await flushPromises();

    expect(myContactApi.create).toHaveBeenCalledWith({ organizationId: 'org-2', roleId: 'role-2' });
    expect(myContactApi.list).toHaveBeenCalledTimes(2);
    expect(authState.fetchMe).toHaveBeenCalled();
  });

  it('打开编辑弹窗并保存', async () => {
    const wrapper = mountProfile();
    await flushPromises();
    const editBtns = wrapper.findAll('[data-testid="contact-edit-btn"]');
    expect(editBtns.length).toBe(2);
    await editBtns[0].trigger('click');
    await flushPromises();

    const vm = wrapper.vm as unknown as ProfileVm;
    expect(vm.editingContact?.id).toBe('contact-1');
    vm.contactForm.organizationId = 'org-2';
    await flushPromises();

    await wrapper.find('[data-testid="contact-save-btn"]').trigger('click');
    await flushPromises();

    expect(myContactApi.update).toHaveBeenCalledWith('contact-1', { organizationId: 'org-2', roleId: 'role-1' });
  });

  it('删除联系人身份：确认后调用 delete 并刷新列表', async () => {
    const confirmSpy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as unknown as Awaited<ReturnType<typeof ElMessageBox.confirm>>);
    const wrapper = mountProfile();
    await flushPromises();
    const deleteBtns = wrapper.findAll('[data-testid="contact-delete-btn"]');
    expect(deleteBtns.length).toBe(2);
    await deleteBtns[1].trigger('click');
    await flushPromises();
    expect(myContactApi.delete).toHaveBeenCalledWith('contact-2');
    expect(myContactApi.list).toHaveBeenCalledTimes(2);
    expect(authState.fetchMe).toHaveBeenCalled();
    confirmSpy.mockRestore();
    wrapper.unmount();
  });

  it('新建弹窗（非管理员）不显示注册人员选项', async () => {
    const wrapper = mountProfile();
    await flushPromises();
    await wrapper.find('[data-testid="contact-add-btn"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="contact-kind-radio"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="contact-kind-registrar"]').exists()).toBe(false);
  });

  it('新建弹窗（超级管理员）显示注册人员选项并可保存', async () => {
    authState.user = { id: 'user-1', username: 'test', displayName: 'Test User', sysRole: 'SUPER_ADMIN' };
    const wrapper = mountProfile();
    await flushPromises();
    await wrapper.find('[data-testid="contact-add-btn"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="contact-kind-registrar"]').exists()).toBe(true);

    const vm = wrapper.vm as unknown as ProfileVm;
    vm.contactForm.kind = 'REGISTRAR';
    vm.contactForm.organizationId = 'org-2';
    vm.contactForm.roleId = 'role-2';
    await flushPromises();

    await wrapper.find('[data-testid="contact-save-btn"]').trigger('click');
    await flushPromises();

    expect(myContactApi.create).toHaveBeenCalledWith({ organizationId: 'org-2', roleId: 'role-2', kind: 'REGISTRAR' });
    expect(myContactApi.list).toHaveBeenCalledTimes(2);
  });

  it('编辑弹窗中身份类型以文本展示（不可变更）', async () => {
    const wrapper = mountProfile();
    await flushPromises();
    const editBtns = wrapper.findAll('[data-testid="contact-edit-btn"]');
    await editBtns[0].trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-testid="contact-kind-text"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="contact-kind-radio"]').exists()).toBe(false);
  });

  it('个人版 Registrar 行点击删除仅提示，不弹确认、不调用 delete', async () => {
    authState.edition = 'PERSONAL';
    myContactApi.list.mockResolvedValueOnce([
      {
        id: 'contact-3',
        organizationId: 'org-3',
        organizationName: 'Org Three',
        individualId: 'ind-1',
        individualName: '张三',
        roleId: null,
        roleName: null,
        isRegistrar: true,
        registrationAuthorityId: 'ra-1',
        registrationAuthorityName: 'RA One',
        isDefault: true,
      },
    ]);
    const warningSpy = vi.spyOn(ElMessage, 'warning');
    const confirmSpy = vi.spyOn(ElMessageBox, 'confirm');
    const wrapper = mountProfile();
    await flushPromises();
    await wrapper.find('[data-testid="contact-delete-btn"]').trigger('click');
    await flushPromises();
    expect(warningSpy).toHaveBeenCalledWith('contactIdentity.registrarDeleteBlocked');
    expect(confirmSpy).not.toHaveBeenCalled();
    expect(myContactApi.delete).not.toHaveBeenCalled();
    warningSpy.mockRestore();
    confirmSpy.mockRestore();
  });

  it('团队版 Registrar 行可删除（现状按钮保留）', async () => {
    authState.edition = 'TEAM';
    myContactApi.list.mockResolvedValueOnce([
      {
        id: 'contact-3',
        organizationId: 'org-3',
        organizationName: 'Org Three',
        individualId: 'ind-1',
        individualName: '张三',
        roleId: null,
        roleName: null,
        isRegistrar: true,
        registrationAuthorityId: 'ra-1',
        registrationAuthorityName: 'RA One',
        isDefault: true,
      },
    ]);
    const confirmSpy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as unknown as Awaited<ReturnType<typeof ElMessageBox.confirm>>);
    const wrapper = mountProfile();
    await flushPromises();
    await wrapper.find('[data-testid="contact-delete-btn"]').trigger('click');
    await flushPromises();
    expect(myContactApi.delete).toHaveBeenCalledWith('contact-3');
    expect(myContactApi.list).toHaveBeenCalledTimes(2);
    confirmSpy.mockRestore();
  });
});
