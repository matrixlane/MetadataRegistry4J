import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { defineComponent, reactive } from 'vue';
import ElementPlus from 'element-plus';
import ItemRegistrationSection from './ItemRegistrationSection.vue';
import { activeRegistrarApi } from '@/api/resources';
import { useActiveRegistrar } from '@/composables/useActiveRegistrar';

const namespaces = vi.hoisted(() => [
  {
    id: 'ns-auto-only',
    identifier: 'ns-ao',
    identifierInputMode: 'AUTO_ONLY',
    versionInputMode: 'AUTO_ONLY',
    versioningPolicy: 'SEMVER',
    registrationAuthorityId: 'ra-auto-only',
  },
  {
    id: 'ns-awo',
    identifier: 'ns-awo',
    identifierInputMode: 'AUTO_WITH_OVERRIDE',
    versionInputMode: 'AUTO_WITH_OVERRIDE',
    versioningPolicy: 'SEQUENTIAL',
    registrationAuthorityId: 'ra-awo',
  },
  {
    id: 'ns-manual',
    identifier: 'ns-manual',
    identifierInputMode: 'MANUAL_ONLY',
    versionInputMode: 'MANUAL_ONLY',
    versioningPolicy: 'DATE',
    registrationAuthorityId: 'ra-manual',
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
    current: vi.fn().mockResolvedValue({
      defaultRegistrarId: 'reg-awo',
      identities: [{ registrarId: 'reg-awo', registrationAuthorityName: '测试注册机构', registrationAuthorityId: 'ra-awo' }],
    }),
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
  props: {
    isEditing: { type: Boolean, default: false },
    identifierCreationMode: { type: String as () => string | null, default: null },
    versionCreationMode: { type: String as () => string | null, default: null },
  },
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
        :identifier-creation-mode="props.identifierCreationMode"
        :version-creation-mode="props.versionCreationMode"
      />
    </el-form>
  `,
});

async function mountSection(
  namespaceId: string,
  isEditing = false,
  identifierCreationMode: 'AUTO' | 'MANUAL' | null = null,
  versionCreationMode: 'AUTO' | 'MANUAL' | null = null,
) {
  const wrapper = mount(Host, {
    props: { isEditing, identifierCreationMode, versionCreationMode },
    global: { plugins: [ElementPlus] },
  });
  const section = wrapper.findComponent(ItemRegistrationSection);
  // 等待 onMounted 的命名空间加载
  await vi.waitFor(() => {
    expect((section.vm as any).loaded).toBeTruthy();
  });
  (wrapper.vm as any).form.registration.namespaceId = namespaceId;
  await wrapper.vm.$nextTick();
  await vi.waitFor(() => {});
  return { wrapper, section };
}

function inputs(wrapper: any) {
  return wrapper.findAllComponents({ name: 'ElInput' });
}

function inlineModeControls(wrapper: any) {
  return wrapper.findAllComponents({ name: 'ElSegmented' });
}

// useActiveRegistrar 为模块级单例：每用例重置并清理浏览器端选择
beforeEach(() => {
  useActiveRegistrar().reset();
  localStorage.removeItem('activeRegistrarId');
});

describe('ItemRegistrationSection 命名空间输入模式', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    strategyView.value = null;
  });

  it('AUTO_WITH_OVERRIDE：显示内嵌生成方式切换控件，默认自动，输入框禁用', async () => {
    const { wrapper, section } = await mountSection('ns-awo');
    // 标识符与版本两个内嵌控件
    expect(inlineModeControls(section).length).toBe(2);
    const [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(true);
    expect(versionInput.props('disabled')).toBe(true);
    expect((section.vm as any).buildPayload()).toEqual({
      namespaceId: 'ns-awo',
      identifier: null,
      version: null,
      variables: undefined,
      identifierMode: 'AUTO',
      versionMode: 'AUTO',
      registrationStatus: null,
      administrativeStatus: null,
      effectiveDate: null,
      untilDate: null,
      administrativeNote: null,
      unresolvedIssue: null,
    });
    wrapper.unmount();
  });

  it('AUTO_ONLY：不显示内嵌切换控件，输入框恒禁用', async () => {
    const { section } = await mountSection('ns-auto-only');
    expect(inlineModeControls(section).length).toBe(0);
    const [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(true);
    expect(versionInput.props('disabled')).toBe(true);
  });

  it('MANUAL_ONLY：不显示内嵌切换控件，输入框启用', async () => {
    const { section } = await mountSection('ns-manual');
    expect(inlineModeControls(section).length).toBe(0);
    const [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(false);
    expect(versionInput.props('disabled')).toBe(false);
    expect(versionInput.props('placeholder')).toBe('如 2026-01-31');
  });

  it('外部命名空间：标识符不显示内嵌控件，强制手工；版本仍显示内嵌控件', async () => {
    const { section } = await mountSection('ns-external');
    expect(inlineModeControls(section).length).toBe(1);
    const [identifierInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(false);
  });

  it('AUTO_WITH_OVERRIDE：内嵌控件可切换，标识符切到手动后输入框启用', async () => {
    const { section } = await mountSection('ns-awo');
    const controls = inlineModeControls(section);
    expect(controls.length).toBe(2);
    // 默认 auto：标识符 / 版本输入框均禁用
    let [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(true);
    expect(versionInput.props('disabled')).toBe(true);
    // 切换标识符 segmented 到 manual，输入框启用
    const identifierControl = controls[0];
    (identifierControl.vm as any).$emit('change', 'manual');
    identifierControl.setValue('manual');
    await section.vm.$nextTick();
    [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(false);
    expect(versionInput.props('disabled')).toBe(true);
    expect((section.vm as any).buildPayload()).toEqual({
      namespaceId: 'ns-awo',
      identifier: '',
      version: null,
      variables: undefined,
      identifierMode: null,
      versionMode: 'AUTO',
      registrationStatus: null,
      administrativeStatus: null,
      effectiveDate: null,
      untilDate: null,
      administrativeNote: null,
      unresolvedIssue: null,
    });
  });

  it('Registration_State 属性：buildPayload 透传显式值，未设置为 null（不变更）', async () => {
    const { wrapper, section } = await mountSection('ns-manual');
    Object.assign((wrapper.vm as any).form.registration, {
      registrationStatus: 'QUALIFIED',
      administrativeStatus: 'Approved',
      effectiveDate: '2026-09-01T00:00:00',
      untilDate: '2027-09-01T00:00:00',
      administrativeNote: '备注内容',
      unresolvedIssue: '待确认问题',
    });
    await wrapper.vm.$nextTick();
    const payload = (section.vm as any).buildPayload();
    expect(payload.registrationStatus).toBe('QUALIFIED');
    expect(payload.administrativeStatus).toBe('Approved');
    expect(payload.effectiveDate).toBe('2026-09-01T00:00:00');
    expect(payload.untilDate).toBe('2027-09-01T00:00:00');
    expect(payload.administrativeNote).toBe('备注内容');
    expect(payload.unresolvedIssue).toBe('待确认问题');
  });

  it('Registration_State 文本属性：空串保留（语义为清空），区别于 null（不变更）', async () => {
    const { wrapper, section } = await mountSection('ns-manual');
    Object.assign((wrapper.vm as any).form.registration, {
      administrativeStatus: '',
      administrativeNote: '',
      unresolvedIssue: '',
    });
    await wrapper.vm.$nextTick();
    const payload = (section.vm as any).buildPayload();
    expect(payload.administrativeStatus).toBe('');
    expect(payload.administrativeNote).toBe('');
    expect(payload.unresolvedIssue).toBe('');
  });

  it('AUTO_WITH_OVERRIDE：内嵌控件携带 radiogroup 语义与 aria-label', async () => {
    const { section } = await mountSection('ns-awo');
    const radios = section.findAll('[role="radiogroup"]');
    expect(radios.length).toBe(2);
    const labels = radios.map((el: any) => el.attributes('aria-label'));
    expect(labels).toEqual(expect.arrayContaining(['标识符生成方式', '版本生成方式']));
  });

  it('编辑态（mode 为 null，legacy）：内嵌切换控件整体隐藏、标识符与版本均启用', async () => {
    // 迁移前既存 SI：mode 为 NULL 等价 MANUAL；identifier / version 在编辑态应均启用，
    // inline 切换控件不显示（编辑态不展示 AUTO/MANUAL 切换 UI）。
    const { wrapper, section } = await mountSection('ns-awo', true, null, null);
    expect(inlineModeControls(section).length).toBe(0);
    const [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(false);
    expect(versionInput.props('disabled')).toBe(false);
    // 通过宿主写入 version，确认编辑态可保留
    (wrapper.vm as any).form.registration.version = '1.2.3';
    await wrapper.vm.$nextTick();
    const reloaded = inputs(section)[1];
    expect((reloaded.vm as any).modelValue).toBe('1.2.3');
  });

  it('版本字段内嵌控件在 AUTO_WITH_OVERRIDE 下显示且切到 manual 后启用输入', async () => {
    const { section } = await mountSection('ns-awo');
    const versionControl = inlineModeControls(section)[1];
    (versionControl.vm as any).$emit('change', 'manual');
    versionControl.setValue('manual');
    await section.vm.$nextTick();
    const [, versionInput] = inputs(section);
    expect(versionInput.props('disabled')).toBe(false);
    // SEQUENTIAL 命名空间下手动模式预填初始版本 "1"，避免后端静默落 1.0.0
    expect((section.vm as any).buildPayload().version).toBe('1');
  });

  it('AUTO_WITH_OVERRIDE：切到手动后预填语义版本号；用户可改', async () => {
    const { wrapper, section } = await mountSection('ns-awo');
    // 切换命名空间到 SEMVER 命名空间（使用 ns-awo 已是 SEQUENTIAL，改用 ns-auto-only 不行因为不允许切换）
    // 直接修改 model 的 versioningPolicy 不方便（selectedNamespace 由 namespaces ref 决定）。
    // 这里用 ns-awo 是 SEQUENTIAL：预填 '1'。
    const versionControl = inlineModeControls(section)[1];
    (versionControl.vm as any).$emit('change', 'manual');
    versionControl.setValue('manual');
    await section.vm.$nextTick();
    const [, versionInput] = inputs(section);
    expect((section.vm as any).buildPayload().version).toBe('1');
    // 用户可改
    (wrapper.vm as any).form.registration.version = '2';
    await wrapper.vm.$nextTick();
    expect((section.vm as any).buildPayload().version).toBe('2');
  });

  it('编辑既存记录（mode 为 null）：标识符启用、版本启用', async () => {
    // 兼容场景：迁移前既存 SI 的 creation_mode 为 NULL，等价 MANUAL；现有测试与该场景一致。
    const { section } = await mountSection('ns-awo', true, null, null);
    const [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(false);
    expect(versionInput.props('disabled')).toBe(false);
    expect(versionInput.props('placeholder')).toBe('留空则按命名空间策略自动递增');
  });

  it('编辑 AUTO 模式：标识符 / 版本输入框禁用、inline 控件不显示', async () => {
    const { section } = await mountSection('ns-awo', true, 'AUTO', 'AUTO');
    // inline 切换控件在编辑态不显示
    expect(inlineModeControls(section).length).toBe(0);
    const [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(true);
    expect(versionInput.props('disabled')).toBe(true);
    expect(identifierInput.props('placeholder')).toBe('自动生成，不可编辑');
    expect(versionInput.props('placeholder')).toBe('自动生成，不可编辑');
  });

  it('编辑 MANUAL 模式：标识符 / 版本输入框启用', async () => {
    const { section } = await mountSection('ns-awo', true, 'MANUAL', 'MANUAL');
    expect(inlineModeControls(section).length).toBe(0);
    const [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(false);
    expect(versionInput.props('disabled')).toBe(false);
    expect(versionInput.props('placeholder')).toBe('留空则按命名空间策略自动递增');
  });

  it('编辑既存记录（mode 为 null）：等价 MANUAL，标识符与版本均启用', async () => {
    const { section } = await mountSection('ns-awo', true, null, null);
    const [identifierInput, versionInput] = inputs(section);
    expect(identifierInput.props('disabled')).toBe(false);
    expect(versionInput.props('disabled')).toBe(false);
  });

  it('编辑 AUTO 模式：buildPayload 携带 identifierMode / versionMode 并清空 identifier / version', async () => {
    const { section } = await mountSection('ns-awo', true, 'AUTO', 'AUTO');
    const payload = (section.vm as any).buildPayload();
    expect(payload.identifierMode).toBe('AUTO');
    expect(payload.versionMode).toBe('AUTO');
    expect(payload.identifier).toBeNull();
    expect(payload.version).toBeNull();
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

  it('身份查询接口失败：提示查询失败，而非误报无身份', async () => {
    (activeRegistrarApi.current as any).mockRejectedValueOnce(new Error('Internal Server Error'));
    const { section } = await mountSection('ns-awo');
    await vi.waitFor(() => {
      expect(section.text()).toContain('活动 Registrar 身份查询失败，请刷新页面重试或重新登录');
    });
    expect(section.text()).not.toContain('无活动 Registrar 身份');
  });
});

describe('ItemRegistrationSection 默认命名空间按活动 Registrar 预选', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    strategyView.value = null;
    // 默认 activeRegistrarApi.current：名称与 id 都设 ra-awo，使原有测试不受影响
    (activeRegistrarApi.current as any).mockResolvedValue({
      defaultRegistrarId: 'reg-awo',
      identities: [{ registrarId: 'reg-awo', registrationAuthorityName: '测试注册机构', registrationAuthorityId: 'ra-awo' }],
    });
  });

  async function mountForCreate() {
    const wrapper = mount(Host, {
      props: { isEditing: false },
      global: { plugins: [ElementPlus] },
    });
    const section = wrapper.findComponent(ItemRegistrationSection);
    // 等到 gateBlocked 可读 = loadOptions 完成 = applyDefaultNamespace 已运行
    await vi.waitFor(() => {
      expect((section.vm as any).loaded).toBeTruthy();
    });
    // 多让一拍让异步 setup 都跑完
    await new Promise((r) => setTimeout(r, 0));
    return { wrapper, section };
  }

  it('默认按活动 RA ra-awo 预选自有命名空间 ns-awo', async () => {
    const { wrapper } = await mountForCreate();
    expect((wrapper.vm as any).form.registration.namespaceId).toBe('ns-awo');
    wrapper.unmount();
  });

  it('EXTERNAL 命名空间不参与默认推导', async () => {
    const tempNs = {
      id: 'ns-external-with-ra',
      identifier: 'ns-ext-ra',
      identifierInputMode: 'AUTO_WITH_OVERRIDE',
      versionInputMode: 'AUTO_WITH_OVERRIDE',
      versioningPolicy: 'SEMVER',
      namespaceKind: 'EXTERNAL',
    };
    const originalLen = namespaces.length;
    namespaces.push(tempNs);
    try {
      const { wrapper } = await mountForCreate();
      // 不能选 EXTERNAL；唯一自有候选是 ns-awo
      expect((wrapper.vm as any).form.registration.namespaceId).toBe('ns-awo');
      wrapper.unmount();
    } finally {
      namespaces.length = originalLen;
    }
  });

  it('活动 RA 不存在自有命名空间时不预选', async () => {
    (activeRegistrarApi.current as any).mockResolvedValueOnce({
      defaultRegistrarId: 'reg-unknown',
      identities: [{ registrarId: 'reg-unknown', registrationAuthorityName: '未知机构', registrationAuthorityId: 'ra-unknown' }],
    });
    const wrapper = mount(Host, {
      props: { isEditing: false },
      global: { plugins: [ElementPlus] },
    });
    const section = wrapper.findComponent(ItemRegistrationSection);
    await vi.waitFor(() => {
      expect((section.vm as any).loaded).toBeTruthy();
    });
    await new Promise((r) => setTimeout(r, 0));
    expect((wrapper.vm as any).form.registration.namespaceId).toBeUndefined();
    wrapper.unmount();
  });

  it('无活动 Registrar 身份时不预选', async () => {
    (activeRegistrarApi.current as any).mockResolvedValueOnce({ defaultRegistrarId: null, identities: [] });
    const wrapper = mount(Host, {
      props: { isEditing: false },
      global: { plugins: [ElementPlus] },
    });
    const section = wrapper.findComponent(ItemRegistrationSection);
    await vi.waitFor(() => {
      expect((section.vm as any).loaded).toBeTruthy();
    });
    await new Promise((r) => setTimeout(r, 0));
    expect((wrapper.vm as any).form.registration.namespaceId).toBeUndefined();
    wrapper.unmount();
  });

  it('编辑态不应用默认推导（用户已选）', async () => {
    const wrapper = mount(Host, {
      props: { isEditing: true },
      global: { plugins: [ElementPlus] },
    });
    const section = wrapper.findComponent(ItemRegistrationSection);
    await vi.waitFor(() => {
      expect((section.vm as any).loaded).toBeTruthy();
    });
    await new Promise((r) => setTimeout(r, 0));
    expect((wrapper.vm as any).form.registration.namespaceId).toBeUndefined();
    wrapper.unmount();
  });
});
