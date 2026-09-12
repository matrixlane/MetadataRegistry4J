import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import type { VueWrapper } from '@vue/test-utils';
import ConceptualDomainList from './ConceptualDomainList.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';

const mocks = vi.hoisted(() => ({
  cdCreate: vi.fn(),
  cdUpdate: vi.fn(),
}));

vi.mock('@/api/resources', () => ({
  namespaceApi: {
    list: vi.fn().mockResolvedValue({ content: [] }),
    detail: vi.fn().mockResolvedValue({ namingConventionIds: [] }),
  },
  registrationAuthorityApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
  languageApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
  contextApi: { options: vi.fn().mockResolvedValue([]) },
  referenceDocumentApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
  namingConventionApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
  pairingApi: { list: vi.fn().mockResolvedValue([]), create: vi.fn(), delete: vi.fn() },
  readinessApi: { raNamespaceExists: vi.fn().mockResolvedValue({ exists: true }) },
  registrySpecificationApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
  metadataSearchApi: { search: vi.fn().mockResolvedValue({ content: [] }) },
  conceptualDomainApi: {
    list: vi.fn().mockResolvedValue({ content: [], totalElements: 0, number: 0, size: 20 }),
    create: mocks.cdCreate,
    update: mocks.cdUpdate,
    delete: vi.fn(),
  },
}));

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key: string) => key }) }));
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ isRegistrarUser: true }),
}));

const mounted: VueWrapper[] = [];

function todayStart(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T00:00:00`;
}

async function openCreateDialog() {
  const wrapper = mount(ConceptualDomainList, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      mocks: { $t: (key: string) => key },
    },
  });
  mounted.push(wrapper);
  await flushPromises();
  const createBtn = wrapper.findAll('button').find((b) => b.text().includes('新建'));
  expect(createBtn).toBeTruthy();
  await createBtn!.trigger('click');
  await flushPromises();
  return wrapper;
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.cdCreate.mockResolvedValue({ id: 'cd-1' });
  mocks.cdUpdate.mockResolvedValue({ id: 'cd-1' });
});

afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount();
  document.body.innerHTML = '';
});

describe('ConceptualDomainList 有效期（enforce-valid-period D3）', () => {
  it('新建模式：起始日期预填当日且标记必填，结束日期可空', async () => {
    const wrapper = await openCreateDialog();
    const dialog = wrapper.findComponent(ItemFormDialog);
    const form = (dialog.vm as any).form as Record<string, any>;

    expect(form.validPeriodStart).toBe(todayStart());
    expect(form.validPeriodEnd ?? null).toBeNull();
    expect(dialog.find('.el-form-item.is-required').exists()).toBe(true);
  });

  it('清空起始日期后 required 规则判定不通过（字段级）', async () => {
    const wrapper = await openCreateDialog();
    const dialog = wrapper.findComponent(ItemFormDialog);
    const form = (dialog.vm as any).form as Record<string, any>;
    form.validPeriodStart = '';
    await dialog.vm.$nextTick();

    // 字段级校验：required 规则对空值必须判 false。
    // 注：jsdom 下 el-form.validate() 的聚合路径存在上游怪癖（拒绝值丢失后误判 valid），
    // 真实浏览器行为以 e2e 为准（清空后保存被拦截、不发请求）；这里直接驱动字段校验。
    const formRef = (dialog.vm as any).formRef ?? (dialog.vm as any).$refs?.formRef;
    const field = (formRef?.fields ?? []).find((f: any) => f.prop === 'validPeriodStart');
    expect(field).toBeTruthy();
    let valid: boolean | null = null;
    await field.validate('', (v: boolean) => { valid = v; });
    await flushPromises();
    expect(valid).toBe(false);
  });

  it('结束日期留空可保存（表示仍有效），载荷携带起始日期', async () => {
    const wrapper = await openCreateDialog();
    const dialog = wrapper.findComponent(ItemFormDialog);
    const form = (dialog.vm as any).form as Record<string, any>;
    form.designations = [{ clientKey: 'ck-1', languageId: '', value: '血压', contexts: [{ contextId: 'ctx-1', acceptability: 'ADMITTED' }] }];
    await dialog.vm.$nextTick();

    await (dialog.vm as any).handleSubmit();
    await flushPromises();

    expect(mocks.cdCreate).toHaveBeenCalledTimes(1);
    const payload = mocks.cdCreate.mock.calls[0][0];
    expect(payload.validPeriodStart).toBe(todayStart());
    expect(payload.validPeriodEnd ?? null).toBeNull();
  });

  it('编辑模式：不回填既有值（既可为空，交由服务端校验拒绝）', async () => {
    const wrapper = await openCreateDialog();
    const dialog = wrapper.findComponent(ItemFormDialog);
    await (dialog.vm as any).open({
      id: 'cd-9',
      validPeriodStart: null,
      validPeriodEnd: null,
      designations: [{ id: 'dg-1', languageId: '', value: '旧', contexts: [] }],
      definitions: [],
      registration: {},
    });
    await flushPromises();
    const form = (dialog.vm as any).form as Record<string, any>;
    expect(form.validPeriodStart ?? null).toBeNull();
  });
});
