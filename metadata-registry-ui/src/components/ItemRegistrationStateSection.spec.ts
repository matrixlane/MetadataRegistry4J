import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import { defineComponent, reactive } from 'vue';
import ElementPlus from 'element-plus';
import { createI18n } from 'vue-i18n';
import ItemRegistrationStateSection from './ItemRegistrationStateSection.vue';
import zhCN from '@/i18n/zh-CN.json';

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  messages: { 'zh-CN': zhCN as any },
});

function mountSection(isEditing: boolean, registration: Record<string, any>) {
  const Host = defineComponent({
    components: { ItemRegistrationStateSection },
    setup() {
      const form = reactive({ registration });
      return { form, isEditing };
    },
    template: `
      <el-form :model="form">
        <ItemRegistrationStateSection v-model="form.registration" :is-editing="isEditing" />
      </el-form>
    `,
  });
  return mount(Host, {
    global: { plugins: [ElementPlus, i18n] },
    attachTo: document.body,
  });
}

describe('ItemRegistrationStateSection', () => {
  it('新建模式：注册状态下拉仅含三种合法初始状态（不完整/历史/应用）', () => {
    const wrapper = mountSection(false, {});
    const select = wrapper.findComponent({ name: 'ElSelect' });
    expect(select.exists()).toBe(true);
    const options = wrapper.findAllComponents({ name: 'ElOption' });
    const labels = options.map((o) => o.props('label'));
    expect(labels).toEqual(['不完整', '历史', '应用']);
    // 新建模式无只读提示
    expect(wrapper.find('[data-test="readonly-status-hint"]').exists()).toBe(false);
  });

  it('编辑模式：注册状态只读展示当前状态与生效日期，不渲染下拉', () => {
    const wrapper = mountSection(true, {
      registrationStatus: 'RECORDED',
      effectiveDate: '2026-09-18T10:00:00',
    });
    expect(wrapper.findComponent({ name: 'ElSelect' }).exists()).toBe(false);

    const tag = wrapper.find('[data-test="readonly-status-tag"]');
    expect(tag.exists()).toBe(true);
    expect(tag.text()).toContain('已记录');
    expect(wrapper.find('[data-test="readonly-effective-date"]').text()).toContain('2026-09-18T10:00:00');

    // 附提示：状态推进请在注册项列表的推进流程中执行
    const hint = wrapper.find('[data-test="readonly-status-hint"]');
    expect(hint.exists()).toBe(true);
    expect(hint.text()).toContain('推进流程');
  });

  it('编辑模式：无生效日期时只展示状态', () => {
    const wrapper = mountSection(true, { registrationStatus: 'QUALIFIED' });
    expect(wrapper.find('[data-test="readonly-status-tag"]').text()).toContain('已合格');
    expect(wrapper.find('[data-test="readonly-effective-date"]').exists()).toBe(false);
  });
});
