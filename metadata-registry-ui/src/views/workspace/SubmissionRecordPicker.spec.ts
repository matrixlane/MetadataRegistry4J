import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useActiveRegistrar } from '@/composables/useActiveRegistrar';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { createI18n } from 'vue-i18n';
import type { VueWrapper } from '@vue/test-utils';
import SubmissionRecordPicker from './SubmissionRecordPicker.vue';
import { myContactApi, activeRegistrarApi } from '@/api/resources';
import zhCN from '@/i18n/zh-CN.json';

function selectedOrganizationValue(wrapper: VueWrapper): string | undefined {
  const select = wrapper.findComponent({ name: 'ElSelect' });
  return select.vm.modelValue;
}

/** 两个组织各一条身份：contact-1 为 org-1 默认联系人，contact-2 为 org-2 的 Registrar 身份 */
const contactsFixture = vi.hoisted(() => [
  {
    id: 'contact-1',
    organizationId: 'org-1',
    organizationName: '组织 A',
    individualId: 'ind-1',
    individualName: '张三',
    roleId: 'role-1',
    roleName: '提交人',
    isRegistrar: false,
    registrationAuthorityId: null,
    registrationAuthorityName: null,
    isDefault: true,
  },
  {
    id: 'contact-2',
    organizationId: 'org-2',
    organizationName: '组织 B',
    individualId: 'ind-2',
    individualName: '李四',
    roleId: 'role-2',
    roleName: '注册管理员',
    isRegistrar: true,
    registrationAuthorityId: 'ra-2',
    registrationAuthorityName: '机构 B',
    isDefault: false,
  },
] as any[]);

vi.mock('@/api/resources', () => ({
  myContactApi: {
    list: vi.fn(),
  },
  activeRegistrarApi: {
    current: vi.fn(),
  },
}));

const mockedList = vi.mocked(myContactApi.list);
const mockedActiveCurrent = vi.mocked(activeRegistrarApi.current);

function mountPicker() {
  const i18n = createI18n({
    legacy: false,
    locale: 'zh-CN',
    messages: { 'zh-CN': zhCN as any },
  });
  return mount(SubmissionRecordPicker, {
    attachTo: document.body,
    props: { title: '提交信息' },
    global: { plugins: [ElementPlus, i18n] },
  });
}

const mounted: VueWrapper[] = [];

beforeEach(() => {
  vi.clearAllMocks();
  // useActiveRegistrar 为模块级单例：每用例重置并清理浏览器端选择，隔离激活状态
  useActiveRegistrar().reset();
  localStorage.removeItem('activeRegistrarId');
  mockedList.mockResolvedValue(contactsFixture);
  // 默认无活动身份：默认选中走"第一条 Registrar 身份组织"回退
  mockedActiveCurrent.mockResolvedValue({ defaultRegistrarId: null, identities: [] });
});

afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount();
  document.body.innerHTML = '';
});

