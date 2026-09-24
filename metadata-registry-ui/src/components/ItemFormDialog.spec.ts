import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import ItemFormDialog from './ItemFormDialog.vue';
import { myContactApi } from '@/api/resources';
import type { MyContactResponse, SubmissionInfo } from '@/types/common';

type DialogVm = InstanceType<typeof ItemFormDialog> & {
  contacts: MyContactResponse[];
  form: { registration: { submission: SubmissionInfo; stewardship: { contactId: string | null; organizationId: string | null } } };
  onContactChange: (id: string) => void;
  onStewardshipContactChange: (id: string) => void;
  handleSubmit: () => Promise<void>;
  activeTab: string;
  activeSubTab: string;
  activateNamingTab: (sub: 'designations' | 'definitions' | 'pairings') => void;
};

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
]);

const mockContacts = vi.hoisted(() => ([
  {
    id: 'contact-1',
    organizationId: 'org-1',
    organizationName: 'Org One',
    individualName: '张三',
    roleName: '管理员',
    isRegistrar: false,
    isDefault: true,
  },
  {
    id: 'contact-2',
    organizationId: 'org-2',
    organizationName: 'Org Two',
    individualName: '李四',
    roleName: '提交人',
    isRegistrar: false,
    isDefault: false,
  },
]));

vi.mock('@/api/resources', () => ({
  namespaceApi: {
    list: vi.fn().mockImplementation(() => Promise.resolve({ content: namespaces })),
    detail: vi.fn().mockResolvedValue({ namingConventionIds: [] }),
  },
  registrationAuthorityApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
  languageApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
  contextApi: { options: vi.fn().mockResolvedValue([]) },
  referenceDocumentApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
  namingConventionApi: { list: vi.fn().mockResolvedValue({ content: [] }) },
  pairingApi: {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    delete: vi.fn(),
  },
  readinessApi: { raNamespaceExists: vi.fn().mockResolvedValue({ exists: true }) },
  myContactApi: {
    list: vi.fn().mockResolvedValue(mockContacts),
  },
  organizationApi: {
    list: vi.fn().mockResolvedValue({
      content: [
        { id: 'org-1', name: 'Org One', identifier: 'org-one' },
        { id: 'org-2', name: 'Org Two', identifier: 'org-two' },
      ],
    }),
  },
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

const assistantMocks = vi.hoisted(() => ({
  createJob: vi.fn(),
  poll: vi.fn(),
}));

vi.mock('@/api/assistant', () => ({
  assistantApi: {
    tasks: vi.fn(),
    createJob: assistantMocks.createJob,
    getJob: vi.fn(),
    cancelJob: vi.fn(),
  },
  pollAssistantJob: assistantMocks.poll,
}));

const authState = vi.hoisted(() => ({ isRegistrarUser: true }));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ isRegistrarUser: authState.isRegistrarUser }),
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: unknown) => (params ? `${key} ${JSON.stringify(params)}` : key),
  }),
}));

describe('ItemFormDialog 命名空间输入模式联动', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  async function mountAndOpen(namespaceId: string) {
    const wrapper = mount(ItemFormDialog, {
      props: {
        itemType: 'dataElement',
        submitFn: vi.fn().mockResolvedValue({}),
        showRegistration: true,
      },
      global: {
        plugins: [ElementPlus],
        mocks: {
          $t: (key: string, params?: unknown) => (params ? `${key} ${JSON.stringify(params)}` : key),
        },
      },
    });
    await (wrapper.vm as any).open({ registration: { namespaceId } });
    await wrapper.vm.$nextTick();
    return wrapper;
  }

  function inputs(wrapper: any) {
    return wrapper.findAllComponents({ name: 'ElInput' });
  }

  function radioButtons(wrapper: any) {
    return wrapper.findAllComponents({ name: 'ElRadioButton' });
  }

  function inlineModeControls(wrapper: any) {
    return wrapper.findAllComponents({ name: 'ElSegmented' });
  }

  it('AUTO_WITH_OVERRIDE：显示生成方式单选，默认自动，标识符禁用并提示自动生成', async () => {
    const wrapper = await mountAndOpen('ns-awo');

    // 标识符 + 版本各一个内嵌切换控件
    expect(inlineModeControls(wrapper).length).toBe(2);

    const [identifierInput, versionInput] = inputs(wrapper);
    expect(identifierInput.props('disabled')).toBe(true);
    expect(identifierInput.props('placeholder')).toBe('保存后自动生成');
    // 默认选中自动生成，版本框同样禁用并提示自动生成
    expect(versionInput.props('disabled')).toBe(true);
    expect(versionInput.props('placeholder')).toBe('保存后自动生成');
  });

  it('AUTO_ONLY：不显示单选，标识符与版本输入框恒禁用', async () => {
    const wrapper = await mountAndOpen('ns-auto-only');

    expect(inlineModeControls(wrapper).length).toBe(0);

    const [identifierInput, versionInput] = inputs(wrapper);
    expect(identifierInput.props('disabled')).toBe(true);
    expect(identifierInput.props('placeholder')).toBe('保存后自动生成');
    expect(versionInput.props('disabled')).toBe(true);
  });

  it('MANUAL_ONLY：不显示单选，标识符与版本输入框启用可填', async () => {
    const wrapper = await mountAndOpen('ns-manual');

    expect(inlineModeControls(wrapper).length).toBe(0);

    const [identifierInput, versionInput] = inputs(wrapper);
    expect(identifierInput.props('disabled')).toBe(false);
    expect(identifierInput.props('placeholder')).toBe('唯一标识符');
    expect(versionInput.props('disabled')).toBe(false);
    // DATE 策略占位提示
    expect(versionInput.props('placeholder')).toBe('如 2026-01-31');
  });
});

