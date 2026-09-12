<template>
  <div
    v-if="chat.open"
    class="ai-chat-window"
    :style="{ left: `${pos.x}px`, top: `${pos.y}px` }"
    data-test="ai-chat-window"
  >
    <div
      class="ai-chat-header"
      data-test="ai-chat-header"
      @mousedown="startDrag"
    >
      <span class="ai-chat-title">{{ t('aiChat.title') }}</span>
      <div class="ai-chat-header-actions">
        <el-button size="small" text circle data-test="ai-chat-clear" @click="chat.clear()">
          <el-icon><Delete /></el-icon>
        </el-button>
        <el-button size="small" text circle data-test="ai-chat-close" @click="closeWindow">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </div>

    <div v-if="chat.quote" class="ai-chat-quote" data-test="ai-chat-quote">
      <pre class="ai-chat-quote-text">{{ chat.quote.text }}</pre>
      <el-button size="small" text circle data-test="ai-chat-quote-remove" @click="chat.setQuote(null)">
        <el-icon><Close /></el-icon>
      </el-button>
    </div>

    <div class="ai-chat-messages" data-test="ai-chat-messages">
      <el-empty
        v-if="!chat.messages.length"
        :description="t('aiChat.empty')"
        :image-size="60"
        data-test="ai-chat-empty"
      />
      <div
        v-for="(message, idx) in chat.messages"
        :key="idx"
        class="ai-chat-message"
        :class="message.role === 'user' ? 'is-user' : 'is-assistant'"
        :data-test="`ai-chat-message-${message.role}`"
      >
        <div class="ai-chat-bubble-row">
          <pre class="ai-chat-bubble">{{ message.content }}</pre>
          <el-button
            size="small"
            text
            circle
            class="ai-chat-copy"
            :data-test="`ai-chat-copy-${idx}`"
            @click="copyMessage(message.content)"
          >
            <el-icon><CopyDocument /></el-icon>
          </el-button>
        </div>
        <el-tag
          v-if="message.degraded"
          size="small"
          type="warning"
          effect="plain"
          class="ai-chat-degraded"
          data-test="ai-chat-degraded"
        >{{ t('aiChat.degraded') }}</el-tag>
        <template v-if="message.error">
          <el-button
            size="small"
            text
            type="primary"
            data-test="ai-chat-retry"
            @click="retry(message)"
          >{{ t('aiChat.retry') }}</el-button>
        </template>
      </div>
    </div>

    <div class="ai-chat-input-row">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        resize="none"
        :placeholder="t('aiChat.inputPlaceholder')"
        data-test="ai-chat-input"
        :disabled="chat.sending"
        @keydown.enter.exact.prevent="send"
      />
      <div class="ai-chat-send-col">
        <el-button
          v-if="chat.sending"
          size="small"
          data-test="ai-chat-cancel"
          @click="cancel"
        >{{ t('aiChat.cancel') }}</el-button>
        <el-button
          type="primary"
          size="small"
          :disabled="chat.sending || !input.trim()"
          data-test="ai-chat-send"
          @click="send"
        >{{ t('aiChat.send') }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue';
import { useI18n } from 'vue-i18n';
const { t } = useI18n();
import { ElMessage } from 'element-plus';
import { Delete, Close, CopyDocument } from '@element-plus/icons-vue';
import { useAiChatStore } from '@/stores/aiChat';
import { assistantApi, pollAssistantJob } from '@/api/assistant';
import type { ChatHistoryEntry } from '@/api/assistant';

const chat = useAiChatStore();

const input = ref('');
const pos = ref({ x: 120, y: 120 });
const messagesEl = ref<HTMLElement>();

// ── 标题栏拖拽 ────────────────────────────────────────────────
let dragOffset: { dx: number; dy: number } | null = null;

function startDrag(event: MouseEvent) {
  // 点击按钮时不启动拖拽
  if ((event.target as HTMLElement).closest('button')) return;
  dragOffset = { dx: event.clientX - pos.value.x, dy: event.clientY - pos.value.y };
  document.addEventListener('mousemove', onDrag);
  document.addEventListener('mouseup', stopDrag);
}

function onDrag(event: MouseEvent) {
  if (!dragOffset) return;
  pos.value = {
    x: Math.max(0, event.clientX - dragOffset.dx),
    y: Math.max(0, event.clientY - dragOffset.dy),
  };
}

function stopDrag() {
  dragOffset = null;
  document.removeEventListener('mousemove', onDrag);
  document.removeEventListener('mouseup', stopDrag);
}

// ── 收发（selection-chat 单发作业；历史随请求重注入）────────────
function recentHistory(): ChatHistoryEntry[] {
  const history: ChatHistoryEntry[] = [];
  const list = chat.messages;
  for (let i = 0; i + 1 < list.length; i += 1) {
    const q = list[i];
    const a = list[i + 1];
    if (q.role === 'user' && a.role === 'assistant' && !a.error) {
      history.push({ question: q.content, answer: a.content });
    }
  }
  return history.slice(-10);
}

async function send() {
  const question = input.value.trim();
  if (!question || chat.sending) return;
  await ask(question);
}

async function ask(question: string) {
  chat.addUserMessage(question);
  input.value = '';
  chat.sending = true;
  scrollToBottom();
  try {
    const { jobId } = await assistantApi.createJob({
      taskId: 'selection-chat',
      itemId: chat.quote?.itemId,
      selection: chat.quote
        ? {
            field: chat.quote.field,
            text: chat.quote.text.slice(0, 2000),
            surroundingText: chat.quote.surroundingText?.slice(0, 2000),
          }
        : undefined,
      params: { question, history: recentHistory() },
    });
    chat.pendingJobId = jobId;
    const job = await pollAssistantJob(jobId);
    chat.pendingJobId = null;
    if (job.status === 'SUCCEEDED') {
      chat.addAssistantMessage(typeof job.result === 'string' ? job.result : String(job.result ?? ''), !!job.degraded);
    } else if (job.status === 'CANCELLED') {
      // 用户已取消：移除占位不需要，错误消息也不展示
      chat.messages.pop(); // 撤回用户消息
    } else {
      chat.addErrorMessage(question, job.error || t('aiChat.failed'));
    }
  } catch (err: any) {
    chat.pendingJobId = null;
    chat.addErrorMessage(question, err?.message || t('aiChat.failed'));
  } finally {
    chat.sending = false;
    scrollToBottom();
  }
}

async function retry(message: { question?: string }) {
  if (!message.question || chat.sending) return;
  chat.removeLastError();
  await ask(message.question);
}

async function cancel() {
  const jobId = chat.pendingJobId;
  if (jobId) {
    try {
      await assistantApi.cancelJob(jobId);
    } catch {
      // 拦截器已提示
    }
  }
}

function closeWindow() {
  if (chat.sending) void cancel();
  chat.close();
}

async function copyMessage(content: string) {
  try {
    await navigator.clipboard.writeText(content);
    ElMessage.success(t('aiChat.copied'));
  } catch {
    ElMessage.error(t('aiChat.copyFailed'));
  }
}

async function scrollToBottom() {
  await nextTick();
  const el = document.querySelector('.ai-chat-messages');
  if (el) el.scrollTop = el.scrollHeight;
}
</script>

<style scoped>
.ai-chat-window {
  position: fixed;
  width: 400px;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  box-shadow: var(--el-box-shadow);
  overflow: hidden;
}

.ai-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  cursor: move;
  user-select: none;
}

