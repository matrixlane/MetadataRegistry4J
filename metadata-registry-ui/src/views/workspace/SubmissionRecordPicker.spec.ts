import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import { createI18n } from 'vue-i18n';
import type { VueWrapper } from '@vue/test-utils';
import SubmissionRecordPicker from './SubmissionRecordPicker.vue';
import { myContactApi } from '@/api/resources';
import zhCN from '@/i18n/zh-CN.json';

function selectedContactValue(wrapper: VueWrapper): string | undefined {
  const select = wrapper.findComponent({ name: 'ElSelect' });
  return select.vm.modelValue;
}

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
    registrationAuthorityId: 'ra-1',
    registrationAuthorityName: 'RA',
    isDefault: true,
  },
  {
    id: 'contact-2',
    organizationId: 'org-2',
    organizationName: '组织 B',
    individualId: 'ind-2',
    individualName: '李四',
    roleId: 'role-2',
    roleName: '主管',
    isRegistrar: true,
    registrationAuthorityId: 'ra-1',
    registrationAuthorityName: 'RA',
    isDefault: false,
  },
] as any[]);

vi.mock('@/api/resources', () => ({
  myContactApi: {
    list: vi.fn(),
  },
}));

const mockedList = vi.mocked(myContactApi.list);

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
  mockedList.mockResolvedValue(contactsFixture);
});

afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount();
  document.body.innerHTML = '';
});

describe('SubmissionRecordPicker', () => {
  it('打开时加载联系人并默认选中默认联系人', async () => {
    const wrapper = mountPicker();
    mounted.push(wrapper);

    (wrapper.vm as any).open();
    await flushPromises();

    expect(mockedList).toHaveBeenCalled();
    expect(selectedContactValue(wrapper)).toBe('contact-1');
    expect((wrapper.find('[data-testid="submission-organization-display"]').element as HTMLInputElement).value).toBe('组织 A');
  });

  it('切换联系人后，组织显示随之更新', async () => {
    const wrapper = mountPicker();
    mounted.push(wrapper);

    (wrapper.vm as any).open();
    await flushPromises();

    const select = wrapper.findComponent({ name: 'ElSelect' });
    select.vm.$emit('update:modelValue', 'contact-2');
    select.vm.$emit('change', 'contact-2');
    await flushPromises();

    expect((wrapper.find('[data-testid="submission-organization-display"]').element as HTMLInputElement).value).toBe('组织 B');
  });

  it('未选择联系人时确认校验失败', async () => {
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

  it('选择有效联系人并确认后抛出 submission 信息', async () => {
    const wrapper = mountPicker();
    mounted.push(wrapper);

    (wrapper.vm as any).open();
    await flushPromises();

    await wrapper.find('[data-testid="submission-confirm"]').trigger('click');
    await flushPromises();

    expect(wrapper.emitted('confirm')?.[0]).toEqual([
      { contactId: 'contact-1', organizationId: 'org-1' },
    ]);
  });
});
