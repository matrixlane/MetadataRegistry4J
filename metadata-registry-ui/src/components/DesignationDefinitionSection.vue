<template>
  <div class="designation-definition-section">
    <div class="section-card">
      <div class="section-header">
        <span class="section-title">指称（Designations）</span>
        <el-tooltip content="指称是概念在特定语言和上下文下的命名符号" placement="top">
          <el-icon class="info-icon"><InfoFilled /></el-icon>
        </el-tooltip>
      </div>
      <div class="section-body">
        <DesignationEditor
          :model-value="designations"
          @update:model-value="emit('update:designations', $event)"
        />
      </div>
    </div>

    <div class="section-card">
      <div class="section-header">
        <span class="section-title">定义（Definitions）</span>
        <el-tooltip content="定义是对注册项概念内容的规范性文本阐释" placement="top">
          <el-icon class="info-icon"><InfoFilled /></el-icon>
        </el-tooltip>
      </div>
      <div class="section-body">
        <DefinitionEditor
          :model-value="definitions"
          @update:model-value="emit('update:definitions', $event)"
        />
      </div>
    </div>

    <div v-if="showPairings" class="section-card">
      <div class="section-header">
        <span class="section-title">指称-定义配对（Pairings）</span>
        <el-tooltip content="指称-定义配对将特定语言下的符号与文本定义在特定上下文下进行关联" placement="top">
          <el-icon class="info-icon"><InfoFilled /></el-icon>
        </el-tooltip>
      </div>
      <div class="section-body">
        <DesignationDefinitionPairingEditor
          :model-value="pairings"
          :designations="designationOptions"
          :definitions="definitionOptions"
          @update:model-value="emit('update:pairings', $event)"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { InfoFilled, Lock } from '@element-plus/icons-vue';
import type { TextLineForm, PairingForm } from '@/types/common';
import DesignationEditor from './DesignationEditor.vue';
import DefinitionEditor from './DefinitionEditor.vue';
import DesignationDefinitionPairingEditor from './DesignationDefinitionPairingEditor.vue';

interface Props {
  designations: TextLineForm[];
  definitions: TextLineForm[];
  pairings?: PairingForm[];
  showPairings?: boolean;
  itemId?: string;
}

const props = withDefaults(defineProps<Props>(), {
  pairings: () => [],
  showPairings: true,
  itemId: '',
});

const emit = defineEmits<{
  'update:designations': [value: TextLineForm[]];
  'update:definitions': [value: TextLineForm[]];
  'update:pairings': [value: PairingForm[]];
}>();

const designationOptions = computed(() =>
  props.designations
    .filter((d) => d.id && d.value)
    .map((d) => ({ id: d.id!, value: d.value })),
);

const definitionOptions = computed(() =>
  props.definitions
    .filter((d) => d.id && d.value)
    .map((d) => ({ id: d.id!, value: d.value })),
);
</script>

<style scoped>
.designation-definition-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.section-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  padding: 18px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px -2px rgba(0, 0, 0, 0.05);
}

.section-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 4px 16px -4px rgba(0, 0, 0, 0.08);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.info-icon {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  cursor: help;
  transition: color 0.2s;
}

.info-icon:hover {
  color: var(--el-color-primary);
}

.section-body {
  position: relative;
}

.pairing-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30px 20px;
  border: 2px dashed var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  gap: 8px;
}

.lock-icon {
  color: var(--el-text-color-disabled);
}
</style>
