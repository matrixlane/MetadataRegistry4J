<template>
  <el-drawer
    :model-value="modelValue"
    :title="$t('aiDebug.title')"
    :size="`${drawerWidth}px`"
    :modal="false"
    modal-class="ai-debug-nonmodal-overlay"
    data-test="ai-debug-drawer"
    @update:model-value="onVisibleChange"
  >
    <div class="ai-debug-body">
      <div
        class="ai-debug-resize-handle"
        data-test="ai-debug-resize-handle"
        title="拖拽调节宽度"
        @mousedown="startResize"
      ></div>
      <div class="ai-debug-toolbar" data-test="ai-debug-toolbar">
      <span class="ai-debug-count" data-test="ai-debug-count">{{ $t('aiDebug.count', { count: exchanges.length }) }}</span>
      <span class="ai-debug-hint">{{ $t('aiDebug.autoRefreshHint') }}</span>
      <el-button
        size="small"
        :loading="loading"
        data-test="ai-debug-refresh"
        @click="load"
      >
        {{ $t('aiDebug.refresh') }}
      </el-button>
    </div>

    <el-alert
      v-if="loadFailed"
      :title="loadFailed"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: 12px;"
      data-test="ai-debug-load-failed"
    />

    <el-empty
      v-if="!loading && !loadFailed && exchanges.length === 0"
      :description="$t('aiDebug.empty')"
      data-test="ai-debug-empty"
    />

    <el-collapse v-else accordion data-test="ai-debug-list">
      <el-collapse-item
        v-for="exchange in exchanges"
        :key="exchange.jobId"
        :name="exchange.jobId"
        data-test="ai-debug-item"
      >
        <template #title>
          <div class="ai-debug-item-head">
            <span class="ai-debug-time" data-test="ai-debug-time">{{ formatTime(exchange.createdAt) }}</span>
            <span class="ai-debug-task" data-test="ai-debug-task">{{ taskName(exchange.taskId) }}</span>
            <span class="ai-debug-agent" data-test="ai-debug-agent">{{ agentName(exchange) }}</span>
            <el-tag
              size="small"
              :type="statusTagType(exchange.status)"
              data-test="ai-debug-status"
            >{{ exchange.status }}</el-tag>
          </div>
        </template>

        <div class="ai-debug-detail">
          <div v-if="exchange.itemId" class="ai-debug-row">
            <span class="ai-debug-label">{{ $t('aiDebug.itemId') }}</span>
            <span class="ai-debug-value" data-test="ai-debug-item-id">{{ exchange.itemId }}</span>
          </div>

          <div v-if="exchange.selection?.text" class="ai-debug-block">
            <div class="ai-debug-block-head">
              <span class="ai-debug-label">{{ $t('aiDebug.selection') }}</span>
              <el-button text size="small" data-test="ai-debug-copy-selection" @click.stop="copyText(exchange.selection!.text!)">
                {{ $t('aiDebug.copy') }}
              </el-button>
            </div>
            <pre class="ai-debug-pre" data-test="ai-debug-selection">{{ exchange.selection.text }}</pre>
          </div>

          <div v-if="exchange.prompt" class="ai-debug-block">
            <div class="ai-debug-block-head">
              <span class="ai-debug-label">{{ $t('aiDebug.prompt') }}</span>
              <el-button text size="small" data-test="ai-debug-copy-prompt" @click.stop="copyText(exchange.prompt!)">
                {{ $t('aiDebug.copy') }}
              </el-button>
            </div>
            <pre class="ai-debug-pre" data-test="ai-debug-prompt">{{ exchange.prompt }}</pre>
          </div>

          <div v-if="isFailed(exchange)" class="ai-debug-block">
            <div class="ai-debug-block-head">
              <span class="ai-debug-label">{{ $t('aiDebug.error') }}</span>
              <el-button text size="small" data-test="ai-debug-copy-output" @click.stop="copyText(errorText(exchange))">
                {{ $t('aiDebug.copy') }}
              </el-button>
            </div>
            <pre class="ai-debug-pre ai-debug-error" data-test="ai-debug-error-text">{{ errorText(exchange) }}</pre>
          </div>
          <div v-else-if="exchange.rawOutput" class="ai-debug-block">
            <div class="ai-debug-block-head">
              <span class="ai-debug-label">{{ $t('aiDebug.output') }}</span>
              <el-button text size="small" data-test="ai-debug-copy-output" @click.stop="copyText(exchange.rawOutput!)">
                {{ $t('aiDebug.copy') }}
              </el-button>
            </div>
            <pre class="ai-debug-pre" data-test="ai-debug-output">{{ exchange.rawOutput }}</pre>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue';
import { useI18n } from 'vue-i18n';
import { ElMessage } from 'element-plus';
import { assistantApi } from '@/api/assistant';
import type { DebugExchangeResponse } from '@/api/assistant';
import {
  clampPanelWidth,
  defaultPanelWidth,
  loadStoredPanelWidth,
  storePanelWidth,
} from '@/utils/panelWidth';