describe('ItemFormDialog itemType 资源索引', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  async function mountWithItemType(itemType: string, submitFn?: any) {
    const wrapper = mount(ItemFormDialog, {
      props: {
        itemType,
        submitFn: submitFn ?? vi.fn().mockResolvedValue({}),
        showRegistration: true,
      },
      global: {
        plugins: [ElementPlus],
        mocks: {
          $t: (key: string, params?: unknown) => (params ? `${key} ${JSON.stringify(params)}` : key),
        },
      },
    });
    await (wrapper.vm as any).open({});
    await wrapper.vm.$nextTick();
    return wrapper;
  }

  it('主对话框标题按 itemType 通过 i18n 资源 key 索引', async () => {
    const wrapper = await mountWithItemType('dataElement');
    // mock t(key, fallback) 会把 fallback 当 params JSON 化；
    // toContain 验证主键是 meta.itemType.dataElement，且 fallback 也被传入。
    expect((wrapper.vm as any).resolvedTitle).toContain('meta.itemType.dataElement');
  });

  it('不同的 itemType 映射到不同的 i18n key', async () => {
    const wrapper = await mountWithItemType('valueDomain');
    expect((wrapper.vm as any).resolvedTitle).toContain('meta.itemType.valueDomain');
  });

  it('未识别的 itemType 落回原 key，避免显示空白', async () => {
    const wrapper = await mountWithItemType('unknownType');
    // 即使 i18n 资源里查不到，主键仍按 meta.itemType.<itemType> 查找，
    // 同时传入原 itemType 作为 fallback，确保运行时不会出现空白。
    const title = (wrapper.vm as any).resolvedTitle;
    expect(title).toContain('meta.itemType.unknownType');
    expect(title).toContain('unknownType');
  });

  it('指称与定义都为空时 handleSubmit 触发 emptyConfirm 对话框', async () => {
    const wrapper = await mountWithItemType('dataElement');
    await (wrapper.vm as any).handleSubmit();
    await wrapper.vm.$nextTick();
    expect((wrapper.vm as any).emptyConfirmVisible).toBe(true);
  });

  it('只有指称时不应触发 emptyConfirm', async () => {
    const wrapper = await mountWithItemType('dataElement');
    (wrapper.vm as any).form.designations = [{ value: '示例名称' }];
    await (wrapper.vm as any).handleSubmit();
    await wrapper.vm.$nextTick();
    expect((wrapper.vm as any).emptyConfirmVisible).toBe(false);
  });

  it('空确认取消时关闭弹窗并不提交', async () => {
    const submitFn = vi.fn().mockResolvedValue({});
    const wrapper = await mountWithItemType('dataElement', submitFn);
    await (wrapper.vm as any).handleSubmit();
    await wrapper.vm.$nextTick();
    expect((wrapper.vm as any).emptyConfirmVisible).toBe(true);

    // 取消等价于关闭弹窗，且不触发提交链
    (wrapper.vm as any).emptyConfirmVisible = false;
    await wrapper.vm.$nextTick();
    expect(submitFn).not.toHaveBeenCalled();
  });
});

