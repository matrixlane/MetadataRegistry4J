<template>
  <!-- 纯受控：只传 :visible、不传 update:visible 监听 → ElTooltip hasUpdateHandler=false、
       controlled=true，其内置触发行为（虚拟锚定的 hover/click 监听、click-outside、
       afterLeave 补发 update:visible）全部禁用，避免生成中被外部强关导致 cacheKey 丢失；
       关闭完全由本组件自管理（见 document click 监听与 scheduleClose） -->
  <el-popover
    :visible="visible"
    :virtual-ref="anchor"
    virtual-triggering
    placement="right-start"
    :width="360"
    popper-class="ai-candidate-popper"
    data-test="ai-candidate-popover"
  >
    <div
      ref="panelEl"
      class="ai-candidate-panel"
      @mouseenter="onPopperHover(true)"
      @mouseleave="onPopperHover(false)"
    >
      <div class="ai-candidate-head">
        <span class="ai-candidate-title">{{ t('aiGenerate.title') }}</span>
        <el-button
          v-if="phase === 'loading'"
          size="small"
          text
          data-test="ai-candidate-cancel"
          @click="cancel"
        >{{ t('aiGenerate.cancel') }}</el-button>
        <el-button
          v-else
          size="small"
          text
          circle
          data-test="ai-candidate-close"
          @click="emit('close')"
        >
          <el-icon><Close /></el-icon>
        </el-button>
      </div>

      <div v-if="phase === 'loading'" class="ai-candidate-loading" data-test="ai-candidate-loading">
        <el-icon class="is-loading" :size="18"><Loading /></el-icon>
        <span>{{ t('aiGenerate.running') }}</span>
      </div>

      <template v-else-if="phase === 'error'">
        <el-alert
          :title="errorText"
          type="error"
          :closable="false"
          show-icon
          data-test="ai-candidate-error"
        />
        <div style="margin-top: 8px;">
          <el-button size="small" type="primary" data-test="ai-candidate-retry" @click="start">
            {{ t('aiGenerate.retry') }}
          </el-button>
          <el-button size="small" text data-test="ai-candidate-close-error" @click="emit('close')">
            {{ t('aiGenerate.close') }}
          </el-button>
        </div>
      </template>

      <template v-else>
        <el-empty
          v-if="!candidates.length"
          :description="t('aiGenerate.empty')"
          :image-size="60"
          data-test="ai-candidate-empty"
        />
        <div v-else class="ai-candidate-list" data-test="ai-candidate-list">
          <div
            v-for="(candidate, idx) in candidates"
            :key="idx"
            class="ai-candidate-item"
            :data-test="`ai-candidate-item-${idx}`"
            @click="apply(candidate)"
          >
            <pre class="ai-candidate-text">{{ candidate.text }}</pre>
          </div>
        </div>
      </template>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue';
import { useI18n } from 'vue-i18n';
const { t } = useI18n();
import { Loading, Close } from '@element-plus/icons-vue';
import { assistantApi, pollAssistantJob } from '@/api/assistant';
import type { CreateAssistantJobPayload } from '@/api/assistant';

export interface FieldCandidate {
  text: string;
  language?: string;
  context?: string;
}

/** 新建模式草稿行（complete-metadata 契约）：language 为语言代码（如 zh-CN），无法解析时省略 */
export interface DraftLine {
  language?: string;
  value: string;
}

/** 新建模式草稿：以当前表单已填内容为生成上下文；与 itemId 同给时后端以 itemId 为准（前端编辑态不发 draft） */
export interface AiDraft {
  itemType?: string;
  designations?: DraftLine[];
  definitions?: DraftLine[];
}

const props = defineProps<{
  visible: boolean;
  anchor: HTMLElement | null;
  field: 'designation' | 'definition';
  itemId?: string;
  /** 行级缓存 key：成功结果经 generated 事件回传父级缓存 */
  cacheKey: string;
  /** true=点击按钮打开：立即发起生成；false=悬停回看：直接展示 cached，不发请求 */
  autoGenerate: boolean;
  cached?: FieldCandidate[] | null;
  /** 父级记录的行级最近错误：无缓存行悬停重开时展示错误态与重试入口（悬停不自动重发） */
  lastError?: string | null;
  /** 新建模式草稿（无 itemId 时的生成上下文）：itemId 存在时被忽略（后端契约：itemId 优先） */
  draft?: AiDraft | null;
  /** 锚定按钮的悬停状态（父级跟踪）：按钮或浮层任一悬停时保持打开 */
  buttonHover?: boolean;
}>();

