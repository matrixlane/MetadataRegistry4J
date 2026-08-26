import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { defineComponent, reactive } from 'vue';
import ElementPlus from 'element-plus';
import ItemRegistrationSection from './ItemRegistrationSection.vue';
import { activeRegistrarApi } from '@/api/resources';

const namespaces = vi.hoisted(() => [
  {
    id: 'ns-auto-only',
    identifier: 'ns-ao',
    identifierInputMode: 'AUTO_ONLY',
    versionInputMode: 'AUTO_ONLY',
    versioningPolicy: 'SEMVER',
  },
  {
    id: 'ns-awo',
    identifier: 'ns-awo',
    identifierInputMode: 'AUTO_WITH_OVERRIDE',
    versionInputMode: 'AUTO_WITH_OVERRIDE',
    versioningPolicy: 'SEQUENTIAL',
  },
  {
    id: 'ns-manual',
    identifier: 'ns-manual',
    identifierInputMode: 'MANUAL_ONLY',
    versionInputMode: 'MANUAL_ONLY',
    versioningPolicy: 'DATE',
  },
  {
    id: 'ns-external',
    identifier: 'ns-ext',
    identifierInputMode: 'AUTO_WITH_OVERRIDE',
    versionInputMode: 'AUTO_WITH_OVERRIDE',
    versioningPolicy: 'SEMVER',
    namespaceKind: 'EXTERNAL',
  },
]);

const strategyView = vi.hoisted(() => ({
  value: null as any,
}));

vi.mock('@/api/resources', () => ({
  namespaceApi: {
    list: vi.fn().mockImplementation(() => Promise.resolve({ content: namespaces })),
    detail: vi.fn().mockResolvedValue({ namingConventionIds: [] }),
  },
  activeRegistrarApi: {
    current: vi.fn().mockResolvedValue({ active: { registrationAuthorityName: '测试注册机构' } }),
  },
  readinessApi: { raNamespaceExists: vi.fn().mockResolvedValue({ exists: true }) },
}));

