<template>
  <div
    class="status-node"
    :class="['status-node--' + data.category, { 'status-node--highlighted': isHighlighted }]"
    :data-status="data.status"
  >
    <!-- 显式连接点：默认走 下(源)→上(目标)；应用→候选经右侧环路（src-right/tgt-right）。
         透明渲染，仅作边路由锚点 -->
    <Handle id="src-bottom" type="source" :position="Position.Bottom" class="status-node__handle" />
    <Handle id="tgt-top" type="target" :position="Position.Top" class="status-node__handle" />
    <Handle id="src-right" type="source" :position="Position.Right" class="status-node__handle" />
    <Handle id="tgt-right" type="target" :position="Position.Right" class="status-node__handle" />
    <div class="status-node__label">{{ $t('registrationStatus.' + data.status) }}</div>
    <div class="status-node__code">{{ data.status }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, type ComputedRef } from 'vue';
import { Handle, Position } from '@vue-flow/core';

interface Props {
  data: {
    status: string;
    category: 'lifecycle' | 'documentation';
  };
}

const props = defineProps<Props>();

/** 高亮来源：选中连线时其两端状态节点联动高亮；单击选中状态时该节点高亮（由流程图视图 provide） */
const highlightedStatuses = inject<ComputedRef<Set<string>>>('rp-highlightedStatuses');
const selectedNode = inject<ComputedRef<string | null>>('rp-selectedNode');

const isHighlighted = computed(
  () => highlightedStatuses?.value.has(props.data.status) || selectedNode?.value === props.data.status,
);
</script>

<style scoped lang="scss">
.status-node {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  border-radius: 8px;
  border: 1.5px solid var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  font-size: 13px;
  cursor: grab;

  &:active {
    cursor: grabbing;
  }

  &--documentation {
    border-color: var(--el-color-info);
    background: var(--el-color-info-light-9);
  }

  // 选中连线的两端节点联动高亮：主色描边 + 光晕
  &--highlighted {
    border-color: var(--el-color-primary);
    border-width: 2.5px;
    box-shadow:
      0 0 0 4px var(--el-color-primary-light-8),
      0 0 14px var(--el-color-primary-light-5);
  }

  &__label {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__code {
    font-size: 11px;
    color: var(--el-text-color-secondary);
    font-family: monospace;
  }

  // 连接点仅作边路由锚点：透明、不拦截指针
  &__handle {
    opacity: 0;
    pointer-events: none;
  }
}
</style>
