import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import ElementPlus from 'element-plus';
import type { VueWrapper } from '@vue/test-utils';
import AiDebugDrawer from './AiDebugDrawer.vue';
import {
  clampPanelWidth,
  defaultPanelWidth,
  AI_DEBUG_WIDTH_STORAGE_KEY,
} from '@/utils/panelWidth';
import type { DebugExchangeResponse } from '@/api/assistant';

const mocks = vi.hoisted(() => ({
  listDebugExchanges: vi.fn(),
  tasks: vi.fn(),
}));

vi.mock('@/api/assistant', () => ({
  assistantApi: {
    tasks: mocks.tasks,
    listDebugExchanges: mocks.listDebugExchanges,
  },
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: any) => (params ? `${key} ${JSON.stringify(params)}` : key),
  }),
}));

const TASKS = [
  {
    id: 'complete-metadata',
    displayName: '补全元数据',
    inputSpec: { itemIdRequired: true, selection: 'NONE', params: ['fields'] },
    outputKind: 'CANDIDATES',
  },
  {
    id: 'explain-selection',
    displayName: '解释所选文本',
    inputSpec: { itemIdRequired: false, selection: 'REQUIRED', params: ['question'] },
    outputKind: 'TEXT',
  },
];

const EXCHANGES: DebugExchangeResponse[] = [
  {
    jobId: 'job-2',
    taskId: 'explain-selection',
    itemId: 'item-9',
    selection: { field: 'definition', text: '患者年龄' },
    providerId: 'claude',
    providerDisplayName: 'Claude Code',
    status: 'FAILED',
    prompt: '请解释所选文本',
    rawOutput: '',
    error: 'CLI timeout',
    createdAt: '2026-09-08T10:00:02Z',
    finishedAt: '2026-09-08T10:00:20Z',
  },
  {
    jobId: 'job-1',
    taskId: 'complete-metadata',
    itemId: 'item-1',
    providerId: 'codex',
    providerDisplayName: 'Codex',
    status: 'SUCCEEDED',
    prompt: '请补全元数据',
    rawOutput: '建议：名称=患者年龄；定义=…',
    createdAt: '2026-09-08T10:00:01Z',
    finishedAt: '2026-09-08T10:00:15Z',
  },
];

const mounted: VueWrapper[] = [];

afterEach(() => {
  while (mounted.length) {
    mounted.pop()!.unmount();
  }
  vi.useRealTimers();
});

function body(): HTMLElement {
  return document.body;
}

async function mountDrawer(props?: Record<string, unknown>) {
  const wrapper = mount(AiDebugDrawer, {
    props: { modelValue: false, ...props },
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      mocks: {
        $t: (key: string, params?: any) => (params ? `${key} ${JSON.stringify(params)}` : key),
      },
    },
  });
  mounted.push(wrapper);
  await wrapper.setProps({ modelValue: true });
  await flushPromises();
  await wrapper.vm.$nextTick();
  return wrapper;
}

beforeEach(() => {
  vi.clearAllMocks();
  mocks.listDebugExchanges.mockResolvedValue(EXCHANGES);
  mocks.tasks.mockResolvedValue(TASKS);
});

