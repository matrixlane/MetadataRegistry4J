import { defineStore } from 'pinia';
import { ref } from 'vue';

export interface ChatQuote {
  field?: string;
  text: string;
  surroundingText?: string;
  itemId?: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  error?: boolean;
  degraded?: boolean;
  /** 失败消息重试时复用的提问文本 */
  question?: string;
}

/** 浮动 AI 对话窗状态：全局单例，AppLayout 挂载窗口组件，划词按钮与窗口共用。 */
export const useAiChatStore = defineStore('aiChat', () => {
  const open = ref(false);
  const quote = ref<ChatQuote | null>(null);
  const messages = ref<ChatMessage[]>([]);
  /** 正在执行的 selection-chat 作业 id（用于取消） */
  const pendingJobId = ref<string | null>(null);
  const sending = ref(false);

  function openWithQuote(payload: ChatQuote) {
    quote.value = payload;
    open.value = true;
  }

  function setQuote(payload: ChatQuote | null) {
    quote.value = payload;
  }

  function close() {
    open.value = false;
  }

  function addUserMessage(content: string) {
    messages.value.push({ role: 'user', content });
  }

  function addAssistantMessage(content: string, degraded: boolean) {
    messages.value.push({ role: 'assistant', content, degraded });
  }

  function addErrorMessage(question: string, error: string) {
    messages.value.push({ role: 'assistant', content: error, error: true, question });
  }

  /** 移除最后一条失败消息（重试前调用，避免重复错误气泡） */
  function removeLastError() {
    const last = messages.value[messages.value.length - 1];
    if (last?.error) messages.value.pop();
  }

  function clear() {
    messages.value = [];
    quote.value = null;
  }

  return {
    open, quote, messages, pendingJobId, sending,
    openWithQuote, setQuote, close,
    addUserMessage, addAssistantMessage, addErrorMessage, removeLastError, clear,
  };
});