describe('ItemFormDialog 解释性注释提交', () => {

  async function mountEditing() {
    const submitFn = vi.fn().mockResolvedValue({ id: 'item-1' });
    const wrapper = mount(ItemFormDialog, {
      props: { itemType: 'dataElement', submitFn, showRegistration: true },
      global: {
        plugins: [ElementPlus],
        mocks: {
          $t: (key: string, params?: unknown) => (params ? `${key} ${JSON.stringify(params)}` : key),
        },
      },
    });
    await (wrapper.vm as any).open({
      id: 'item-1',
      designations: [{ id: 'dg-1', languageId: '', value: '患者年龄', contexts: [{ contextId: 'ctx-1', acceptability: 'ADMITTED' }] }],
      definitions: [],
      registration: {},
    });
    await wrapper.vm.$nextTick();
    return { wrapper, submitFn };
  }

  it('注释未变更时不往 registration payload 携带 explanatoryComment', async () => {
    const { submitFn } = await mountEditing();
    const wrapper = mount(ItemFormDialog, {
      props: { itemType: 'dataElement', submitFn, showRegistration: true },
      global: {
        plugins: [ElementPlus],
        mocks: {
          $t: (key: string, params?: unknown) => (params ? `${key} ${JSON.stringify(params)}` : key),
        },
      },
    });
    await (wrapper.vm as any).open({
      id: 'item-1',
      designations: [{ id: 'dg-1', languageId: '', value: '患者年龄', contexts: [{ contextId: 'ctx-1', acceptability: 'ADMITTED' }] }],
      registration: {},
    });
    await wrapper.vm.$nextTick();
    await (wrapper.vm as any).handleSubmit();
    await flushPromises();
    const payload = submitFn.mock.calls[0][0];
    expect('explanatoryComment' in payload.registration).toBe(false);
  });
});

