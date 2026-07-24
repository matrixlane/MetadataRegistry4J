<template>
  <div class="text-line-editor">
    <div
      v-for="(row, index) in modelValue"
      :key="index"
      class="text-line-card"
    >
      <div class="text-line-row">
        <el-select
          v-model="row.languageId"
          placeholder="语言"
          style="width: 140px; flex-shrink: 0;"
          size="small"
        >
          <el-option
            v-for="lang in languages"
            :key="lang.id"
            :label="lang.languageIdentifier"
            :value="lang.id"
          />
        </el-select>
        <el-input
          v-model="row.value"
          placeholder="输入指称文本"
          size="small"
        />
        <el-button
          type="danger"
          size="small"
          text
          circle
          @click="removeRow(index)"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>

      <div class="context-section">
        <div class="context-header" @click="toggleExpand(index)">
          <el-icon size="12"><component :is="expanded[index] ? ArrowDown : ArrowRight" /></el-icon>
          <span>上下文关联</span>
          <el-tag v-if="row.contexts?.length" size="small" type="info" round style="margin-left: 8px;">
            {{ row.contexts.length }}
          </el-tag>
        </div>
        <el-collapse-transition>
          <div v-show="expanded[index]" class="context-body">
            <div
              v-for="(ctx, cidx) in (row.contexts || [])"
              :key="cidx"
              class="context-row"
            >
              <ContextSelector
                v-model="ctx.contextId"
                style="flex: 1;"
              />
              <AcceptabilitySelect
                v-model="ctx.acceptability"
                style="width: 130px;"
              />
              <el-button
                type="danger"
                size="small"
                text
                circle
                @click="removeContext(index, cidx)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button
              type="primary"
              size="small"
              text
              @click="addContext(index)"
            >
              <el-icon><Plus /></el-icon>
              添加上下文
            </el-button>
          </div>
        </el-collapse-transition>
      </div>
    </div>

    <el-button
      type="primary"
      size="small"
      text
      @click="addRow"
    >
      <el-icon><Plus /></el-icon>
      添加指称
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Plus, Delete, ArrowRight, ArrowDown } from '@element-plus/icons-vue';
import { languageApi } from '@/api/resources';
import type { TextLineForm } from '@/types/common';
import ContextSelector from './ContextSelector.vue';
import AcceptabilitySelect from './AcceptabilitySelect.vue';

interface Props {
  modelValue: TextLineForm[];
}

const props = defineProps<Props>();
const emit = defineEmits<{
  'update:modelValue': [value: TextLineForm[]];
}>();

const languages = ref<{ id: string; languageIdentifier: string }[]>([]);
const expanded = ref<Record<number, boolean>>({});

onMounted(async () => {
  try {
    const langRes = await languageApi.list({ size: 100, enabled: true });
    languages.value = (langRes.content || []).map((l: any) => ({
      id: l.id,
      languageIdentifier: l.languageIdentifier || l.identifier || '',
    }));
  } catch {
    // fallback
  }
});

function toggleExpand(index: number) {
  expanded.value[index] = !expanded.value[index];
}

function addRow() {
  const tempId = typeof crypto.randomUUID === 'function' ? crypto.randomUUID() : `temp-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
  const next: TextLineForm[] = [...props.modelValue, { id: tempId, languageId: '', value: '', contexts: [] }];
  emit('update:modelValue', next);
}

function removeRow(index: number) {
  const next = props.modelValue.filter((_, i) => i !== index);
  emit('update:modelValue', next);
}

function addContext(rowIndex: number) {
  const next = props.modelValue.map((row, i) => {
    if (i !== rowIndex) return row;
    return {
      ...row,
      contexts: [...(row.contexts || []), { contextId: '', acceptability: 'ADMITTED' as const }],
    };
  });
  emit('update:modelValue', next);
  expanded.value[rowIndex] = true;
}

function removeContext(rowIndex: number, ctxIndex: number) {
  const next = props.modelValue.map((row, i) => {
    if (i !== rowIndex) return row;
    return {
      ...row,
      contexts: (row.contexts || []).filter((_, ci) => ci !== ctxIndex),
    };
  });
  emit('update:modelValue', next);
}
</script>

<style scoped>
.text-line-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.text-line-card {
  background: #f8fafc;
  border-radius: 8px;
  padding: 10px 12px;
  border: 1px solid #f1f5f9;
}
.text-line-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.context-section {
  margin-top: 8px;
}
.context-header {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6b7280;
  cursor: pointer;
  user-select: none;
  padding: 2px 0;
}
.context-header:hover {
  color: #2563eb;
}
.context-body {
  margin-top: 8px;
  padding-left: 8px;
}
.context-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
</style>