const emit = defineEmits<{
  apply: [candidate: FieldCandidate];
  close: [];
  generated: [payload: { key: string; candidates: FieldCandidate[] }];
  /** 行级生成进行中状态变化（start/完成/取消/失败各路径维护）：父级悬停判定据此走「进行中」分支 */
  'inflight-change': [payload: { key: string; inflight: boolean }];
  /** 生成失败：父级记录行级错误，供悬停重开时展示错误态 */
  failed: [payload: { key: string; message: string }];
  'popper-hover-change': [hovered: boolean];
}>();

type Phase = 'idle' | 'loading' | 'done' | 'error';
const phase = ref<Phase>('idle');
const candidates = ref<FieldCandidate[]>([]);
const errorText = ref('');
const panelEl = ref<HTMLElement | null>(null);
let jobId: string | null = null;
let closeTimer: ReturnType<typeof setTimeout> | null = null;
const CLOSE_DELAY_MS = 200;
/** 进行中的生成：loading 期间浮层可能被关闭（phase 被重置）但后端作业仍在跑，
 * 仅靠 phase 无法判重，须以显式标志防重入 */
let inflight = false;
/** 生成代次：取消/取代时 +1，使旧 start 的续体（finally/回写缓存/补发关闭）全部失效 */
let startSeq = 0;
/** 当前代生成所属的行级缓存 key：cancel/finally 上报 inflight-change 时使用 */
let currentKey = '';

onMounted(() => document.addEventListener('click', onDocumentClick));
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick));

function isBusy(): boolean {
  return phase.value === 'loading';
}

defineExpose({ isBusy });

function clearCloseTimer() {
  if (closeTimer !== null) {
    clearTimeout(closeTimer);
    closeTimer = null;
  }
}

/** 按钮与浮层都离开且非生成中 → 延迟关闭；加载/生成中鼠标离开不关闭 */
function scheduleClose() {
  clearCloseTimer();
  if (!props.visible) return;
  if (props.buttonHover) return;
  closeTimer = setTimeout(() => {
    closeTimer = null;
    if (props.buttonHover || popperHover.value) return;
    if (phase.value === 'loading') return;
    emit('close');
  }, CLOSE_DELAY_MS);
}

const popperHover = ref(false);

function onPopperHover(hovered: boolean) {
  popperHover.value = hovered;
  emit('popper-hover-change', hovered);
  if (!hovered) scheduleClose();
}

watch(
  () => [props.visible, props.autoGenerate] as const,
  ([visible, autoGenerate]) => {
    clearCloseTimer();
    if (!visible) {
      phase.value = 'idle';
      candidates.value = [];
      errorText.value = '';
      return;
    }
    if (autoGenerate) {
      if (inflight) {
        // 进行中悬停重开：仅恢复加载态展示，不发新请求（防重入在 start 内）；
        // 轮询续体仍存活，结果到达后就地切换为候选/错误态
        phase.value = 'loading';
        errorText.value = '';
        return;
      }
      if (props.lastError) {
        // 上次失败且（父级判定）无缓存：展示错误态与重试入口，等用户主动重试，
        // 悬停不自动重发——避免鼠标划过即打请求
        errorText.value = props.lastError;
        phase.value = 'error';
        return;
      }
      void start();
    } else {
      // 悬停回看：展示行级缓存，不发请求
      phase.value = 'done';
      candidates.value = [...(props.cached ?? [])];
      errorText.value = '';
    }
  },
  { immediate: true },
);

watch(
  () => props.buttonHover,
  (hovered) => {
    if (hovered) clearCloseTimer();
    else scheduleClose();
  },
);