describe('ItemFormDialog 字段级 AI 生成', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    assistantMocks.createJob.mockResolvedValue({ jobId: 'job-ai' });
    assistantMocks.poll.mockResolvedValue({
      id: 'job-ai', taskId: 'complete-metadata', status: 'SUCCEEDED',
      result: [{ text: '患者年龄', language: 'zh-CN' }], degraded: false,
    });
  });

  async function mountEditing() {
    const wrapper = mount(ItemFormDialog, {
      attachTo: document.body,
      props: { itemType: 'dataElement', submitFn: vi.fn().mockResolvedValue({}), showRegistration: true },
      global: {
        plugins: [ElementPlus],
        mocks: {
          $t: (key: string, params?: unknown) => (params ? `${key} ${JSON.stringify(params)}` : key),
        },
      },
    });
    await (wrapper.vm as any).open({
      id: 'item-1',
      designations: [{ id: 'dg-1', languageId: 'lang-1', value: '', contexts: [] }],
      definitions: [],
      registration: {},
    });
    await wrapper.vm.$nextTick();
    return wrapper;
  }

  it('编辑态渲染行内 AI 生成按钮；点击自动生成并点选回填选中行', async () => {
    const wrapper = await mountEditing();
    const btn = document.body.querySelector('[data-testid="ai-generate-btn"]') as HTMLElement;
    expect(btn).toBeTruthy();
    btn.click();
    await wrapper.vm.$nextTick();
    await flushPromises();
    expect(assistantMocks.createJob).toHaveBeenCalledWith(expect.objectContaining({
      taskId: 'complete-metadata',
      itemId: 'item-1',
      params: { fields: ['designation'] },
    }));
    const item = document.body.querySelector('[data-test="ai-candidate-item-0"]') as HTMLElement;
    expect(item).toBeTruthy();
    item.click();
    await wrapper.vm.$nextTick();
    expect((wrapper.vm as any).form.designations[0].value).toBe('患者年龄');
    expect((wrapper.vm as any).aiGen).toBeNull();
    wrapper.unmount();
  });

  // 回归：真实浏览器中 ElTooltip 虚拟锚定（trigger 默认 hover）会在生成中把浮层关掉
  //（鼠标移出卡片 → update:visible(false)），导致 aiGen=null、cacheKey prop 回退空串，
  // 缓存写进 ''、悬停回看永远 miss。修复后：成功必须回写发起时的 key，悬停按该行 key 命中且不请求。
  it('生成中浮层被外部关闭：缓存写正确 key，悬停回看不再请求', async () => {
    let resolvePoll!: (value: unknown) => void;
    assistantMocks.poll.mockImplementation(() => new Promise((res) => { resolvePoll = res; }));
    const wrapper = await mountEditing();
    const btns = document.body.querySelectorAll('[data-testid="ai-generate-btn"]');
    const btn = btns[btns.length - 1] as HTMLElement;
    expect(btn).toBeTruthy();

    btn.click();
    await flushPromises();
    expect(assistantMocks.createJob).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeTruthy();

    // 生成进行中点击浮层外（click-outside 关闭路径，经 ElTooltip hideAfter 200ms 生效）
    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await vi.waitFor(() => expect((wrapper.vm as any).aiGen).toBeNull(), { timeout: 3000, interval: 100 });
    await flushPromises();

    // 生成成功：缓存必须写在 designation:dg-1（而非空 key）
    resolvePoll({
      id: 'job-ai', taskId: 'complete-metadata', status: 'SUCCEEDED',
      result: [{ text: '患者年龄', language: 'zh-CN' }], degraded: false,
    });
    await flushPromises();
    expect((wrapper.vm as any).aiCandidateCache).toEqual({
      'designation:dg-1': [{ text: '患者年龄', language: 'zh-CN' }],
    });

    // 悬停同一行：view 模式打开放置缓存的浮层，不发新请求
    btn.dispatchEvent(new Event('mouseenter'));
    await flushPromises();
    expect(assistantMocks.createJob).toHaveBeenCalledTimes(1);
    const item = document.body.querySelector('[data-test="ai-candidate-item-0"]') as HTMLElement;
    expect(item?.textContent).toContain('患者年龄');
    wrapper.unmount();
  });

  // 回归：start() 无防重入时，loading 期间浮层被关闭（phase 重置 idle）后再点击会重复发请求
  //（调试侧栏成对作业的根因）。修复后：进行中重复触发一律忽略，一次点击严格一次请求。
  it('生成中浮层被关闭后再次点击：仍只发一次请求，完成后悬停回看可用', async () => {
    let resolvePoll!: (value: unknown) => void;
    assistantMocks.poll.mockImplementation(() => new Promise((res) => { resolvePoll = res; }));
    const wrapper = await mountEditing();
    const btns = document.body.querySelectorAll('[data-testid="ai-generate-btn"]');
    const btn = btns[btns.length - 1] as HTMLElement;
    expect(btn).toBeTruthy();

    btn.click();
    await flushPromises();
    expect(assistantMocks.createJob).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeTruthy();

    // 生成中被外部关闭（click-outside）→ 用户以为没点上，再次点击同一行
    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await vi.waitFor(() => expect((wrapper.vm as any).aiGen).toBeNull(), { timeout: 3000, interval: 100 });
    btn.click();
    await flushPromises();
    expect(assistantMocks.createJob).toHaveBeenCalledTimes(1); // 关键断言：不重复发请求

    // 作业完成：缓存写正确 key，悬停回看展示且不请求
    resolvePoll({
      id: 'job-ai', taskId: 'complete-metadata', status: 'SUCCEEDED',
      result: [{ text: '患者年龄', language: 'zh-CN' }], degraded: false,
    });
    await flushPromises();
    expect((wrapper.vm as any).aiCandidateCache).toEqual({
      'designation:dg-1': [{ text: '患者年龄', language: 'zh-CN' }],
    });
    btn.dispatchEvent(new Event('mouseenter'));
    await flushPromises();
    expect(assistantMocks.createJob).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector('[data-test="ai-candidate-item-0"]')?.textContent).toContain('患者年龄');
    wrapper.unmount();
  });

  // 悬停体验：生成中浮层被关闭后，再次悬停应重开浮层并继续显示加载态（不发新请求），
  // 作业返回后同一浮层就地展示候选——用户随时能看到「此刻正在发生什么」。
  it('生成中关闭后悬停：浮层重开为加载态（不重复请求），结果到达就地展示候选', async () => {
    let resolvePoll!: (value: unknown) => void;
    assistantMocks.poll.mockImplementation(() => new Promise((res) => { resolvePoll = res; }));
    const wrapper = await mountEditing();
    const btns = document.body.querySelectorAll('[data-testid="ai-generate-btn"]');
    const btn = btns[btns.length - 1] as HTMLElement;
    expect(btn).toBeTruthy();

    btn.click();
    await flushPromises();
    expect(assistantMocks.createJob).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeTruthy();

    // 生成中浮层被外部关闭（click-outside）
    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await vi.waitFor(() => expect((wrapper.vm as any).aiGen).toBeNull(), { timeout: 3000, interval: 100 });
    await flushPromises();
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeFalsy();

    // 再次悬停同一行：「进行中」分支 → 浮层重开为加载态，不重复发请求
    btn.dispatchEvent(new Event('mouseenter'));
    await flushPromises();
    expect(assistantMocks.createJob).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector('[data-test="ai-candidate-loading"]')).toBeTruthy();

    // 作业完成：同一浮层就地切换为候选列表
    resolvePoll({
      id: 'job-ai', taskId: 'complete-metadata', status: 'SUCCEEDED',
      result: [{ text: '患者年龄', language: 'zh-CN' }], degraded: false,
    });
    await flushPromises();
    expect(document.body.querySelector('[data-test="ai-candidate-item-0"]')?.textContent).toContain('患者年龄');
    expect(assistantMocks.createJob).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  /* ---- ai-generate-everywhere（D1/D3）：门控放开为仅 Registrar，新建模式以表单草稿为上下文 ---- */

  async function mountCreate() {
    const wrapper = mount(ItemFormDialog, {
      attachTo: document.body,
      props: { itemType: 'dataElement', submitFn: vi.fn().mockResolvedValue({}), showRegistration: true },
      global: {
        plugins: [ElementPlus],
        mocks: {
          $t: (key: string, params?: unknown) => (params ? `${key} ${JSON.stringify(params)}` : key),
        },
      },
    });
    await (wrapper.vm as any).open({});
    await wrapper.vm.$nextTick();
    return wrapper;
  }

  it('新建模式：Registrar 可见行内按钮；点击载荷无 itemId、draft 含条目类型与全部已填行（空行跳过）', async () => {
    const wrapper = await mountCreate();
    (wrapper.vm as any).languages = [{ id: 'lang-1', languageCode: 'zh-CN', languageIdentifier: 'zh-CN' }];
    (wrapper.vm as any).form.designations = [
      { clientKey: 'ck-dg1', languageId: 'lang-1', value: '血压' },
      { clientKey: 'ck-dg2', languageId: '', value: '   ' },
    ];
    (wrapper.vm as any).form.definitions = [
      { clientKey: 'ck-df1', languageId: '', value: '收缩压与舒张压的测量值' },
    ];
    await wrapper.vm.$nextTick();

    const btns = document.body.querySelectorAll('[data-testid="ai-generate-btn"]');
    expect(btns.length).toBe(3);
    (btns[0] as HTMLElement).click(); // 指称第 1 行（目标行内容须包含在 draft 中）
    await flushPromises();

    expect(assistantMocks.createJob).toHaveBeenCalledTimes(1);
    const payload = assistantMocks.createJob.mock.calls[0][0];
    expect(payload.itemId).toBeUndefined();
    expect(payload).toEqual(expect.objectContaining({ taskId: 'complete-metadata' }));
    expect(payload.params).toEqual({
      fields: ['designation'],
      draft: {
        itemType: 'dataElement',
        designations: [{ language: 'zh-CN', value: '血压' }],
        definitions: [{ value: '收缩压与舒张压的测量值' }],
      },
    });
    wrapper.unmount();
  });

  it('编辑模式：载荷仍带 itemId 且不带 draft', async () => {
    const wrapper = await mountEditing();
    const btns = document.body.querySelectorAll('[data-testid="ai-generate-btn"]');
    (btns[btns.length - 1] as HTMLElement).click();
    await flushPromises();
    expect(assistantMocks.createJob).toHaveBeenCalledTimes(1);
    const payload = assistantMocks.createJob.mock.calls[0][0];
    expect(payload.itemId).toBe('item-1');
    expect(payload.params).toEqual({ fields: ['designation'] });
    wrapper.unmount();
  });

  it('非 Registrar：新建与编辑对话框均无 AI 生成按钮', async () => {
    authState.isRegistrarUser = false;
    try {
      const createWrapper = await mountCreate();
      expect(document.body.querySelectorAll('[data-testid="ai-generate-btn"]').length).toBe(0);
      createWrapper.unmount();

      const editWrapper = await mountEditing();
      expect(document.body.querySelectorAll('[data-testid="ai-generate-btn"]').length).toBe(0);
      editWrapper.unmount();
    } finally {
      authState.isRegistrarUser = true;
    }
  });

  it('新建模式：缓存 key 用行 clientKey；生成后悬停回看零请求', async () => {
    const wrapper = await mountCreate();
    (wrapper.vm as any).form.designations = [{ clientKey: 'ck-1', languageId: '', value: '血压' }];
    await wrapper.vm.$nextTick();
    const btns = document.body.querySelectorAll('[data-testid="ai-generate-btn"]');
    const btn = btns[btns.length - 1] as HTMLElement;

    btn.click();
    await flushPromises();
    expect(assistantMocks.createJob).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector('[data-test="ai-candidate-item-0"]')?.textContent).toContain('患者年龄');

    // 关闭浮层后悬停回看：命中 clientKey 缓存，不重复发请求
    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await vi.waitFor(() => expect((wrapper.vm as any).aiGen).toBeNull(), { timeout: 3000, interval: 100 });
    expect((wrapper.vm as any).aiCandidateCache).toEqual({
      'designation:ck-1': [{ text: '患者年龄', language: 'zh-CN' }],
    });
    btn.dispatchEvent(new Event('mouseenter'));
    await flushPromises();
    expect(assistantMocks.createJob).toHaveBeenCalledTimes(1);
    expect(document.body.querySelector('[data-test="ai-candidate-item-0"]')?.textContent).toContain('患者年龄');
    wrapper.unmount();
  });
});

