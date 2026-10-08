import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import NamingConventionList from './NamingConventionList.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const { namingConventionApi } = vi.hoisted(() => ({
  namingConventionApi: {
    list: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
    get: vi.fn(),
    create: vi.fn().mockResolvedValue({}),
    update: vi.fn().mockResolvedValue({}),
    delete: vi.fn().mockResolvedValue({}),
  },
}));

vi.mock('@/api/resources', () => ({
  namingConventionApi,
  namespaceApi: { list: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }) },
  activeRegistrarApi: { current: vi.fn().mockResolvedValue({}) },
  readinessApi: { raNamespaceExists: vi.fn().mockResolvedValue({ exists: false }) },
  registrySpecificationApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

function mountComponent() {
  return mount(NamingConventionList, {
    global: {
      plugins: [ElementPlus],
      mocks: { $t: (key: string) => key },
    },
  });
}

async function openCreateDialog(wrapper: ReturnType<typeof mountComponent>) {
  wrapper.findComponent(ResourceFormDialog).vm.open({
    scopeRule: '',
    authorityRule: '',
    semanticRule: '',
    syntacticRule: '',
    lexicalRule: '',
    designations: [],
    definitions: [],
    registration: {},
  });
  await flushPromises();
}

describe('NamingConventionList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    namingConventionApi.list.mockResolvedValue({ content: [], totalElements: 0 });
  });

  it('新建对话框含「注册标识」「指称」「定义」「属性」页签', async () => {
    const wrapper = mountComponent();
    await flushPromises();
    await openCreateDialog(wrapper);

    const tabLabels = wrapper.findAll('.el-tabs__item').map((el) => el.text());
    expect(tabLabels).toContain('注册标识');
    expect(tabLabels).toContain('指称');
    expect(tabLabels).toContain('定义');
    expect(tabLabels).toContain('属性');
  });

  it('属性页签渲染五个规则属性输入项', async () => {
    const wrapper = mountComponent();
    await flushPromises();
    await openCreateDialog(wrapper);

    const inputs = wrapper.findAllComponents({ name: 'ElInput' });
    expect(inputs.filter((i) => i.find('input').exists()).length).toBeGreaterThanOrEqual(5);
  });

  it('提交 payload 含五个规则属性并携带 designations/definitions（可为空）', async () => {
    const wrapper = mountComponent();
    await flushPromises();
    await openCreateDialog(wrapper);

    const submitFn = wrapper.findComponent(ResourceFormDialog).props('submitFn') as (
      data: Record<string, unknown>,
    ) => Promise<unknown>;
    await submitFn({
      scopeRule: '^[a-z]+$',
      authorityRule: '由架构组审批',
      semanticRule: '名称应表达对象类与特性',
      syntacticRule: '限定词置于被限定部分之前',
      lexicalRule: '仅小写字母，camelCase 拼接',
      designations: [{ languageId: 'lang-1', value: '小写规则' }],
      definitions: [],
      registration: {},
    });

    expect(namingConventionApi.create).toHaveBeenCalledTimes(1);
    const payload = namingConventionApi.create.mock.calls[0][0] as Record<string, unknown>;
    expect(payload.scopeRule).toBe('^[a-z]+$');
    expect(payload.authorityRule).toBe('由架构组审批');
    expect(payload.semanticRule).toBe('名称应表达对象类与特性');
    expect(payload.syntacticRule).toBe('限定词置于被限定部分之前');
    expect(payload.lexicalRule).toBe('仅小写字母，camelCase 拼接');
    expect(payload.designations).toEqual([{ languageId: 'lang-1', value: '小写规则' }]);
    expect(payload.definitions).toEqual([]);
  });

  it('未填写指称/定义也可提交', async () => {
    const wrapper = mountComponent();
    await flushPromises();
    await openCreateDialog(wrapper);

    const submitFn = wrapper.findComponent(ResourceFormDialog).props('submitFn') as (
      data: Record<string, unknown>,
    ) => Promise<unknown>;
    await submitFn({
      scopeRule: '^[a-z]+$',
      designations: [],
      definitions: [],
      registration: {},
    });

    expect(namingConventionApi.create).toHaveBeenCalledTimes(1);
    const payload = namingConventionApi.create.mock.calls[0][0] as Record<string, unknown>;
    expect(payload.designations).toEqual([]);
    expect(payload.definitions).toEqual([]);
  });

  it('scopeRule 为空时前端拒绝提交且不调用 API', async () => {
    const wrapper = mountComponent();
    await flushPromises();
    await openCreateDialog(wrapper);

    const submitFn = wrapper.findComponent(ResourceFormDialog).props('submitFn') as (
      data: Record<string, unknown>,
    ) => Promise<unknown>;
    await expect(submitFn({ scopeRule: '  ', registration: {} })).rejects.toThrow();
    expect(namingConventionApi.create).not.toHaveBeenCalled();
  });
});