vi.mock('@/api/identifierStrategy', () => ({
  identifierStrategyApi: {
    view: vi.fn().mockImplementation(() => Promise.resolve(strategyView.value)),
  },
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

const Host = defineComponent({
  components: { ItemRegistrationSection },
  props: ['isEditing'],
  setup(props) {
    const form = reactive({ registration: {} as Record<string, any> });
    return { form, props };
  },
  template: `
    <el-form :model="form">
      <ItemRegistrationSection
        ref="section"
        v-model="form.registration"
        :is-editing="props.isEditing"
      />
    </el-form>
  `,
});

async function mountSection(namespaceId: string, isEditing = false) {
  const wrapper = mount(Host, {
    props: { isEditing },
    global: { plugins: [ElementPlus] },
  });
  const section = wrapper.findComponent(ItemRegistrationSection);
  // 等待 onMounted 的命名空间加载
  await vi.waitFor(() => {
    expect((section.vm as any).gateBlocked).toBeDefined();
  });
  (wrapper.vm as any).form.registration.namespaceId = namespaceId;
  await wrapper.vm.$nextTick();
  await vi.waitFor(() => {});
  return { wrapper, section };
}

function inputs(wrapper: any) {
  return wrapper.findAllComponents({ name: 'ElInput' });
}

function radioButtons(wrapper: any) {
  return wrapper.findAllComponents({ name: 'ElRadioButton' });
}

describe('ItemRegistrationSection 命名空间输入模式', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    strategyView.value = null;
  });

  it('AUTO_WITH_OVERRIDE：显示生成方式单选，默认自动，输入框禁用', async () => {
    const { wrapper, section } = await mountSection('ns-awo');
    expect(radioButtons(section).length).toBe(4);
    const [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(true);
    expect(versionInput.props('disabled')).toBe(true);
    expect((section.vm as any).buildPayload()).toEqual({
      namespaceId: 'ns-awo',
      identifier: null,
      version: null,
      variables: undefined,
    });
    wrapper.unmount();
  });

  it('AUTO_ONLY：不显示单选，输入框恒禁用', async () => {
    const { section } = await mountSection('ns-auto-only');
    expect(radioButtons(section).length).toBe(0);
    const [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(true);
    expect(versionInput.props('disabled')).toBe(true);
  });

  it('MANUAL_ONLY：不显示单选，输入框启用', async () => {
    const { section } = await mountSection('ns-manual');
    expect(radioButtons(section).length).toBe(0);
    const [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(false);
    expect(versionInput.props('disabled')).toBe(false);
    expect(versionInput.props('placeholder')).toBe('如 2026-01-31');
  });

  it('外部命名空间：无自动生成入口，标识符强制手工', async () => {
    const { section } = await mountSection('ns-external');
    // 版本单选仍显示，标识符单选隐藏
    expect(radioButtons(section).length).toBe(2);
    const [identifierInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(false);
  });

  it('编辑态：标识符禁用，版本可编辑（留空则按策略递增）', async () => {
    const { section } = await mountSection('ns-awo', true);
    const [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(true);
    expect(versionInput.props('disabled')).toBe(false);
    expect(versionInput.props('placeholder')).toBe('留空则按命名空间策略自动递增');
  });

  it('段模板：自动模式下渲染变量输入并随负载提交', async () => {
    strategyView.value = {
      published: {
        strategyType: 'SEGMENT_TEMPLATE',
        configJson: JSON.stringify({
          segments: [
            { type: 'LITERAL' },
            {
              type: 'VARIABLE',
              name: 'dept',
              params: { label: '科室代码', pattern: '\\d{2}', default: '00', required: true },
            },
          ],
        }),
      },
    };
    const { wrapper, section } = await mountSection('ns-awo');
    await vi.waitFor(() => {
      expect((section.vm as any).buildPayload().variables).toBeDefined();
    });
    const payload = (section.vm as any).buildPayload();
    expect(payload.variables).toEqual({ dept: '00' });
    expect((section.vm as any).validateTemplateVariables()).toBeNull();
    // 清空必填变量后校验失败
    (wrapper.vm as any).form.registration.namespaceId = 'ns-awo';
    const varsInput = inputs(section).find((i: any) => i.props('placeholder') === '格式：\\d{2}');
    expect(varsInput).toBeDefined();
    await varsInput!.setValue('');
    expect((section.vm as any).validateTemplateVariables()).toBe('请填写科室代码');
  });
});

describe('ItemRegistrationSection 注册机构归属提示', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    strategyView.value = null;
  });

  it('有活动 Registrar 身份：显示注册机构名称标签', async () => {
    const { section } = await mountSection('ns-awo');
    await vi.waitFor(() => {
      expect(section.text()).toContain('测试注册机构');
    });
    expect(section.text()).not.toContain('无活动 Registrar 身份');
    expect(section.text()).not.toContain('查询失败');
  });

  it('无活动 Registrar 身份：提示无法注册条目', async () => {
    (activeRegistrarApi.current as any).mockResolvedValueOnce({ active: null, identities: [] });
    const { section } = await mountSection('ns-awo');
    await vi.waitFor(() => {
      expect(section.text()).toContain('当前账号无活动 Registrar 身份，无法注册条目');
    });
    expect(section.text()).not.toContain('查询失败');
  });

  it('身份查询接口失败：提示查询失败，而非误报无身份', async () => {
    (activeRegistrarApi.current as any).mockRejectedValueOnce(new Error('Internal Server Error'));
    const { section } = await mountSection('ns-awo');
    await vi.waitFor(() => {
      expect(section.text()).toContain('活动 Registrar 身份查询失败，请刷新页面重试或重新登录');
    });
    expect(section.text()).not.toContain('无活动 Registrar 身份');
  });
});