describe('ItemFormDialog 提交信息预填', () => {
  async function mountWithContacts() {
    const wrapper = mount(ItemFormDialog, {
      props: {
        itemType: 'dataElement',
        submitFn: vi.fn().mockResolvedValue({}),
        showRegistration: true,
      },
      global: {
        plugins: [ElementPlus],
        mocks: {
          $t: (key: string, params?: unknown) => (params ? `${key} ${JSON.stringify(params)}` : key),
        },
      },
    });
    await (wrapper.vm as unknown as DialogVm).open({});
    await wrapper.vm.$nextTick();
    return wrapper;
  }

  it('有联系人时默认选中 isDefault 项并联动所属组织', async () => {
    const wrapper = await mountWithContacts();
    const vm = wrapper.vm as unknown as DialogVm;
    expect(vm.contacts.length).toBe(2);
    expect(vm.form.registration.submission.contactId).toBe('contact-1');
    expect(vm.form.registration.submission.organizationId).toBe('org-1');
    wrapper.unmount();
  });

  it('切换联系人时所属组织联动更新', async () => {
    const wrapper = await mountWithContacts();
    const vm = wrapper.vm as unknown as DialogVm;
    vm.form.registration.submission.contactId = 'contact-2';
    await wrapper.vm.$nextTick();
    vm.onContactChange('contact-2');
    await wrapper.vm.$nextTick();
    expect(vm.form.registration.submission.organizationId).toBe('org-2');
    wrapper.unmount();
  });

  it('保存时 registration payload 包含 submission 字段', async () => {
    const submitFn = vi.fn().mockResolvedValue({});
    const wrapper = mount(ItemFormDialog, {
      props: { itemType: 'dataElement', submitFn, showRegistration: true },
      global: {
        plugins: [ElementPlus],
        mocks: {
          $t: (key: string, params?: unknown) => (params ? `${key} ${JSON.stringify(params)}` : key),
        },
      },
    });
    await (wrapper.vm as unknown as DialogVm).open({
      designations: [{ id: 'dg-1', languageId: '', value: '测试', contexts: [{ contextId: 'ctx-1', acceptability: 'ADMITTED' }] }],
      registration: { namespaceId: 'ns-awo' },
    });
    await wrapper.vm.$nextTick();
    const vm = wrapper.vm as unknown as DialogVm;
    vm.form.registration.submission.contactId = 'contact-2';
    vm.form.registration.submission.organizationId = 'org-2';
    await wrapper.vm.$nextTick();

    await vm.handleSubmit();
    await flushPromises();

    const payload = submitFn.mock.calls[0][0];
    expect(payload.registration.submission).toEqual({
      contactId: 'contact-2',
      organizationId: 'org-2',
    });
    wrapper.unmount();
  });

  it('无联系人时显示 noContact 提示', async () => {
    vi.mocked(myContactApi).list.mockResolvedValueOnce([]);
    const wrapper = await mountWithContacts();
    expect(wrapper.find('[data-testid="stewardship-no-contact-alert"]').exists()).toBe(true);
    expect(wrapper.find('[data-testid="submission-contact-select"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="stewardship-contact-select"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('registrationRecords 标签页存在且包含 submission/stewardship 两个 collapse', async () => {
    const wrapper = await mountWithContacts();
    const pane = wrapper.findComponent({ name: 'ElTabPane' });
    expect(pane.exists()).toBe(true);
    const collapseItems = wrapper.findAllComponents({ name: 'ElCollapseItem' });
    expect(collapseItems.length).toBeGreaterThanOrEqual(2);
    expect(collapseItems.some((c) => c.props('name') === 'submission')).toBe(true);
    expect(collapseItems.some((c) => c.props('name') === 'stewardship')).toBe(true);
    wrapper.unmount();
  });

  it('stewardship 切换联系人时所属组织联动更新', async () => {
    const wrapper = await mountWithContacts();
    const vm = wrapper.vm as unknown as DialogVm;
    vm.form.registration.stewardship.contactId = 'contact-2';
    await wrapper.vm.$nextTick();
    vm.onStewardshipContactChange('contact-2');
    await wrapper.vm.$nextTick();
    expect(vm.form.registration.stewardship.organizationId).toBe('org-2');
    wrapper.unmount();
  });

  it('保存时 registration payload 包含 stewardship 字段', async () => {
    const submitFn = vi.fn().mockResolvedValue({});
    const wrapper = mount(ItemFormDialog, {
      props: { itemType: 'dataElement', submitFn, showRegistration: true },
      global: {
        plugins: [ElementPlus],
        mocks: {
          $t: (key: string, params?: unknown) => (params ? `${key} ${JSON.stringify(params)}` : key),
        },
      },
    });
    await (wrapper.vm as unknown as DialogVm).open({
      designations: [{ id: 'dg-1', languageId: '', value: '测试', contexts: [{ contextId: 'ctx-1', acceptability: 'ADMITTED' }] }],
      registration: { namespaceId: 'ns-awo' },
    });
    await wrapper.vm.$nextTick();
    const vm = wrapper.vm as unknown as DialogVm;
    vm.form.registration.stewardship.contactId = 'contact-1';
    vm.form.registration.stewardship.organizationId = 'org-1';
    await wrapper.vm.$nextTick();

    await vm.handleSubmit();
    await flushPromises();

    const payload = submitFn.mock.calls[0][0];
    expect(payload.registration.stewardship).toEqual({
      contactId: 'contact-1',
      organizationId: 'org-1',
    });
    wrapper.unmount();
  });
});

describe('ItemFormDialog 指称与定义合并 Tab', () => {
  async function mountAndOpen() {
    const wrapper = mount(ItemFormDialog, {
      props: {
        itemType: 'dataElement',
        submitFn: vi.fn().mockResolvedValue({}),
        showRegistration: true,
      },
      global: {
        plugins: [ElementPlus],
        mocks: {
          $t: (key: string, params?: unknown) => (params ? `${key} ${JSON.stringify(params)}` : key),
        },
      },
    });
    await (wrapper.vm as unknown as DialogVm).open({});
    await wrapper.vm.$nextTick();
    return wrapper;
  }

  it('"指称与定义"一级 Tab 含指称/定义/指称-定义配对三个二级 Tab', async () => {
    const wrapper = await mountAndOpen();
    const panes = wrapper.findAllComponents({ name: 'ElTabPane' });
    const outer = panes.find((p) => p.props('name') === 'designationDefinition');
    expect(outer).toBeDefined();
    const names = panes.map((p) => p.props('name'));
    expect(names).toContain('designations');
    expect(names).toContain('definitions');
    expect(names).toContain('pairings');
    wrapper.unmount();
  });

  it('activateNamingTab 同时定位一级与二级 Tab', async () => {
    const wrapper = await mountAndOpen();
    const vm = wrapper.vm as unknown as DialogVm;
    vm.activeTab = 'registration';
    vm.activateNamingTab('pairings');
    expect(vm.activeTab).toBe('designationDefinition');
    expect(vm.activeSubTab).toBe('pairings');
    wrapper.unmount();
  });
});
