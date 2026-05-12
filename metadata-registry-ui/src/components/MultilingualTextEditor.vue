<template>
  <div class="multilingual-editor">
    <div
      v-for="(row, index) in modelValue"
      :key="index"
      class="multilingual-card"
    >
      <div class="multilingual-row">
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
          :placeholder="`${label}内容`"
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
              <el-select
                v-model="ctx.contextId"
                placeholder="选择上下文"
                size="small"
                style="width: 180px;"
                filterable
                remote
                reserve-keyword
                :remote-method="searchContexts"
                :loading="searchingContexts"
              >
                <el-option
                  v-for="c in contextOptions"
                  :key="c.id"
                  :label="c.label"
                  :value="c.id"
                />
              </el-select>
              <el-select
                v-model="ctx.acceptability"
                placeholder="可接受性"
                size="small"
                style="width: 140px;"
              >
                <el-option label="首选" value="PREFERRED" />
                <el-option label="认可" value="ADMITTED" />
                <el-option label="已弃用" value="DEPRECATED" />
                <el-option label="已取代" value="SUPERSEDED" />
                <el-option label="已过时" value="OBSOLETE" />
              </el-select>
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
      添加{{ label }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Plus, Delete, ArrowRight, ArrowDown } from '@element-plus/icons-vue';
import { languageApi, contextApi } from '@/api/resources';
import type { TextLineForm } from '@/types/common';

interface Props {
  modelValue: TextLineForm[];
  label: string;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  'update:modelValue': [value: TextLineForm[]];
}>();

const languages = ref<{ id: string; languageIdentifier: string }[]>([]);
const contextOptions = ref<{ id: string; label: string }[]>([]);
const searchingContexts = ref(false);
const expanded = ref<Record<number, boolean>>({});

onMounted(async () => {
  try {
    const langRes = await languageApi.list({ size: 100 });
    languages.value = (langRes.content || []).map((l: any) => ({
      id: l.id,
      languageIdentifier: l.languageIdentifier || l.identifier || '',
    }));
    await searchContexts('');
  } catch {
    // fallback
  }
});

async function searchContexts(query: string) {
  searchingContexts.value = true;
  try {
    const res = await contextApi.options({ q: query });
    contextOptions.value = (res as any) || [];
  } catch {
    // ignore
  } finally {
    searchingContexts.value = false;
  }
}

function toggleExpand(index: number) {
  expanded.value[index] = !expanded.value[index];
}

function addRow() {
  const next = [...props.modelValue, { languageId: '', value: '' }];
  emit('update:modelValue', next);
}

function removeRow(index: number) {
  const next = props.modelValue.filter((_, i) => i !== index);
  emit('update:modelValue', next);
}

function addContext(rowIndex: number) {
  const next = [...props.modelValue];
  if (!next[rowIndex].contexts) {
    next[rowIndex].contexts = [];
  }
  next[rowIndex].contexts!.push({ contextId: '', acceptability: 'ADMITTED' });
  emit('update:modelValue', next);
  expanded.value[rowIndex] = true;
}

function removeContext(rowIndex: number, ctxIndex: number) {
  const next = [...props.modelValue];
  next[rowIndex].contexts!.splice(ctxIndex, 1);
  emit('update:modelValue', next);
}
</script>

<style scoped>
.multilingual-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.multilingual-card {
  background: #f8fafc;
  border-radius: 8px;
  padding: 10px 12px;
  border: 1px solid #f1f5f9;
}
.multilingual-row {
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