describe('AiDebugDrawer 列表渲染', () => {
  it('打开后加载最近交互：条目头含时间、任务显示名、Agent 显示名、状态', async () => {
    await mountDrawer();
    expect(mocks.listDebugExchanges).toHaveBeenCalledTimes(1);
    const items = body().querySelectorAll('[data-test="ai-debug-item"]');
    expect(items.length).toBe(2);
    // 任务显示名来自 tasks 端点映射
    const taskNames = Array.from(body().querySelectorAll('[data-test="ai-debug-task"]')).map((el) => el.textContent);
    expect(taskNames).toContain('解释所选文本');
    expect(taskNames).toContain('补全元数据');
    // Agent 显示名与状态
    expect(body().textContent).toContain('Claude Code');
    expect(body().textContent).toContain('Codex');
    const statuses = Array.from(body().querySelectorAll('[data-test="ai-debug-status"]')).map((el) => el.textContent);
    expect(statuses).toEqual(['FAILED', 'SUCCEEDED']);
    // 条数
    expect(body().querySelector('[data-test="ai-debug-count"]')!.textContent).toContain('aiDebug.count');
  });

  it('展开条目隐藏内部 itemId、显示所选文本、提示词与响应原文（纯文本 pre）', async () => {
    const wrapper = await mountDrawer();
    // el-collapse 展开第二项（成功项）；断言限定在该条目内（折叠条目内容仍在 DOM 中）
    const items = body().querySelectorAll('[data-test="ai-debug-item"]');
    const target = items[1] as HTMLElement;
    (target.querySelector('.el-collapse-item__header') as HTMLElement).click();
    await flushPromises();
    await wrapper.vm.$nextTick();
    // 界面禁显 UUID：itemId 以占位文案代替（测试环境 $t 回显 key）
    expect(target.querySelector('[data-test="ai-debug-item-id"]')!.textContent).toBe('common.internalIdHidden');
    const prompt = target.querySelector('[data-test="ai-debug-prompt"]');
    expect(prompt!.tagName).toBe('PRE');
    expect(prompt!.textContent).toContain('请补全元数据');
    expect(target.querySelector('[data-test="ai-debug-output"]')!.textContent).toContain('建议：名称=患者年龄');
    // 成功项不显示错误块
    expect(target.querySelector('[data-test="ai-debug-error-text"]')).toBeNull();
  });

  it('失败条目展示错误而非响应原文', async () => {
    const wrapper = await mountDrawer();
    const items = body().querySelectorAll('[data-test="ai-debug-item"]');
    const target = items[0] as HTMLElement;
    (target.querySelector('.el-collapse-item__header') as HTMLElement).click();
    await flushPromises();
    await wrapper.vm.$nextTick();
    const errorText = target.querySelector('[data-test="ai-debug-error-text"]');
    expect(errorText!.tagName).toBe('PRE');
    expect(errorText!.textContent).toContain('CLI timeout');
    expect(target.querySelector('[data-test="ai-debug-output"]')).toBeNull();
    // 所选文本块
    expect(target.querySelector('[data-test="ai-debug-selection"]')!.textContent).toContain('患者年龄');
  });

  it('任务清单加载失败时回落显示 taskId', async () => {
    mocks.tasks.mockRejectedValue(new Error('boom'));
    await mountDrawer();
    const taskNames = Array.from(body().querySelectorAll('[data-test="ai-debug-task"]')).map((el) => el.textContent);
    expect(taskNames).toContain('explain-selection');
    expect(taskNames).toContain('complete-metadata');
  });

  it('空列表显示空态提示', async () => {
    mocks.listDebugExchanges.mockResolvedValue([]);
    await mountDrawer();
    expect(body().querySelector('[data-test="ai-debug-empty"]')).toBeTruthy();
    expect(body().querySelector('[data-test="ai-debug-item"]')).toBeNull();
  });
});

describe('AiDebugDrawer 轮询生命周期', () => {
  it('打开期间每 4 秒自动刷新，关闭后停止', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    const wrapper = await mountDrawer();
    expect(mocks.listDebugExchanges).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(4000);
    await flushPromises();
    expect(mocks.listDebugExchanges).toHaveBeenCalledTimes(2);
    vi.advanceTimersByTime(4000);
    await flushPromises();
    expect(mocks.listDebugExchanges).toHaveBeenCalledTimes(3);
    // 关闭抽屉停止轮询
    await wrapper.setProps({ modelValue: false });
    await flushPromises();
    vi.advanceTimersByTime(12000);
    await flushPromises();
    expect(mocks.listDebugExchanges).toHaveBeenCalledTimes(3);
  });

  it('组件卸载时清理定时器', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    const wrapper = await mountDrawer();
    expect(mocks.listDebugExchanges).toHaveBeenCalledTimes(1);
    wrapper.unmount();
    vi.advanceTimersByTime(12000);
    expect(mocks.listDebugExchanges).toHaveBeenCalledTimes(1);
  });

  it('初次未打开时不加载、不轮询', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    const wrapper = mount(AiDebugDrawer, {
      props: { modelValue: false },
      attachTo: document.body,
      global: {
        plugins: [ElementPlus],
        mocks: { $t: (key: string) => key },
      },
    });
    mounted.push(wrapper);
    await flushPromises();
    vi.advanceTimersByTime(10000);
    await flushPromises();
    expect(mocks.listDebugExchanges).not.toHaveBeenCalled();
  });
});

