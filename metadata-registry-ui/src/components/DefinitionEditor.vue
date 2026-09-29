<template>
  <div class="text-line-editor">
    <div
      v-for="(row, index) in modelValue"
      :key="index"
      class="text-line-card"
    >
      <div class="text-line-row" style="align-items: flex-start;">
        <el-select
          v-model="row.languageId"
          placeholder="语言"
          style="width: 140px; flex-shrink: 0;"
          size="small"
        >
          <template #label="{ label, value }">
            <span v-if="isPendingLanguage(value)">…</span>
            <span v-else>{{ label }}</span>
          </template>
          <el-option
            v-for="lang in languages"
            :key="lang.id"
            :label="lang.languageName ? `${lang.languageName}（${lang.languageIdentifier}）` : lang.languageIdentifier"
            :value="lang.id"
          />
        </el-select>
        <el-input
          v-model="row.value"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="输入定义文本"
          size="small"
        />
        <el-button
          type="danger"
          size="small"
          text
          circle
          @click="removeRow(index)"
          style="margin-top: 2px;"
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
      class="add-row-btn"
      :class="{ 'is-empty': !modelValue.length }"
      @click="addRow"
    >
      <el-icon><Plus /></el-icon>
      添加定义
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Plus, Delete, ArrowRight, ArrowDown } from '@element-plus/icons-vue';
import { languageApi } from '@/api/resources';
import { useDefaultContext } from '@/composables/useDefaultContext';
import type { TextLineForm } from '@/types/common';
import ContextSelector from './ContextSelector.vue';
import AcceptabilitySelect from './AcceptabilitySelect.vue';

interface Props {
  modelValue: TextLineForm[];
  defaultLanguageId?: string;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  'update:modelValue': [value: TextLineForm[]];
}>();

const languages = ref<{ id: string; languageIdentifier: string; languageName?: string }[]>([]);
const expanded = ref<Record<number, boolean>>({});
const { defaultContextId, load: loadDefaultContext } = useDefaultContext();

const languagesLoaded = ref(false);

/** 语言列表加载完成前，未命中的选中值显示占位而非原始 id */
function isPendingLanguage(value: unknown): boolean {
  if (!value || typeof value !== 'string') return false;
  if (languagesLoaded.value) return false;
  return !languages.value.some((l) => l.id === value);
}

onMounted(async () => {
  loadDefaultContext();
  try {
    const langRes = await languageApi.list({ size: 100, enabled: true });
    languages.value = (langRes.content || []).map((l: any) => ({
      id: l.id,
      languageIdentifier: l.languageIdentifier || l.identifier || '',
      languageName: l.languageName ?? undefined,
    }));
  } catch {
    // fallback
  } finally {
    languagesLoaded.value = true;
  }
});

function toggleExpand(index: number) {
  expanded.value[index] = !expanded.value[index];
}

function addRow() {
  const tempId = typeof crypto.randomUUID === 'function' ? crypto.randomUUID() : `temp-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
  const next: TextLineForm[] = [...props.modelValue, {
    id: tempId,
    languageId: props.defaultLanguageId || '',
    value: '',
    // 系统默认上下文（Registry Context）作为预填值，用户可变更
    contexts: defaultContextId.value
      ? [{ contextId: defaultContextId.value, acceptability: 'ADMITTED' as const }]
      : [],
  }];
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
      contexts: [...(row.contexts || []), { contextId: defaultContextId.value || '', acceptability: 'ADMITTED' as const }],
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
  align-items: flex-start;
  align-self: stretch;
  width: 100%;
}
.text-line-card {
  align-self: stretch;
  background: #f8fafc;
  border-radius: 8px;
  padding: 10px 12px;
  border: 1px solid #f1f5f9;
}
.add-row-btn.is-empty {
  align-self: stretch;
  width: 100%;
  height: auto;
  margin-left: 0;
  padding: 8px 0;
  border: 1.5px dashed var(--el-border-color);
  border-radius: 8px;
  color: var(--el-color-primary);
  background: var(--el-fill-color-blank);
}
.add-row-btn.is-empty:hover {
  border-color: var(--el-color-primary);
  background: #f0f7f2;
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
  color: var(--el-color-primary);
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
