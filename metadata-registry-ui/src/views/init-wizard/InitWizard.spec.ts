import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import ElementPlus, { ElMessage, ElMessageBox } from 'element-plus';
import InitWizard from './InitWizard.vue';
import {
  DATASOURCE_RESTART_SESSION_KEY,
  readRestartSession,
  writeRestartSession,
  type DatasourceRestartSnapshot,
} from '@/utils/datasourceRestart';

const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}));
const restartApiMock = vi.hoisted(() => ({
  getCapability: vi.fn(),
  apply: vi.fn(),
  confirmCurrent: vi.fn(),
  getRestartStatus: vi.fn(),
}));
const languageListMock = vi.hoisted(() => vi.fn());
const routerPushMock = vi.hoisted(() => vi.fn());
const routeLeaveGuards = vi.hoisted(() => [] as Array<() => unknown>);
const monitorState = vi.hoisted(() => ({
  instances: [] as Array<{
    options: { onUpdate: (snapshot: DatasourceRestartSnapshot) => void; restartId: string };
    start: ReturnType<typeof vi.fn>;
    stop: ReturnType<typeof vi.fn>;
    retry: ReturnType<typeof vi.fn>;
  }>,
}));
const authState = vi.hoisted(() => ({
  initialized: false as boolean | null,
  edition: 'PERSONAL' as string | null,
  user: null as unknown,
}));
const authMock = vi.hoisted(() => ({
  setInitialized: vi.fn(),
  setEdition: vi.fn(),
  checkInitialized: vi.fn(),
  refreshInitialized: vi.fn(),
  logout: vi.fn(),
  adoptSession: vi.fn(),
}));

vi.mock('@/utils/request', () => ({
  default: requestMock,
  withSilentErrors: <T>(operation: () => Promise<T>) => operation(),
}));
vi.mock('@/api/datasourceRestart', () => ({
  datasourceRestartApi: restartApiMock,
}));
vi.mock('@/api/resources', () => ({
  languageApi: { list: languageListMock },
}));
vi.mock('@/components/identifier/SegmentTemplateDesigner.vue', () => ({
  default: { template: '<div />' },
}));
vi.mock('@/utils/datasourceRestart', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/datasourceRestart')>();
  return {
    ...actual,
    createDatasourceRestartMonitor: (options: {
      onUpdate: (snapshot: DatasourceRestartSnapshot) => void;
      restartId: string;
    }) => {
      const instance = {
        options,
        start: vi.fn(),
        stop: vi.fn(),
        retry: vi.fn(),
      };
      monitorState.instances.push(instance);
      return instance;
    },
  };
});
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPushMock }),
  onBeforeRouteLeave: (guard: () => unknown) => routeLeaveGuards.push(guard),
}));
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    get initialized() { return authState.initialized; },
    get edition() { return authState.edition; },
    get user() { return authState.user; },
    set user(value: unknown) { authState.user = value; },
    setInitialized: authMock.setInitialized,
    setEdition: authMock.setEdition,
    checkInitialized: authMock.checkInitialized,
    refreshInitialized: authMock.refreshInitialized,
    logout: authMock.logout,
    adoptSession: authMock.adoptSession,
  }),
}));

const currentDb = {
  vendor: 'sqlserver',
  jdbcUrl: 'jdbc:sqlserver://localhost:1433;databaseName=metadata_registry',
  schema: 'mdr',
  driverClassName: 'com.microsoft.sqlserver.jdbc.SQLServerDriver',
  host: 'localhost',
  port: 1433,
  database: 'metadata_registry',
  filePath: null,
  hasCredentials: false,
};

const wrappers: Array<ReturnType<typeof mount>> = [];

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => { resolve = res; });
  return { promise, resolve };
}

function findButton(wrapper: ReturnType<typeof mount>, text: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text().includes(text));
  expect(button, `未找到按钮：${text}`).toBeDefined();
  return button!;
}

async function clickButton(wrapper: ReturnType<typeof mount>, text: string) {
  await findButton(wrapper, text).trigger('click');
  await flushPromises();
}

async function mountWizard() {
  const wrapper = mount(InitWizard, {
    global: { plugins: [ElementPlus] },
  });
  wrappers.push(wrapper);
  await flushPromises();
  return wrapper;
}

async function openChangedDatabaseForm(wrapper: ReturnType<typeof mount>) {
  await clickButton(wrapper, '下一步');
  const changeChoice = wrapper.findAll('.el-radio').find((radio) => radio.text().includes('配置新的数据源'));
  expect(changeChoice).toBeDefined();
  const input = changeChoice!.find('input[type="radio"]');
  expect(input.exists()).toBe(true);
  await input.setValue();
  await flushPromises();
}

