import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus, { ElMessageBox } from 'element-plus';
import { createI18n } from 'vue-i18n';
import type { VueWrapper } from '@vue/test-utils';
import RegistrationAuthorityList from './RegistrationAuthorityList.vue';
import { registrationAuthorityApi } from '@/api/resources';
import request from '@/utils/request';
import zhCN from '@/i18n/zh-CN.json';

const raRow = vi.hoisted(() => ({
  id: 'ra-1',
  organizationIdentifier: 'RA-001',
  name: '示例机构',
  deletable: true,
}));

const raPrimaryRow = vi.hoisted(() => ({
  id: 'ra-primary',
  organizationIdentifier: 'RA-PRIMARY',
  name: '主要注册机构',
  deletable: false,
}));

const previewFixture = vi.hoisted(() => ({
  registrationAuthorityId: 'ra-1',
  administeredItemCount: 3,
  submissionRecordCount: 2,
  registrarCount: 1,
  namespaceCount: 4,
  stewardshipRecordCount: 0,
  transitionRuleCount: 5,
}));

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
  },
}));

vi.mock('@/api/resources', () => ({
  registrationAuthorityApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    deletionPreview: vi.fn(),
  },
}));

// SegmentTemplateDesigner（向导步骤二）挂载时经 identifierStrategyApi 拉预设与缩写表，
// 这里打桩为空，避免真实 request 被 @/utils/request mock 污染（presets 必须是数组）
vi.mock('@/api/identifierStrategy', () => ({
  identifierStrategyApi: {
    presets: vi.fn().mockResolvedValue([]),
    objectAbbreviations: vi.fn().mockResolvedValue({}),
    previewAnonymous: vi.fn().mockResolvedValue({ expression: '', sample: '', parsed: 0, skipped: 0 }),
  },
}));

const mockedStatusGet = vi.mocked(request.get);
const mockedList = vi.mocked(registrationAuthorityApi.list);
const mockedDelete = vi.mocked(registrationAuthorityApi.delete);
const mockedPreview = vi.mocked(registrationAuthorityApi.deletionPreview);

function pageOf(content: any[]) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1 };
}

function mountList() {
  const i18n = createI18n({
    legacy: false,
    locale: 'zh-CN',
    messages: { 'zh-CN': zhCN as any },
  });
  return mount(RegistrationAuthorityList, {
    attachTo: document.body,
    global: { plugins: [ElementPlus, i18n] },
  });
}

const mounted: VueWrapper[] = [];

beforeEach(() => {
  vi.clearAllMocks();
  mockedList.mockResolvedValue(pageOf([raRow]) as any);
  mockedPreview.mockResolvedValue(previewFixture as any);
  mockedDelete.mockResolvedValue(undefined as any);
});

afterEach(() => {
  while (mounted.length) mounted.pop()!.unmount();
  document.body.innerHTML = '';
});