describe('AiDebugDrawer 复制', () => {
  it('点击复制按钮将提示词原文写入剪贴板', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    });
    const wrapper = await mountDrawer();
    const items = body().querySelectorAll('[data-test="ai-debug-item"]');
    const target = items[1] as HTMLElement;
    (target.querySelector('.el-collapse-item__header') as HTMLElement).click();
    await flushPromises();
    await wrapper.vm.$nextTick();
    (target.querySelector('[data-test="ai-debug-copy-prompt"]') as HTMLElement).click();
    await flushPromises();
    expect(writeText).toHaveBeenCalledWith('请补全元数据');
  });
});

describe('AiDebugDrawer 非模态与默认宽度', () => {
  afterEach(() => {
    localStorage.removeItem(AI_DEBUG_WIDTH_STORAGE_KEY);
  });

  it('el-drawer 以非模态打开（modal=false），页面其它区域不被遮罩拦截', async () => {
    const wrapper = await mountDrawer();
    const drawer = wrapper.findComponent({ name: 'ElDrawer' });
    expect(drawer.exists()).toBe(true);
    expect(drawer.props('modal')).toBe(false);
  });

  it('首次打开默认宽度为视口 20vw（宽视口）', async () => {
    const original = window.innerWidth;
    Object.defineProperty(window, 'innerWidth', { value: 2400, configurable: true });
    try {
      const wrapper = await mountDrawer();
      expect((wrapper.vm as any).drawerWidth).toBe(480);
    } finally {
      Object.defineProperty(window, 'innerWidth', { value: original, configurable: true });
    }
  });

  it('默认宽度受 360px 下限约束（窄视口 20vw 不足时取下限）', async () => {
    const original = window.innerWidth;
    Object.defineProperty(window, 'innerWidth', { value: 1024, configurable: true });
    try {
      const wrapper = await mountDrawer();
      expect((wrapper.vm as any).drawerWidth).toBe(360);
      expect(defaultPanelWidth(1024)).toBe(360);
      expect(defaultPanelWidth(2400)).toBe(480);
    } finally {
      Object.defineProperty(window, 'innerWidth', { value: original, configurable: true });
    }
  });
});

describe('AiDebugDrawer 宽度拖拽调节', () => {
  afterEach(() => {
    localStorage.removeItem(AI_DEBUG_WIDTH_STORAGE_KEY);
  });

  it('clampPanelWidth 约束 360px ~ 90vw（视口过窄时不小于最小值）', () => {
    expect(clampPanelWidth(100, 1024)).toBe(360);
    expect(clampPanelWidth(500, 1024)).toBe(500);
    expect(clampPanelWidth(9999, 1024)).toBe(921);
    expect(clampPanelWidth(100, 300)).toBe(360);
  });

  it('拖拽左缘手柄实时调宽，松手后记住到 localStorage', async () => {
    const wrapper = await mountDrawer();
    const before = (wrapper.vm as any).drawerWidth as number;
    const handle = body().querySelector('[data-test="ai-debug-resize-handle"]') as HTMLElement;
    expect(handle).toBeTruthy();

    handle.dispatchEvent(new MouseEvent('mousedown', { clientX: 800, bubbles: true }));
    document.dispatchEvent(new MouseEvent('mousemove', { clientX: 700 }));
    await wrapper.vm.$nextTick();
    expect((wrapper.vm as any).drawerWidth).toBe(before + 100);

    document.dispatchEvent(new MouseEvent('mouseup'));
    expect(localStorage.getItem(AI_DEBUG_WIDTH_STORAGE_KEY)).toBe(String(before + 100));

    // 松手后继续 mousemove 不再生效（监听已移除）
    document.dispatchEvent(new MouseEvent('mousemove', { clientX: 600 }));
    await wrapper.vm.$nextTick();
    expect((wrapper.vm as any).drawerWidth).toBe(before + 100);
  });

  it('再次打开恢复 localStorage 记住的宽度', async () => {
    localStorage.setItem(AI_DEBUG_WIDTH_STORAGE_KEY, '720');
    const wrapper = await mountDrawer();
    expect((wrapper.vm as any).drawerWidth).toBe(720);
  });
});