.ai-chat-title {
  font-size: 14px;
  font-weight: 600;
}

.ai-chat-header-actions {
  display: flex;
  gap: 4px;
}

.ai-chat-quote {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin: 8px 12px 0;
  padding: 6px 10px;
  background: var(--el-color-primary-light-9);
  border-left: 3px solid var(--el-color-primary);
  border-radius: 4px;
}

.ai-chat-quote-text {
  flex: 1;
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 72px;
  overflow: auto;
}

.ai-chat-messages {
  flex: 1;
  min-height: 200px;
  max-height: 320px;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-chat-message {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ai-chat-message.is-user {
  align-items: flex-end;
}

.ai-chat-message.is-assistant {
  align-items: flex-start;
}

.ai-chat-bubble-row {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  max-width: 100%;
}

.ai-chat-bubble {
  margin: 0;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-width: 320px;
}

.is-user .ai-chat-bubble {
  background: var(--el-color-primary-light-8);
  color: var(--el-text-color-primary);
}

.is-assistant .ai-chat-bubble {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.is-assistant .ai-chat-bubble-row .ai-chat-copy {
  visibility: visible;
}

.ai-chat-copy {
  flex-shrink: 0;
}

.ai-chat-degraded {
  align-self: flex-start;
}

.ai-chat-input-row {
  display: flex;
  gap: 8px;
  padding: 8px 12px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.ai-chat-send-col {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 6px;
}
</style>
