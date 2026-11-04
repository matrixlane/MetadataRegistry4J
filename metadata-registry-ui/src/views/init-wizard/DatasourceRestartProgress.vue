<template>
  <section class="restart-progress" aria-labelledby="restart-progress-title">
    <div class="restart-activity">
      <progress
        v-if="phase !== 'FAILED' && phase !== 'ROLLED_BACK'"
        class="restart-spinner"
        aria-label="数据库设置应用进度"
      />
      <div
        id="restart-progress-title"
        class="restart-announcement"
        role="status"
        aria-live="polite"
        aria-atomic="true"
      >
        <h4>{{ phaseTitle }}</h4>
        <p>{{ phaseDescription }}</p>
      </div>
    </div>

    <ol class="restart-stages" aria-label="数据库设置应用阶段">
      <li
        v-for="(stage, index) in stages"
        :key="stage"
        :class="{ active: currentStage === index }"
        :aria-current="currentStage === index ? 'step' : undefined"
      >
        {{ stage }}
      </li>
    </ol>

    <div class="restart-waiting-row">
      <span data-testid="restart-elapsed" aria-live="off">已等待 {{ formattedElapsed }}</span>
      <el-button
        v-if="phase === 'FAILED'"
        data-testid="restart-retry"
        type="primary"
        @click="$emit('retry')"
      >
        重试连接
      </el-button>
    </div>
    <p v-if="phase === 'FAILED'" class="restart-support">
      您也可以关闭后重新打开应用；若仍无法使用，请联系支持人员。
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import type { DatasourceRestartPhase } from '@/utils/datasourceRestart';

const props = defineProps<{
  phase: DatasourceRestartPhase;
  startedAt: number;
  reasonCode?: string | null;
}>();

defineEmits<{ retry: [] }>();

const stages = ['保存设置', '重启应用', '准备数据库', '核对设置', '恢复'];
const now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | undefined;

const currentStage = computed(() => {
  switch (props.phase) {
    case 'SAVING': return 0;
    case 'RESTARTING': return 1;
    case 'PREPARING': return 2;
    // 成功路径的收尾仍是"核对设置"；"恢复"这一段只属于回滚路径。
    case 'VERIFYING':
    case 'SUCCEEDED':
    case 'RESTORING': return 3;
    case 'RECOVERING':
    case 'ROLLED_BACK': return 4;
    default: return -1;
  }
});

const phaseTitle = computed(() => {
  const titles: Record<DatasourceRestartPhase, string> = {
    SAVING: '正在保存数据库设置',
    RESTARTING: '正在重启应用',
    PREPARING: '正在准备数据库',
    VERIFYING: '正在核对数据库设置',
    RECOVERING: '正在恢复原数据库设置',
    RESTORING: '正在恢复初始化向导',
    SUCCEEDED: '数据库设置已生效',
    ROLLED_BACK: '新数据库设置未能应用，已恢复原设置',
    FAILED: '暂时无法完成数据库设置',
  };
  return titles[props.phase];
});

const phaseDescription = computed(() => {
  if (props.phase === 'FAILED') {
    return props.reasonCode === 'WAIT_TIMEOUT'
      ? '等待时间较长，本次自动操作已停止等待。您可以重试连接，或重新打开应用。'
      : '自动恢复未完成。请重试连接或重新打开应用；若仍无法使用，请联系支持人员。';
  }
  const descriptions: Record<Exclude<DatasourceRestartPhase, 'FAILED'>, string> = {
    SAVING: '正在安全保存设置，请稍候。',
    RESTARTING: '应用会自动恢复连接，请稍候。',
    PREPARING: '应用已响应，正在完成数据库准备，请稍候。',
    VERIFYING: '正在确认当前使用的数据库与本次设置一致。',
    RECOVERING: '新设置未能正常使用，正在自动恢复原设置。',
    RESTORING: '设置已生效，正在重新读取数据源和初始化状态。',
    SUCCEEDED: '新数据库设置已核对并生效。',
    ROLLED_BACK: '原数据库设置已恢复，您可以修改后重试。',
  };
  return descriptions[props.phase as Exclude<DatasourceRestartPhase, 'FAILED'>];
});

const formattedElapsed = computed(() => {
  const totalSeconds = Math.max(0, Math.floor((now.value - props.startedAt) / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return minutes > 0 ? `${minutes} 分 ${seconds} 秒` : `${seconds} 秒`;
});

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now();
  }, 1000);
});

onBeforeUnmount(() => {
  if (timer !== undefined) clearInterval(timer);
});
</script>

<style scoped lang="scss">
.restart-progress {
  padding: 20px;
  border: 1px solid #dbe5df;
  border-radius: 12px;
  background: #f8fafc;
}

.restart-activity {
  display: flex;
  align-items: center;
  gap: 16px;
}

.restart-announcement {
  h4 {
    margin: 0 0 6px;
    color: #1e293b;
    font-size: 16px;
  }

  p {
    margin: 0;
    color: #64748b;
    line-height: 1.5;
  }
}

progress.restart-spinner:indeterminate {
  --size: 40px;
  --thickness: 3px;
  --spinner-duration: 1.5s;
  --_used-spinner-duration: var(--spinner-duration);
  flex: 0 0 var(--size);
  width: var(--size);
  height: var(--size);
  border-radius: 50%;
  appearance: none;
  background: conic-gradient(var(--el-color-primary) 0 25%, #dbe5df 0);
  animation: restart-spin var(--_used-spinner-duration) linear infinite;
}

progress.restart-spinner:indeterminate::-webkit-progress-bar {
  display: none;
  background: none;
}
progress.restart-spinner:indeterminate::-webkit-progress-value {
  display: none;
  background: none;
}
progress.restart-spinner:indeterminate::-moz-progress-bar {
  display: none;
  background: none;
}
progress.restart-spinner:indeterminate::slider-fill {
  display: none;
  background: none;
}

@supports (background-clip: border-area) {
  progress.restart-spinner:indeterminate {
    background-clip: border-area;
    border: var(--thickness) solid transparent;
    background-origin: border-box;
  }
}

@supports not (background-clip: border-area) {
  progress.restart-spinner:indeterminate {
    --clip-boundary: calc(100% - var(--thickness));
    mask-image: radial-gradient(
      farthest-side,
      transparent var(--clip-boundary),
      #000 var(--clip-boundary)
    );
    border: 0;
  }
}

.restart-stages {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  margin: 20px 0 12px;
  padding: 0;
  list-style: none;
  color: #8492a6;
  font-size: 12px;
  text-align: center;

  li {
    padding-top: 8px;
    border-top: 2px solid #dbe5df;
  }

  li.active {
    border-color: var(--el-color-primary);
    color: #166534;
    font-weight: 600;
  }
}

.restart-waiting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  color: #64748b;
  font-size: 13px;
}

.restart-support {
  margin: 12px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

@keyframes restart-spin {
  to { rotate: 1turn; }
}

@media (prefers-reduced-motion: reduce) {
  .restart-spinner {
    --_used-spinner-duration: 6s;
  }
}
</style>