describe('SubmissionRecordPicker（acg-submission 组织下拉）', () => {
  it('打开时加载身份，组织选项为身份所属组织去重，默认选中活动 RA 身份组织', async () => {
    mockedActiveCurrent.mockResolvedValue({
      defaultRegistrarId: 'contact-2',
      identities: [
        { registrarId: 'contact-2', registrationAuthorityId: 'ra-2', registrationAuthorityName: '机构 B' },
      ],
    });
    const wrapper = mountPicker();
    mounted.push(wrapper);

    (wrapper.vm as any).open();
    await flushPromises();

    expect(mockedList).toHaveBeenCalled();
    expect(mockedActiveCurrent).toHaveBeenCalled();
    // 默认选中 = 活动 RA 身份所属组织（org-2）
    expect(selectedOrganizationValue(wrapper)).toBe('org-2');
    // 联系人为只读展示：该组织下本人身份的 individualName（organizationName）
    expect((wrapper.find('[data-testid="submission-contact-display"]').element as HTMLInputElement).value)
      .toBe('李四（组织 B）');
    expect(wrapper.find('[data-testid="submission-organization-select"]').exists()).toBe(true);
  });

  it('无 Registrar 身份时默认选中默认身份组织', async () => {
    mockedList.mockResolvedValue([contactsFixture[0]]);
    const wrapper = mountPicker();
    mounted.push(wrapper);

    (wrapper.vm as any).open();
    await flushPromises();

    expect(selectedOrganizationValue(wrapper)).toBe('org-1');
    expect((wrapper.find('[data-testid="submission-contact-display"]').element as HTMLInputElement).value)
      .toBe('张三（组织 A）');
  });

  it('M2：两条 Registrar 身份时默认选中活动 Registrar 身份所属组织（而非第一条）', async () => {
    mockedList.mockResolvedValue([
      {
        id: 'contact-ra-1',
        organizationId: 'org-1',
        organizationName: '组织 A',
        individualId: 'ind-1',
        individualName: '张三',
        roleId: 'role-1',
        roleName: '注册管理员',
        isRegistrar: true,
        registrationAuthorityId: 'ra-1',
        registrationAuthorityName: '机构 A',
        isDefault: true,
      },
      {
        id: 'contact-ra-2',
        organizationId: 'org-2',
        organizationName: '组织 B',
        individualId: 'ind-2',
        individualName: '李四',
        roleId: 'role-2',
        roleName: '注册管理员',
        isRegistrar: true,
        registrationAuthorityId: 'ra-2',
        registrationAuthorityName: '机构 B',
        isDefault: false,
      },
    ] as any[]);
    mockedActiveCurrent.mockResolvedValue({
      defaultRegistrarId: 'contact-ra-2',
      identities: [
        { registrarId: 'contact-ra-1', registrationAuthorityId: 'ra-1', registrationAuthorityName: '机构 A' },
        { registrarId: 'contact-ra-2', registrationAuthorityId: 'ra-2', registrationAuthorityName: '机构 B' },
      ],
    });
    const wrapper = mountPicker();
    mounted.push(wrapper);

    (wrapper.vm as any).open();
    await flushPromises();

    expect(mockedActiveCurrent).toHaveBeenCalled();
    expect(selectedOrganizationValue(wrapper)).toBe('org-2');
    expect((wrapper.find('[data-testid="submission-contact-display"]').element as HTMLInputElement).value)
      .toBe('李四（组织 B）');
  });

  it('minor-3：联系人只读框 placeholder 使用"随所选组织自动确定"语义', async () => {
    const wrapper = mountPicker();
    mounted.push(wrapper);

    (wrapper.vm as any).open();
    await flushPromises();

    expect((wrapper.find('[data-testid="submission-contact-display"]').element as HTMLInputElement).placeholder)
      .toBe('随所选组织自动确定');
  });

  it('切换组织后，只读联系人展示随之更新', async () => {
    const wrapper = mountPicker();
    mounted.push(wrapper);

    (wrapper.vm as any).open();
    await flushPromises();

    const select = wrapper.findComponent({ name: 'ElSelect' });
    select.vm.$emit('update:modelValue', 'org-1');
    select.vm.$emit('change', 'org-1');
    await flushPromises();

    expect((wrapper.find('[data-testid="submission-contact-display"]').element as HTMLInputElement).value)
      .toBe('张三（组织 A）');
  });

  it('身份列表为空时显示提示区，不显示表单与确认按钮', async () => {
    mockedList.mockResolvedValue([]);
    const wrapper = mountPicker();
    mounted.push(wrapper);

    (wrapper.vm as any).open();
    await flushPromises();

    expect(wrapper.find('[data-testid="submission-no-identity"]').exists()).toBe(true);
    expect(wrapper.text()).toContain('在提交元数据前，请先创建为当前注册机构的联系人');
    expect(wrapper.find('[data-testid="submission-organization-select"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="submission-confirm"]').exists()).toBe(false);
  });

  it('未选择组织时确认校验失败', async () => {
    const wrapper = mountPicker();
    mounted.push(wrapper);

    (wrapper.vm as any).open();
    await flushPromises();

    const select = wrapper.findComponent({ name: 'ElSelect' });
    select.vm.$emit('update:modelValue', '');
    select.vm.$emit('change', '');
    await flushPromises();

    await wrapper.find('[data-testid="submission-confirm"]').trigger('click');
    await flushPromises();

    expect(wrapper.emitted('confirm')).toBeUndefined();
  });

  it('close() 关闭弹窗（供父组件在提交成功后调用）', async () => {
    const wrapper = mountPicker();
    mounted.push(wrapper);

    (wrapper.vm as any).open();
    await flushPromises();
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(true);

    (wrapper.vm as any).close();
    await flushPromises();
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(false);
  });

  it('确认时抛出该组织下本人身份 id 与所选组织', async () => {
    const wrapper = mountPicker();
    mounted.push(wrapper);

    (wrapper.vm as any).open();
    await flushPromises();

    await wrapper.find('[data-testid="submission-confirm"]').trigger('click');
    await flushPromises();

    expect(wrapper.emitted('confirm')?.[0]).toEqual([
      { contactId: 'contact-2', organizationId: 'org-2' },
    ]);
  });

  it('切换组织后确认抛出对应组织下的身份', async () => {
    const wrapper = mountPicker();
    mounted.push(wrapper);

    (wrapper.vm as any).open();
    await flushPromises();

    const select = wrapper.findComponent({ name: 'ElSelect' });
    select.vm.$emit('update:modelValue', 'org-1');
    select.vm.$emit('change', 'org-1');
    await flushPromises();

    await wrapper.find('[data-testid="submission-confirm"]').trigger('click');
    await flushPromises();

    expect(wrapper.emitted('confirm')?.[0]).toEqual([
      { contactId: 'contact-1', organizationId: 'org-1' },
    ]);
  });
});
