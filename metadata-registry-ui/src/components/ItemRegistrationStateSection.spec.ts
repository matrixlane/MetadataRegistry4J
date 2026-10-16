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
    // 新建模式不渲染只读状态区
    expect(wrapper.find('[data-test="readonly-registration-status"]').exists()).toBe(false);
  });

  it('编辑模式：注册状态只读展示当前状态，不渲染下拉', () => {
    const wrapper = mountSection(true, {
      registrationStatus: 'RECORDED',
      effectiveDate: '2026-09-18T10:00:00',
    });
    expect(wrapper.findComponent({ name: 'ElSelect' }).exists()).toBe(false);

    const tag = wrapper.find('[data-test="readonly-status-tag"]');
    expect(tag.exists()).toBe(true);
    expect(tag.text()).toContain('已记录');
    expect(wrapper.find('[data-test="readonly-effective-date"]').exists()).toBe(false);

    // 不附带"状态推进请在注册项列表的推进流程中执行"提示：
    // 该提示对经基准导入的条目不成立（它们本就由导入脚本直接落定注册状态，
    // 不经推进流程取得），且对无受管实例的历史条目会指向一条走不通的操作路径。
    expect(wrapper.find('[data-test="readonly-status-hint"]').exists()).toBe(false);
  });

  it('编辑模式：无生效日期时只展示状态', () => {
    const wrapper = mountSection(true, { registrationStatus: 'QUALIFIED' });
    expect(wrapper.find('[data-test="readonly-status-tag"]').text()).toContain('已合格');
    expect(wrapper.find('[data-test="readonly-effective-date"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('字段标签旁展示解释性图标，悬浮提示为 ISO/IEC 11179-3 属性说明', () => {
    const wrapper = mountSection(true, { registrationStatus: 'RECORDED' });
    const tooltips = wrapper
      .findAllComponents({ name: 'ElTooltip' })
      .filter((tp) => typeof tp.props('content') === 'string' && tp.props('content'));
    expect(tooltips.length).toBe(7);
    const contents = tooltips.map((tp) => tp.props('content') as string);
    expect(contents).toContain('以该注册状态注册的受管理项在注册生命周期中所处状态的指称');
    expect(contents).toContain('负责该受管理项的注册机构在处理注册请求的行政审批流程中，对该受管理项所处状态的指称');
    expect(contents).toContain('关于该受管理项的描述性注释');
    wrapper.unmount();
  });
});
