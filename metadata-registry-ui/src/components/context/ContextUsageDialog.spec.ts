import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import ContextUsageDialog from './ContextUsageDialog.vue';

const push = vi.fn();
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
}));

vi.mock('@/api/resources', () => ({
  contextApi: {
    usages: vi.fn().mockResolvedValue({
      truncated: true,
      groups: [
        {
          itemType: 'DataElement',
          itemTypeLabel: '数据元',
          items: [
            {
              itemId: 'item-1',
              itemLabel: '患者姓名',
              associations: [
                { associationType: 'DESIGNATION', designationValue: '患者姓名', definitionValue: null, acceptability: 'PREFERRED' },
                { associationType: 'DEFINITION', designationValue: null, definitionValue: '患者姓名的定义', acceptability: 'ADMITTED' },
              ],
            },
          ],
        },
      ],
    }),
  },
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: any) => (params ? `${key} ${JSON.stringify(params)}` : key),
  }),
}));

describe('ContextUsageDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  function mountComponent() {
    return mount(ContextUsageDialog, {
      props: { contextLabel: '默认上下文' },
      global: {
        plugins: [ElementPlus],
        mocks: { $t: (key: string) => key },
      },
    });
  }

  it('opens dialog and loads usages', async () => {
    const wrapper = mountComponent();
    const exposed = wrapper.vm as any;
    await exposed.open('ctx-1');
    expect(wrapper.find('.usage-group').exists()).toBe(true);
    expect(wrapper.find('.group-title').text()).toContain('数据元');
  });

  it('renders truncated warning', async () => {
    const wrapper = mountComponent();
    const exposed = wrapper.vm as any;
    await exposed.open('ctx-1');
    expect(wrapper.find('.el-alert').exists()).toBe(true);
    expect(wrapper.find('.el-alert').text()).toContain('100');
  });

  it('navigates to item on link click', async () => {
    const wrapper = mountComponent();
    const exposed = wrapper.vm as any;
    await exposed.open('ctx-1');
    await flushPromises();
    const link = wrapper.find('.el-link');
    expect(link.exists()).toBe(true);
    await link.trigger('click');
    expect(push).toHaveBeenCalledWith({ name: 'MetadataDetail', params: { id: 'item-1' } });
  });
});
