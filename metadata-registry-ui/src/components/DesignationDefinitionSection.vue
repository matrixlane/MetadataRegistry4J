<template>
  <div class="designation-definition-section">
    <el-collapse v-model="activePanels" class="designation-definition-collapse">
      <el-collapse-item name="designations">
        <template #title>
          <div class="collapse-title">
            <span class="section-title">指称（Designations）</span>
            <el-tooltip content="指称是概念在特定语言和上下文下的命名符号" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </div>
        </template>
        <DesignationEditor
          :model-value="designations"
          :default-language-id="defaultLanguageId"
          :naming-convention-options="namingConventionOptions"
          :require-naming-conventions="requireNamingConventions"
          @update:model-value="emit('update:designations', $event)"
        />
      </el-collapse-item>

      <el-divider />

      <el-collapse-item name="definitions">
        <template #title>
          <div class="collapse-title">
            <span class="section-title">定义（Definitions）</span>
            <el-tooltip content="定义是对注册项概念内容的规范性文本阐释" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </div>
        </template>
        <DefinitionEditor
          :model-value="definitions"
          :default-language-id="defaultLanguageId"
          @update:model-value="emit('update:definitions', $event)"
        />
      </el-collapse-item>

      <el-divider v-if="showPairings" />

      <el-collapse-item v-if="showPairings" name="pairings">
        <template #title>
          <div class="collapse-title">
            <span class="section-title">指称-定义配对（Pairings）</span>
            <el-tooltip content="指称-定义配对将特定语言下的符号与文本定义在特定上下文下进行关联" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </div>
        </template>
        <div v-if="!itemId" class="pairing-placeholder">
          <el-icon :size="24" class="lock-icon"><Lock /></el-icon>
          <span>保存后方可配置配对</span>
        </div>
        <DesignationDefinitionPairingEditor
          v-else
          :model-value="pairings"
          :designations="designationOptions"
          :definitions="definitionOptions"
          @update:model-value="emit('update:pairings', $event)"
        />
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { InfoFilled, Lock } from '@element-plus/icons-vue';
import type { TextLineForm, PairingForm, NamingConventionResponse } from '@/types/common';
import DesignationEditor from './DesignationEditor.vue';
import DefinitionEditor from './DefinitionEditor.vue';
import DesignationDefinitionPairingEditor from './DesignationDefinitionPairingEditor.vue';

interface Props {
  designations: TextLineForm[];
  definitions: TextLineForm[];
  pairings?: PairingForm[];
  showPairings?: boolean;
  itemId?: string;
  defaultLanguageId?: string;
  namingConventionOptions?: NamingConventionResponse[];
  requireNamingConventions?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  pairings: () => [],
  showPairings: true,
  itemId: '',
  namingConventionOptions: () => [],
  requireNamingConventions: false,
});

const emit = defineEmits<{
  'update:designations': [value: TextLineForm[]];
  'update:definitions': [value: TextLineForm[]];
  'update:pairings': [value: PairingForm[]];
}>();

const activePanels = ref(['designations', 'definitions']);

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
  width: 100%;
}

.designation-definition-collapse {
  --el-collapse-border-color: transparent;
}

.collapse-title {
  display: flex;
  align-items: center;
  gap: 8px;
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