const props = defineProps<{
  modelValue: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
}>();

const { t } = useI18n();

const exchanges = ref<DebugExchangeResponse[]>([]);
const loading = ref(false);
const loadFailed = ref('');
const taskDisplayNames = ref<Record<string, string>>({});

const POLL_INTERVAL_MS = 4000;
let pollTimer: ReturnType<typeof setInterval> | null = null;

// ── 宽度拖拽调节（记住到 localStorage，范围 360px ~ 90vw；默认 20vw）────────
const drawerWidth = ref(
  clampPanelWidth(loadStoredPanelWidth() ?? defaultPanelWidth(window.innerWidth), window.innerWidth),
);

let resizeStart: { x: number; width: number } | null = null;

function startResize(event: MouseEvent) {
  event.preventDefault();
  resizeStart = { x: event.clientX, width: drawerWidth.value };
  document.body.style.userSelect = 'none';
  document.addEventListener('mousemove', onResizeMove);
  document.addEventListener('mouseup', stopResize);
}

function onResizeMove(event: MouseEvent) {
  if (!resizeStart) return;
  // 抽屉锚定右侧：向左拖（clientX 变小）增宽
  const next = resizeStart.width + (resizeStart.x - event.clientX);
  drawerWidth.value = clampPanelWidth(next, window.innerWidth);
}

function stopResize() {
  if (!resizeStart) return;
  resizeStart = null;
  document.body.style.userSelect = '';
  document.removeEventListener('mousemove', onResizeMove);
  document.removeEventListener('mouseup', stopResize);
  storePanelWidth(drawerWidth.value);
}

onBeforeUnmount(stopResize);

function stopPolling() {
  if (pollTimer !== null) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

async function load() {
  loading.value = true;
  try {
    exchanges.value = await assistantApi.listDebugExchanges();
    loadFailed.value = '';
  } catch (err: any) {
    loadFailed.value = err?.message || t('aiDebug.loadFailed');
  } finally {
    loading.value = false;
  }
}

async function loadTaskNames() {
  try {
    const tasks = await assistantApi.tasks();
    taskDisplayNames.value = Object.fromEntries(tasks.map((task) => [task.id, task.displayName]));
  } catch {
    // 任务清单不可用时回落显示 taskId
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      void load();
      void loadTaskNames();
      stopPolling();
      pollTimer = setInterval(() => void load(), POLL_INTERVAL_MS);
    } else {
      stopPolling();
    }
  },
  { immediate: true },
);

onBeforeUnmount(stopPolling);

function onVisibleChange(value: boolean) {
  emit('update:modelValue', value);
}

function taskName(taskId: string): string {
  return taskDisplayNames.value[taskId] || taskId;
}

function agentName(exchange: DebugExchangeResponse): string {
  return exchange.providerDisplayName || exchange.providerId || '-';
}

function isFailed(exchange: DebugExchangeResponse): boolean {
  return exchange.status === 'FAILED';
}

function errorText(exchange: DebugExchangeResponse): string {
  return exchange.error || exchange.rawOutput || '';
}

function statusTagType(status: DebugExchangeResponse['status']): 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case 'SUCCEEDED':
      return 'success';
    case 'FAILED':
      return 'danger';
    case 'RUNNING':
      return 'warning';
    default:
      return 'info';
  }
}

function formatTime(iso?: string): string {
  if (!iso) return '-';
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString();
}

async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success(t('aiDebug.copied'));
  } catch {
    ElMessage.error(t('aiDebug.copyFailed'));
  }
}
</script>

<style scoped lang="scss">
.ai-debug-body {
  position: relative;
  height: 100%;
}

.ai-debug-resize-handle {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6px;
  margin-left: -3px;
  cursor: col-resize;
  z-index: 10;
  border-radius: 3px;
}

.ai-debug-resize-handle:hover {
  background: var(--el-color-primary-light-7);
}

.ai-debug-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.ai-debug-count {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.ai-debug-hint {
  flex: 1;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ai-debug-item-head {
  display: flex;
  align-items: center;
  gap: 12px;
  overflow: hidden;
}

.ai-debug-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.ai-debug-task {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-debug-agent {
  font-size: 12px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.ai-debug-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-debug-row {
  display: flex;
  gap: 8px;
  font-size: 13px;
}

.ai-debug-label {
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.ai-debug-value {
  color: var(--el-text-color-regular);
  word-break: break-all;
}

.ai-debug-block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.ai-debug-pre {
  margin: 0;
  padding: 8px 12px;
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.6;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.ai-debug-error {
  color: var(--el-color-danger);
}
</style>

<!-- 非模态遮罩：EP 在 modal=false 时仍渲染 fixed inset 0 的包裹层，需放行指针事件 -->
<style lang="scss">
.ai-debug-nonmodal-overlay {
  pointer-events: none;
}

.ai-debug-nonmodal-overlay .el-drawer {
  pointer-events: auto;
}
</style>
