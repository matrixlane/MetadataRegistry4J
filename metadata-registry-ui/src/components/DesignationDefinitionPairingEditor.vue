<template>
  <div class="pairing-editor">
    <div v-if="showEmpty" class="pairing-empty">
      <el-empty description="请先添加指称和定义，再创建配对" />
    </div>
    <div v-else class="pairing-list">
      <div
        v-for="(row, index) in modelValue"
        :key="row.id || index"
        class="pairing-card"
        :class="{ 'pairing-error': hasError(index) }"
      >
        <div class="pairing-row">
          <div class="pairing-field">
            <el-input
              :model-value="designationText(row)"
              :placeholder="createDesignation ? '输入指称文本；未关联指称时自动新建并关联' : '选择指称'"
              :disabled="!createDesignation && !updateDesignation"
              size="small"
              style="flex: 1;"
              @update:model-value="(v: string) => onDesignationText(row, v)"
            />
            <el-select
              :model-value="row.designationId"
              placeholder="引用既有"
              size="small"
              class="pairing-link-select"
              clearable
              filterable
              @change="(v: string) => onDesignationLink(row, v)"
            >
              <el-option
                v-for="d in designations"
                :key="d.id"
                :label="lineLabel(d)"
                :value="d.id"
              />
            </el-select>
          </div>
          <el-icon class="pairing-link"><Link /></el-icon>
          <div class="pairing-field">
            <el-input
              :model-value="definitionText(row)"
              :placeholder="createDefinition ? '输入定义文本；未关联定义时自动新建并关联' : '选择定义'"
              :disabled="!createDefinition && !updateDefinition"
              size="small"
              style="flex: 1;"
              @update:model-value="(v: string) => onDefinitionText(row, v)"
            />
            <el-select
              :model-value="row.definitionId"
              placeholder="引用既有"
              size="small"
              class="pairing-link-select"
              clearable
              filterable
              @change="(v: string) => onDefinitionLink(row, v)"
            >
              <el-option
                v-for="d in definitions"
                :key="d.id"
                :label="lineLabel(d)"
                :value="d.id"
              />
            </el-select>
          </div>
        </div>
        <div class="pairing-row" style="margin-top: 8px;">
          <el-select
            v-model="row.contextId"
            placeholder="选择上下文（必填）"
            size="small"
            style="flex: 1;"
            filterable
            remote
            reserve-keyword
            :remote-method="searchContexts"
            :loading="searchingContexts"
          >
            <!-- 自引用选项（上下文条目编辑自身时由宿主传入），不依赖远程搜索 -->
            <el-option
              v-if="selfContextOption"
              :key="selfContextOption.id"
              :label="selfContextOption.label"
              :value="selfContextOption.id"
            />
            <el-option
              v-for="c in contextOptions"
              :key="c.id"
              :label="c.label"
              :value="c.id"
            />
          </el-select>
          <el-select
            v-model="row.acceptability"
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
            @click="removeRow(index)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <div v-if="contextError(index)" class="pairing-error-text">
          请选择上下文
        </div>
        <div v-if="duplicateError(index)" class="pairing-error-text">
          与第 {{ duplicateIndex(index) + 1 }} 行重复（相同指称、定义、上下文）
        </div>
      </div>
     <el-button
        class="add-pairing-button"
       type="primary"
       size="small"
       text
       @click="addRow"
     >
        <el-icon><Plus /></el-icon>
        添加配对
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Plus, Delete, Link } from '@element-plus/icons-vue';
import { contextApi } from '@/api/resources';
import type { PairingForm, TextLineContextForm } from '@/types/common';
import { ref, onMounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
const { t } = useI18n();

interface LineOption {
  id: string;
  value: string;
  languageCode?: string;
  contexts?: { contextId: string; contextLabel?: string; acceptability?: string }[];
}
interface Props {
  modelValue: PairingForm[];
  designations: LineOption[];
  definitions: LineOption[];
  /** 自引用上下文选项（上下文条目关联其自身；新建时值为哨兵 NIL UUID） */
  selfContextOption?: { id: string; label: string };
  /**
   * ISO/IEC 11179-3:2023 §8.4.1.4.1/§8.4.2.4.1：指称/定义可仅经
   * paired_designation/paired_definition 存在，无需先有独立行。
   * 传入回调后，下拉允许输入新文本，由宿主把新行挂到条目上并返回临时键。
   */
  createDesignation?: (value: string) => string;
  createDefinition?: (value: string) => string;
  /**
   * 就地编辑已关联行的文本：宿主按 id/clientKey 找到 form 中的行并写回 value。
   * 传入后文本框可编辑既有指称/定义内容（共享行，影响所有引用它的配对）。
   */
  updateDesignation?: (id: string, value: string) => void;
  updateDefinition?: (id: string, value: string) => void;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  'update:modelValue': [value: PairingForm[]];
}>();

const contextOptions = ref<{ id: string; label: string }[]>([]);
const searchingContexts = ref(false);

const touched = ref<Set<number>>(new Set());

const showEmpty = computed(() =>
  (!props.designations.length && !props.createDesignation)
  || (!props.definitions.length && !props.createDefinition),
);

const duplicateKeys = computed(() => {
  const keyMap = new Map<string, number[]>();
  props.modelValue.forEach((row, index) => {
    if (!row.designationId || !row.definitionId || !row.contextId) return;
    const key = `${row.designationId}|${row.definitionId}|${row.contextId}`;
    const indices = keyMap.get(key) || [];
    indices.push(index);
    keyMap.set(key, indices);
  });
  return keyMap;
});

function isDuplicate(index: number): boolean {
  const row = props.modelValue[index];
  if (!row || !row.designationId || !row.definitionId || !row.contextId) return false;
  const key = `${row.designationId}|${row.definitionId}|${row.contextId}`;
  const indices = duplicateKeys.value.get(key) || [];
  return indices.length > 1 && indices[0] !== index;
}

function duplicateIndex(index: number): number {
  const row = props.modelValue[index];
  if (!row || !row.designationId || !row.definitionId || !row.contextId) return -1;
  const key = `${row.designationId}|${row.definitionId}|${row.contextId}`;
  const indices = duplicateKeys.value.get(key) || [];
  return indices[0] ?? -1;
}

function hasError(index: number): boolean {
  return contextError(index) || duplicateError(index);
}

function contextError(index: number): boolean {
  return touched.value.has(index) && !props.modelValue[index]?.contextId;
}

function duplicateError(index: number): boolean {
  return touched.value.has(index) && isDuplicate(index);
}

function lineLabel(line: LineOption): string {
  return line.value || line.id;
}

function findDesignation(id: string): LineOption | undefined {
  return props.designations.find((d) => d.id === id);
}

function findDefinition(id: string): LineOption | undefined {
  return props.definitions.find((d) => d.id === id);
}

function designationText(row: PairingForm): string {
  return findDesignation(row.designationId || '')?.value ?? '';
}

function definitionText(row: PairingForm): string {
  return findDefinition(row.definitionId || '')?.value ?? '';
}

/** 文本框输入：已关联行 → 就地编辑写回宿主；未关联 → 视为新文本，交宿主建行走 clientKey。 */
function onDesignationText(row: PairingForm, val: string) {
  const line = findDesignation(row.designationId || '');
  if (line) {
    props.updateDesignation?.(line.id, val);
    return;
  }
  const v = (val || '').trim();
  if (v && props.createDesignation) {
    row.designationId = props.createDesignation(v);
  }
}

function onDefinitionText(row: PairingForm, val: string) {
  const line = findDefinition(row.definitionId || '');
  if (line) {
    props.updateDefinition?.(line.id, val);
    return;
  }
  const v = (val || '').trim();
  if (v && props.createDefinition) {
    row.definitionId = props.createDefinition(v);
  }
}

/** “引用既有”选择器：只切换行关联，选项均为既有 id；清空即解除关联。 */
function onDesignationLink(row: PairingForm, val: string) {
  row.designationId = val || '';
}

function onDefinitionLink(row: PairingForm, val: string) {
  row.definitionId = val || '';
}

function markTouched(index: number) {
  touched.value.add(index);
}

function validate(): boolean {
  touched.value = new Set(props.modelValue.map((_, i) => i));
  const designationIds = new Set(props.designations.map((d) => d.id));
  const definitionIds = new Set(props.definitions.map((d) => d.id));
  return props.modelValue.every((row, index) =>
    row.designationId && row.definitionId && row.contextId && !isDuplicate(index)
    && designationIds.has(row.designationId) && definitionIds.has(row.definitionId)
  );
}

onMounted(async () => {
  await searchContexts('');
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

function addRow() {
  const newRow: PairingForm = {
    designationId: '',
    definitionId: '',
    contextId: '',
    acceptability: 'ADMITTED',
  };
  const next: PairingForm[] = [...props.modelValue, newRow];
  touched.value.add(next.length - 1);
  emit('update:modelValue', next);
}

function removeRow(index: number) {
  const next = props.modelValue.filter((_, i) => i !== index);
  const nextTouched = new Set<number>();
  next.forEach((_, i) => {
    if (touched.value.has(i < index ? i : i + 1)) nextTouched.add(i);
  });
  touched.value = nextTouched;
  emit('update:modelValue', next);
}

defineExpose({ validate, hasError, isDuplicate });
</script>

<style scoped>
.pairing-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.pairing-empty {
  padding: 24px 0;
}
.pairing-card {
  background: #f8fafc;
  border-radius: 8px;
  padding: 12px;
  border: 1px solid #f1f5f9;
}
.pairing-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pairing-field {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
}
.pairing-link-select {
  width: 120px;
  flex-shrink: 0;
}
.pairing-link {
  color: #9ca3af;
  flex-shrink: 0;
}
.pairing-error {
  border-color: var(--el-color-danger);
  background: #fef2f2;
}
.pairing-error-text {
  color: var(--el-color-danger);
  font-size: 12px;
  margin-top: 4px;
}
</style>