/** 点击生成：默认提供方与候选数由服务端按设置注入，请求体不询问。
 *  cacheKey 在发请求前捕获：生成中途浮层可能被关闭（aiGen=null 使 prop 回退空串），
 *  成功回传时必须使用发起时的 key，否则缓存写进空 key、悬停回看永远 miss。
 *  防重入语义：进行中（inflight）一律忽略重复触发，不取消重发——旧作业结果将回写同一行缓存；
 *  取消重发会浪费 provider 调用，并引入旧作业晚到覆盖新缓存的竞态。 */
async function start() {
  if (inflight) {
    // 重复触发（watcher 并发、悬停→点击竞态、关闭后再次点击）：仅同步展示态，不发请求
    phase.value = 'loading';
    return;
  }
  inflight = true;
  const mySeq = ++startSeq;
  const cacheKey = props.cacheKey;
  currentKey = cacheKey;
  phase.value = 'loading';
  errorText.value = '';
  emit('inflight-change', { key: cacheKey, inflight: true });
  try {
    // 载荷分支：编辑态以库内条目为准（itemId，不发 draft）；新建态无 itemId，以表单草稿为上下文
    const payload: CreateAssistantJobPayload = {
      taskId: 'complete-metadata',
      params: { fields: [props.field] },
    };
    if (props.itemId) {
      payload.itemId = props.itemId;
    } else if (props.draft) {
      payload.params = { ...payload.params, draft: props.draft };
    }
    const created = await assistantApi.createJob(payload);
    jobId = created.jobId;
    const job = await pollAssistantJob(created.jobId);
    if (mySeq !== startSeq) return; // 已被取消/取代：不再改状态、不回写缓存、不补发关闭
    jobId = null;
    if (job.status === 'SUCCEEDED') {
      const result = Array.isArray(job.result) ? (job.result as FieldCandidate[]) : [];
      candidates.value = result.filter((c) => c && typeof c.text === 'string');
      phase.value = 'done';
      emit('generated', { key: cacheKey, candidates: [...candidates.value] });
    } else if (job.status === 'CANCELLED') {
      emit('close');
    } else {
      errorText.value = job.error || t('aiGenerate.failed');
      phase.value = 'error';
      emit('failed', { key: cacheKey, message: errorText.value });
    }
  } catch (err: any) {
    if (mySeq !== startSeq) return;
    jobId = null;
    errorText.value = err?.message || t('aiGenerate.failed');
    phase.value = 'error';
    emit('failed', { key: cacheKey, message: errorText.value });
  } finally {
    if (mySeq === startSeq) {
      inflight = false;
      emit('inflight-change', { key: cacheKey, inflight: false });
      // 生成结束：若用户已离开按钮与浮层，补一次关闭判定
      scheduleClose();
    }
  }
}

async function cancel() {
  // 使进行中的 start 续体失效：其回写缓存/补发关闭/置 inflight=false 均被跳过；
  // 显式取消代表用户要终止本次生成，允许立即重新发起
  startSeq += 1;
  const jid = jobId;
  jobId = null;
  inflight = false;
  emit('inflight-change', { key: currentKey, inflight: false });
  // 立即关闭浮层，不等网络：cancelJob 慢时迟到的 close 会打断用户已重新发起的生成
  emit('close');
  if (jid) {
    // 后台取消即可，拦截器已统一提示错误
    void Promise.resolve(assistantApi.cancelJob(jid)).catch(() => {});
  }
}

function apply(candidate: FieldCandidate) {
  emit('apply', candidate);
  emit('close');
}

/* ---------- click-outside 关闭（原生实现；不经 ElTooltip 内部机制） ---------- */

function onDocumentClick(event: MouseEvent) {
  if (!props.visible) return;
  const path = event.composedPath();
  // 锚定卡片（含「✨ AI 生成」按钮）与浮层面板内的点击不算外部点击
  if (props.anchor && path.includes(props.anchor)) return;
  if (panelEl.value && path.includes(panelEl.value)) return;
  emit('close');
}
</script>

<style scoped>
.ai-candidate-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-candidate-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ai-candidate-title {
  font-size: 13px;
  font-weight: 600;
}

.ai-candidate-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.ai-candidate-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 260px;
  overflow-y: auto;
}

.ai-candidate-item {
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  cursor: pointer;
}

.ai-candidate-item:hover {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.ai-candidate-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