async function modifyAndTest(wrapper: ReturnType<typeof mount>) {
  await wrapper.find('input[placeholder="localhost"]').setValue('db.example.local');
  await clickButton(wrapper, '测试连接');
}

function snapshot(
  phase: DatasourceRestartSnapshot['phase'],
  state: NonNullable<DatasourceRestartSnapshot['status']>['state'] = 'PENDING',
  verification: NonNullable<DatasourceRestartSnapshot['status']>['verification'] = 'PENDING',
  reasonCode: string | null = null
): DatasourceRestartSnapshot {
  return {
    phase,
    elapsedMs: 1000,
    reasonCode,
    status: { restartId: 'restart-1', state, verification, reasonCode },
  };
}

describe('InitWizard 数据源自动重启', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    monitorState.instances.length = 0;
    routeLeaveGuards.length = 0;
    authState.initialized = false;
    authState.edition = 'PERSONAL';
    authState.user = null;
    vi.stubGlobal('crypto', { randomUUID: vi.fn().mockReturnValue('restart-1') });
    restartApiMock.getCapability.mockResolvedValue({ supported: true, reasonCode: null });
    restartApiMock.apply.mockResolvedValue({ restartId: 'restart-1' });
    // 保持当前数据源：默认完整相已确认，无需重启
    restartApiMock.confirmCurrent.mockResolvedValue({ restartRequired: false, restartId: null });
    languageListMock.mockResolvedValue({ content: [] });
    requestMock.get.mockResolvedValue({ ...currentDb });
    requestMock.post.mockImplementation((url: string) => {
      if (url === '/datasource/test-connection') {
        return Promise.resolve({ success: true, message: '连接成功' });
      }
      return Promise.resolve(null);
    });
    requestMock.put.mockResolvedValue(undefined);
    authMock.checkInitialized.mockImplementation(async () => authState.initialized ?? true);
    // refreshInitialized 与 checkInitialized 的区别正在这里：取不到新值时返回 null，
    // 而不是降级成 true。
    authMock.refreshInitialized.mockImplementation(async () => authState.initialized);
    authMock.setInitialized.mockImplementation((value: boolean | null) => {
      authState.initialized = value;
    });
    authMock.setEdition.mockImplementation((value: string | null) => {
      authState.edition = value;
    });
    authMock.logout.mockImplementation(() => {
      authState.initialized = null;
      authState.user = null;
    });
    vi.spyOn(ElMessage, 'error').mockImplementation(vi.fn() as never);
    vi.spyOn(ElMessage, 'success').mockImplementation(vi.fn() as never);
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValue(new Error('cancel'));
  });

  afterEach(() => {
    wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
    sessionStorage.clear();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    document.body.innerHTML = '';
  });

  it('受管个人版下一步先写非敏感标记再自动 apply，并禁止重复提交', async () => {
    const applyDeferred = deferred<{ restartId: string }>();
    restartApiMock.apply.mockReturnValue(applyDeferred.promise);
    const wrapper = await mountWizard();
    await openChangedDatabaseForm(wrapper);
    await modifyAndTest(wrapper);

    const storageWrite = vi.spyOn(Storage.prototype, 'setItem');
    const nextButton = findButton(wrapper, '下一步');
    const firstClick = nextButton.trigger('click');
    const secondClick = nextButton.trigger('click');
    await Promise.all([firstClick, secondClick]);
    expect(restartApiMock.apply).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(DATASOURCE_RESTART_SESSION_KEY)).not.toMatch(/password|jdbc|secret/i);
    expect(storageWrite.mock.invocationCallOrder[0]).toBeLessThan(
      restartApiMock.apply.mock.invocationCallOrder[0]
    );

    applyDeferred.resolve({ restartId: 'restart-1' });
    await flushPromises();
    expect(restartApiMock.apply).toHaveBeenCalledWith('restart-1', expect.objectContaining({
      host: 'db.example.local',
      edition: 'PERSONAL',
    }));
    expect(requestMock.put).not.toHaveBeenCalled();
    expect(monitorState.instances[0].start).toHaveBeenCalledTimes(1);
  });

  it('apply 响应丢失时保持原 ID 静默恢复轮询，不退回旧 PUT', async () => {
    restartApiMock.apply.mockRejectedValue(new TypeError('Failed to fetch'));
    const wrapper = await mountWizard();
    await openChangedDatabaseForm(wrapper);
    await modifyAndTest(wrapper);
    await clickButton(wrapper, '下一步');

    expect(readRestartSession(sessionStorage)).toMatchObject({ restartId: 'restart-1', step: 1 });
    expect(monitorState.instances[0].options.restartId).toBe('restart-1');
    expect(monitorState.instances[0].start).toHaveBeenCalledTimes(1);
    expect(requestMock.put).not.toHaveBeenCalled();
    expect(ElMessage.error).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('正在重启应用');
  });

  it('非受管环境保留旧 PUT 和手工重启提示，不创建恢复标记', async () => {
    restartApiMock.getCapability.mockResolvedValue({ supported: false, reasonCode: 'UNMANAGED' });
    const wrapper = await mountWizard();
    await openChangedDatabaseForm(wrapper);
    await modifyAndTest(wrapper);
    await clickButton(wrapper, '下一步');

    expect(requestMock.put).toHaveBeenCalledWith('/datasource/config', expect.objectContaining({
      host: 'db.example.local',
    }));
    expect(restartApiMock.apply).not.toHaveBeenCalled();
    expect(readRestartSession(sessionStorage)).toBeNull();
    expect(wrapper.text()).toContain('请重启应用，然后重新进入本向导继续初始化');
  });

  it('未修改参数时下一步直接前进，不保存也不重启（完整相已确认）', async () => {
    const wrapper = await mountWizard();
    await openChangedDatabaseForm(wrapper);
    await clickButton(wrapper, '下一步');

    expect(wrapper.text()).toContain('配置注册规范');
    expect(requestMock.put).not.toHaveBeenCalled();
    expect(restartApiMock.apply).not.toHaveBeenCalled();
    expect(restartApiMock.confirmCurrent).toHaveBeenCalledWith('PERSONAL');
  });

  it('向导相保持当前数据源：确认后经一次自动重启进入进度并恢复会话标记', async () => {
    restartApiMock.confirmCurrent.mockResolvedValue({ restartRequired: true, restartId: 'restart-9' });
    const wrapper = await mountWizard();
    await openChangedDatabaseForm(wrapper);
    await clickButton(wrapper, '下一步');

    expect(wrapper.text()).toContain('正在重启应用');
    expect(restartApiMock.apply).not.toHaveBeenCalled();
    expect(monitorState.instances[0].options.restartId).toBe('restart-9');
    expect(readRestartSession(sessionStorage)).toMatchObject({ restartId: 'restart-9', step: 1 });
    wrapper.unmount();
  });

  it('测试通过后再次修改参数会使测试失效并阻止保存', async () => {
    const wrapper = await mountWizard();
    await openChangedDatabaseForm(wrapper);
    await modifyAndTest(wrapper);
    await wrapper.find('input[placeholder="localhost"]').setValue('changed.after.test');
    await clickButton(wrapper, '下一步');

    expect(ElMessage.error).toHaveBeenCalledWith('请先测试连接，确认新数据源可用后再继续');
    expect(requestMock.put).not.toHaveBeenCalled();
    expect(restartApiMock.apply).not.toHaveBeenCalled();
  });

  it('刷新恢复原 ID 和数据库步骤，刷新与路由离开均不弹放弃确认或 abort', async () => {
    writeRestartSession({ restartId: 'restart-1', edition: 'PERSONAL', step: 1, startedAt: 1000 });
    const wrapper = await mountWizard();

    expect(wrapper.text()).toContain('配置数据库');
    expect(monitorState.instances[0].options.restartId).toBe('restart-1');
    expect(monitorState.instances[0].start).toHaveBeenCalledTimes(1);
    const beforeUnload = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(beforeUnload);
    expect(beforeUnload.defaultPrevented).toBe(false);
    await expect(routeLeaveGuards[0]()).resolves.toBe(false);
    expect(ElMessageBox.confirm).not.toHaveBeenCalled();
    expect(requestMock.post).not.toHaveBeenCalledWith('/init-wizard/abort');
  });

  it('卸载后忽略迟到的轮询回调，不重载状态或导航', async () => {
    writeRestartSession({ restartId: 'restart-1', edition: 'PERSONAL', step: 1, startedAt: 1000 });
    const wrapper = await mountWizard();
    const instance = monitorState.instances[0];
    wrapper.unmount();

    instance.options.onUpdate(snapshot('SUCCEEDED', 'SUCCEEDED', 'MATCHED'));
    await flushPromises();

    expect(instance.stop).toHaveBeenCalledTimes(1);
    expect(authMock.refreshInitialized).not.toHaveBeenCalled();
    expect(routerPushMock).not.toHaveBeenCalled();
  });

  it('成功且目标未初始化时重载实际数据源和 init 状态，回到数据库步骤', async () => {
    requestMock.get.mockResolvedValue({
      ...currentDb,
      vendor: 'sqlite',
      jdbcUrl: 'jdbc:sqlite:/new.db',
      filePath: '/new.db',
    });
    writeRestartSession({ restartId: 'restart-1', edition: 'PERSONAL', step: 1, startedAt: 1000 });
    const wrapper = await mountWizard();

    monitorState.instances[0].options.onUpdate(snapshot('SUCCEEDED', 'SUCCEEDED', 'MATCHED'));
    await flushPromises();

    expect(authMock.refreshInitialized).toHaveBeenCalled();
    expect(readRestartSession(sessionStorage)).toBeNull();
    expect(wrapper.text()).toContain('新数据库设置已生效');
    expect(wrapper.text()).toContain('jdbc:sqlite:/new.db');
    expect(wrapper.text()).toContain('配置数据库');
    expect(routerPushMock).not.toHaveBeenCalled();
  });

  it('成功且目标已初始化时进入正常应用，不放弃目标数据', async () => {
    authState.initialized = true;
    writeRestartSession({ restartId: 'restart-1', edition: 'PERSONAL', step: 1, startedAt: 1000 });
    await mountWizard();

    monitorState.instances[0].options.onUpdate(snapshot('SUCCEEDED', 'SUCCEEDED', 'MATCHED'));
    await flushPromises();

    expect(authMock.refreshInitialized).toHaveBeenCalled();
    expect(routerPushMock).toHaveBeenCalledWith('/dashboard');
    expect(readRestartSession(sessionStorage)).toBeNull();
    expect(requestMock.post).not.toHaveBeenCalledWith('/init-wizard/abort');
  });

  it('ROLLED_BACK 清理标记、重载旧配置并返回可编辑表单', async () => {
    writeRestartSession({ restartId: 'restart-1', edition: 'PERSONAL', step: 1, startedAt: 1000 });
    const wrapper = await mountWizard();

    monitorState.instances[0].options.onUpdate(
      snapshot('ROLLED_BACK', 'ROLLED_BACK', 'NOT_REQUIRED', 'TARGET_MISMATCHED')
    );
    await flushPromises();

    expect(readRestartSession(sessionStorage)).toBeNull();
    expect(wrapper.text()).toContain('新数据库设置未能应用，已恢复原设置');
    expect(wrapper.find('input[placeholder="localhost"]').exists()).toBe(true);
    expect(wrapper.find('input[placeholder="localhost"]').attributes('disabled')).toBeUndefined();
    expect(requestMock.get).toHaveBeenCalledWith('/datasource/config');
  });

  it('RECOVERY_FAILED 提供重试连接，重试沿用原 ID 并重置等待窗口', async () => {
    writeRestartSession({ restartId: 'restart-1', edition: 'PERSONAL', step: 1, startedAt: 1000 });
    const wrapper = await mountWizard();
    const instance = monitorState.instances[0];
    instance.options.onUpdate(
      snapshot('FAILED', 'RECOVERY_FAILED', 'NOT_REQUIRED', 'OLD_CONFIG_START_FAILED')
    );
    await flushPromises();
    // 事务可能仍在后端进行中：必须保留标记，否则刷新后再提交会撞上 409。
    expect(readRestartSession(sessionStorage)).toMatchObject({ restartId: 'restart-1', step: 1 });

    await clickButton(wrapper, '重试连接');

    expect(instance.retry).toHaveBeenCalledTimes(1);
    expect(instance.retry.mock.calls[0][0]).toEqual(expect.any(Number));
    expect(readRestartSession(sessionStorage)).toMatchObject({ restartId: 'restart-1', step: 1 });
  });

  it('事务不存在时回到可编辑表单并提示设置未提交到应用', async () => {
    writeRestartSession({ restartId: 'restart-1', edition: 'PERSONAL', step: 1, startedAt: 1000 });
    const wrapper = await mountWizard();
    monitorState.instances[0].options.onUpdate(
      snapshot('FAILED', 'PENDING', 'PENDING', 'RESTART_NOT_FOUND')
    );
    await flushPromises();

    expect(readRestartSession(sessionStorage)).toBeNull();
    expect(wrapper.text()).toContain('设置未提交到应用');
    expect(wrapper.find('input[placeholder="localhost"]').exists()).toBe(true);
    expect(wrapper.find('input[placeholder="localhost"]').attributes('disabled')).toBeUndefined();
  });

  it('初始化状态探测失败时进入失败态，不误跳首页也不标记已初始化', async () => {
    authMock.refreshInitialized.mockResolvedValueOnce(null);
    writeRestartSession({ restartId: 'restart-1', edition: 'PERSONAL', step: 1, startedAt: 1000 });
    const wrapper = await mountWizard();
    monitorState.instances[0].options.onUpdate(snapshot('SUCCEEDED', 'SUCCEEDED', 'MATCHED'));
    await flushPromises();

    expect(routerPushMock).not.toHaveBeenCalled();
    expect(authMock.setInitialized).not.toHaveBeenCalledWith(true);
    expect(wrapper.text()).toContain('暂时无法完成数据库设置');
  });
});