describe('RegistrationAuthorityList（acg-submission）', () => {
  it('个人版恢复新增入口，对话框展示自动成为注册人员提示', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'PERSONAL' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    const createBtn = wrapper.find('[data-testid="data-table-create-btn"]');
    expect(createBtn.exists()).toBe(true);
    expect((createBtn.element as HTMLButtonElement).disabled).toBe(false);

    await createBtn.trigger('click');
    await flushPromises();

    const hint = document.body.querySelector('[data-testid="create-registrar-hint"]');
    expect(hint).not.toBeNull();
    expect(hint!.textContent).toContain('创建后您将自动成为该机构的注册人员');
  });

  it('新建对话框暴露标准复合标识三段（国际代码标识符/组织标识符必填，组织部件标识符选填）', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'TEAM' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    await wrapper.find('[data-testid="data-table-create-btn"]').trigger('click');
    await flushPromises();

    const icd = document.body.querySelector('[data-testid="ra-icd-input"]');
    const orgPart = document.body.querySelector('[data-testid="ra-org-part-input"]');
    expect(icd).not.toBeNull();
    expect(orgPart).not.toBeNull();
    const dialogText = document.body.querySelector('.el-dialog')?.textContent ?? '';
    expect(dialogText).toContain('国际代码标识符');
    expect(dialogText).toContain('组织标识符');
    expect(dialogText).toContain('组织部件标识符');
  });

  it('新建对话框陈列组织属性（通信地址/电子邮箱/电话/网址）', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'TEAM' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    await wrapper.find('[data-testid="data-table-create-btn"]').trigger('click');
    await flushPromises();

    expect(document.body.querySelector('[data-testid="ra-mail-input"]')).not.toBeNull();
    expect(document.body.querySelector('[data-testid="ra-email-input"]')).not.toBeNull();
    expect(document.body.querySelector('[data-testid="ra-phone-input"]')).not.toBeNull();
    expect(document.body.querySelector('[data-testid="ra-web-input"]')).not.toBeNull();
    const dialogText = document.body.querySelector('.el-dialog')?.textContent ?? '';
    expect(dialogText).toContain('通信地址');
    expect(dialogText).toContain('电子邮箱');
    expect(dialogText).toContain('电话号码');
    expect(dialogText).toContain('网址');
  });

  it('团队版已有注册机构时仍显示新建入口（无运行模式数量限制）', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'TEAM' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    const createBtn = wrapper.find('[data-testid="data-table-create-btn"]');
    expect(createBtn.exists()).toBe(true);
  });

  it('删除：先取删除预览，确认对话框展示 6 项计数与不可恢复警告，确认后才删除', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'PERSONAL' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    const confirmSpy = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(undefined as never);

    await wrapper.find('[data-testid="row-delete"]').trigger('click');
    await flushPromises();

    expect(mockedPreview).toHaveBeenCalledWith('ra-1');
    expect(confirmSpy).toHaveBeenCalled();
    const message = confirmSpy.mock.calls[0][0] as string;
    expect(message).toContain('受管项: 3');
    expect(message).toContain('提交记录: 2');
    expect(message).toContain('注册人员: 1');
    expect(message).toContain('命名空间: 4');
    expect(message).toContain('主管记录: 0');
    expect(message).toContain('流转规则: 5');
    expect(message).toContain('删除该注册机构将同时删除其下全部受管项及关联数据，且不可恢复');

    await flushPromises();
    expect(mockedDelete).toHaveBeenCalledWith('ra-1');
    confirmSpy.mockRestore();
  });

  it('m6：个人版 Primary 行（deletable=false）删除入口禁用并给出说明', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'PERSONAL' } as any);
    mockedList.mockResolvedValue(pageOf([raRow, raPrimaryRow]) as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    const buttons = wrapper.findAll('[data-testid="row-delete"]');
    expect(buttons).toHaveLength(2);
    expect((buttons[0].element as HTMLButtonElement).disabled).toBe(false);
    expect((buttons[1].element as HTMLButtonElement).disabled).toBe(true);

    const tooltip = wrapper
      .findAllComponents({ name: 'ElTooltip' })
      .find((t) => (t.props('content') as string | undefined)?.includes('主要注册机构不允许删除'));
    expect(tooltip).toBeTruthy();
  });

  it('删除：取消确认后不调用删除接口', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'PERSONAL' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    const confirmSpy = vi
      .spyOn(ElMessageBox, 'confirm')
      .mockRejectedValue(new Error('cancel') as never);

    await wrapper.find('[data-testid="row-delete"]').trigger('click');
    await flushPromises();

    expect(mockedPreview).toHaveBeenCalledWith('ra-1');
    expect(mockedDelete).not.toHaveBeenCalled();
    confirmSpy.mockRestore();
  });
});

