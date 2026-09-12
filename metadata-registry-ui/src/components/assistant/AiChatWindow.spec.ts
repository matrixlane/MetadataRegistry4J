import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia, type Pinia } from 'pinia';
import ElementPlus from 'element-plus';
import type { VueWrapper } from '@vue/test-utils';
import AiChatWindow from './AiChatWindow.vue';
import { useAiChatStore } from '@/stores/aiChat';

const mocks = vi.hoisted(() => ({
  createJob: vi.fn(),
  cancelJob: vi.fn(),
  poll: vi.fn(),
}));

vi.mock('@/api/assistant', () => ({
  assistantApi: {
    tasks: vi.fn(),
    listDebugExchanges: vi.fn(),
    createJob: mocks.createJob,
    getJob: vi.fn(),
    cancelJob: mocks.cancelJob,
  },
  pollAssistantJob: mocks.poll,
}));

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: any) => (params ? `${key} ${JSON.stringify(params)}` : key),
  }),
}));

let pinia: Pinia;
let chat: ReturnType<typeof useAiChatStore>;

const mounted: VueWrapper[] = [];

afterEach(() => {
  while (mounted.length) {
    mounted.pop()!.unmount();
  }
});

function body(): HTMLElement {
  return document.body;
}

async function mountWindow() {
  const wrapper = mount(AiChatWindow, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus, pinia],
      mocks: {
        $t: (key: string, params?: any) => (params ? `${key} ${JSON.stringify(params)}` : key),
      },
    },
  });
  mounted.push(wrapper);
  await flushPromises();
  return wrapper;
}

function typeInput(value: string) {
  const el = body().querySelector('[data-test="ai-chat-input"]') as HTMLTextAreaElement;
  el.value = value;
  el.dispatchEvent(new Event('input'));
}

function pressEnter() {
  const el = body().querySelector('[data-test="ai-chat-input"]') as HTMLTextAreaElement;
  el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
}

beforeEach(() => {
  vi.clearAllMocks();
  pinia = createPinia();
  setActivePinia(pinia);
  chat = useAiChatStore();
  chat.open = true;
  chat.quote = { text: '患者年龄在就诊当日按周岁计算。', surroundingText: '患者年龄…', itemId: 'item-1' };
  mocks.createJob.mockResolvedValue({ jobId: 'job-1' });
  mocks.poll.mockResolvedValue({
    id: 'job-1', taskId: 'selection-chat', status: 'SUCCEEDED',
    result: '这是回答', degraded: true,
  });
});

describe('AiChatWindow 引用与渲染', () => {
  it('开窗显示引用块，可移除引用', async () => {
    await mountWindow();
    expect(body().querySelector('[data-test="ai-chat-quote"]')!.textContent)
      .toContain('患者年龄在就诊当日按周岁计算。');
    (body().querySelector('[data-test="ai-chat-quote-remove"]') as HTMLElement).click();
    await flushPromises();
    expect(body().querySelector('[data-test="ai-chat-quote"]')).toBeNull();
    expect(chat.quote).toBeNull();
  });

  it('消息以 user/assistant 气泡纯文本展示', async () => {
    chat.messages = [
      { role: 'user', content: '问题一' },
      { role: 'assistant', content: '回答一' },
    ];
    await mountWindow();
    expect(body().querySelector('[data-test="ai-chat-message-user"]')!.textContent).toContain('问题一');
    expect(body().querySelector('[data-test="ai-chat-message-assistant"] pre')!.textContent).toContain('回答一');
  });
});

describe('AiChatWindow 收发', () => {
  it('Enter 发送 selection-chat 作业：引用+问题+历史入参，成功后 assistant 消息带降级标注', async () => {
    chat.messages = [
      { role: 'user', content: '问题一' },
      { role: 'assistant', content: '回答一' },
    ];
    await mountWindow();
    typeInput('问题二');
    pressEnter();
    await flushPromises();

    expect(mocks.createJob).toHaveBeenCalledWith(expect.objectContaining({
      taskId: 'selection-chat',
      itemId: 'item-1',
      selection: expect.objectContaining({ text: '患者年龄在就诊当日按周岁计算。' }),
      params: expect.objectContaining({
        question: '问题二',
        history: [{ question: '问题一', answer: '回答一' }],
      }),
    }));
    const assistantMsgs = body().querySelectorAll('[data-test="ai-chat-message-assistant"]');
    expect(assistantMsgs.length).toBe(2);
    expect(assistantMsgs[1].textContent).toContain('这是回答');
    expect(assistantMsgs[1].querySelector('[data-test="ai-chat-degraded"]')).toBeTruthy();
  });

  it('失败内联错误并可重试同一问题', async () => {
    mocks.poll.mockResolvedValue({
      id: 'job-1', taskId: 'selection-chat', status: 'FAILED', error: 'CLI timeout', degraded: false,
    });
    await mountWindow();
    typeInput('我的问题');
    pressEnter();
    await flushPromises();
    const errorMsg = body().querySelector('[data-test="ai-chat-message-assistant"]')!;
    expect(errorMsg.textContent).toContain('CLI timeout');

    mocks.poll.mockResolvedValue({
      id: 'job-2', taskId: 'selection-chat', status: 'SUCCEEDED', result: '重试成功', degraded: false,
    });
    mocks.createJob.mockResolvedValue({ jobId: 'job-2' });
    (body().querySelector('[data-test="ai-chat-retry"]') as HTMLElement).click();
    await flushPromises();
    expect(mocks.createJob).toHaveBeenLastCalledWith(expect.objectContaining({
      params: expect.objectContaining({ question: '我的问题' }),
    }));
    const last = body().querySelectorAll('[data-test="ai-chat-message-assistant"]');
    expect(last[last.length - 1].textContent).toContain('重试成功');
  });

  it('生成中显示取消按钮并取消作业', async () => {
    mocks.poll.mockReturnValue(new Promise(() => {}));
    await mountWindow();
    typeInput('问题');
    pressEnter();
    await flushPromises();
    expect(chat.sending).toBe(true);
    const cancel = body().querySelector('[data-test="ai-chat-cancel"]') as HTMLElement;
    expect(cancel).toBeTruthy();
    cancel.click();
    await flushPromises();
    expect(mocks.cancelJob).toHaveBeenCalledWith('job-1');
  });

  it('复制 assistant 消息写剪贴板', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    chat.messages = [{ role: 'assistant', content: '回答一' }];
    await mountWindow();
    (body().querySelector('[data-test="ai-chat-copy-0"]') as HTMLElement).click();
    await flushPromises();
    expect(writeText).toHaveBeenCalledWith('回答一');
  });
});