describe('RegistrationAuthorityList 新建向导（ra-creation-wizard）', () => {
  const mockedCreate = vi.mocked(registrationAuthorityApi.create);

  beforeEach(() => {
    mockedCreate.mockResolvedValue({ id: 'ra-new' } as any);
  });

  function bodyEl(selector: string): HTMLElement {
    return document.body.querySelector(selector) as HTMLElement;
  }

  function clickBody(selector: string) {
    bodyEl(selector).click();
  }

  function setInputValue(selector: string, value: string) {
    const input = document.body.querySelector(selector) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function openWizard(wrapper: VueWrapper) {
    await wrapper.find('[data-testid="data-table-create-btn"]').trigger('click');
    await flushPromises();
  }

  /** 填写步骤一两个必填项（国际代码标识符/组织标识符）并进入步骤二 */
  async function goToStep2() {
    setInputValue('[data-testid="ra-icd-input"]', 'ISO 6523');
    setInputValue('[data-testid="ra-org-input"]', 'RA-NEW');
    await flushPromises();
    clickBody('[data-testid="ra-wizard-next"]');
    await flushPromises();
  }

  async function submitWizard() {
    clickBody('[data-testid="ra-wizard-submit"]');
    await flushPromises();
  }

  it('新建打开两步向导，步骤标题"注册机构信息"/"命名空间"可见', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'TEAM' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    await openWizard(wrapper);

    const titles = Array.from(document.body.querySelectorAll('.el-step__title')).map(
      (el) => el.textContent ?? ''
    );
    expect(titles).toContain('注册机构信息');
    expect(titles).toContain('命名空间');
  });

  it('团队版注册人员开关缺省开启，关闭后提交 payload selfAsRegistrar=false', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'TEAM' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    await openWizard(wrapper);
    expect(bodyEl('[data-testid="ra-wizard-self-registrar-switch"]')).not.toBeNull();

    // 缺省开启：直接提交 → selfAsRegistrar=true
    await goToStep2();
    await submitWizard();
    expect(mockedCreate).toHaveBeenCalledTimes(1);
    expect(mockedCreate.mock.calls[0][0].selfAsRegistrar).toBe(true);

    // 关闭开关后再提交 → selfAsRegistrar=false
    mockedCreate.mockClear();
    await openWizard(wrapper);
    clickBody('[data-testid="ra-wizard-self-registrar-switch"]');
    await flushPromises();
    await goToStep2();
    await submitWizard();
    expect(mockedCreate).toHaveBeenCalledTimes(1);
    expect(mockedCreate.mock.calls[0][0].selfAsRegistrar).toBe(false);
    expect(mockedCreate.mock.calls[0][0].createNamespace).toBe(false);
    expect(mockedCreate.mock.calls[0][0]).not.toHaveProperty('namespaceName');
  });

  it('个人版无注册人员开关，提交 payload selfAsRegistrar=true', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'PERSONAL' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    await openWizard(wrapper);

    // 个人版固定提示保留，且不提供开关
    const hint = document.body.querySelector('[data-testid="create-registrar-hint"]');
    expect(hint).not.toBeNull();
    expect(hint!.textContent).toContain('创建后您将自动成为该机构的注册人员');
    expect(document.body.querySelector('[data-testid="ra-wizard-self-registrar-switch"]')).toBeNull();

    await goToStep2();
    await submitWizard();
    expect(mockedCreate).toHaveBeenCalledTimes(1);
    expect(mockedCreate.mock.calls[0][0].selfAsRegistrar).toBe(true);
  });

  it('命名空间开关关闭时无编辑字段，开启后展现命名空间名称输入框', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'TEAM' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    await openWizard(wrapper);
    await goToStep2();

    // 关闭（缺省）时不渲染编辑区
    expect(document.body.querySelector('[data-testid="ra-wizard-ns-name-input"]')).toBeNull();

    clickBody('[data-testid="ra-wizard-create-ns-switch"]');
    await flushPromises();

    expect(document.body.querySelector('[data-testid="ra-wizard-ns-name-input"]')).not.toBeNull();
  });

  it('createNamespace=true 时 payload 携带命名空间字段', async () => {
    mockedStatusGet.mockResolvedValue({ edition: 'TEAM' } as any);
    const wrapper = mountList();
    mounted.push(wrapper);
    await flushPromises();

    await openWizard(wrapper);
    await goToStep2();

    clickBody('[data-testid="ra-wizard-create-ns-switch"]');
    await flushPromises();
    setInputValue('[data-testid="ra-wizard-ns-name-input"]', '质量数据命名空间');
    await flushPromises();

    await submitWizard();

    expect(mockedCreate).toHaveBeenCalledTimes(1);
    const payload = mockedCreate.mock.calls[0][0];
    expect(payload.createNamespace).toBe(true);
    expect(payload.namespaceName).toBe('质量数据命名空间');
    expect(payload.identifierInputMode).toBe('AUTO_WITH_OVERRIDE');
    expect(payload.versionInputMode).toBe('AUTO_WITH_OVERRIDE');
    expect(payload.versionPolicy).toBe('SEMVER');
    expect(JSON.parse(payload.identifierTemplate as string).segments).toHaveLength(3);
  });
});
