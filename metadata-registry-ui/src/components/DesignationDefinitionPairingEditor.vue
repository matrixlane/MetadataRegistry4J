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
          <!-- 已保存配对：指称/定义引用以纯文本展示（正常颜色、可选中复制、不可编辑） -->
          <div v-if="isRowSaved(row)" class="pairing-saved-text" style="flex: 1;">
            {{ savedLineText(row.designationId, designations) }}
          </div>
          <el-select
            v-else
            v-model="row.designationId"
            :placeholder="createDesignation ? '选择既有指称，或输入文本直接新建' : '选择指称'"
            size="small"
            style="flex: 1;"
            filterable
            :allow-create="!!createDesignation"
            default-first-option
            clearable
            @change="(v: string) => onDesignationChange(row, v)"
          >
            <el-option
              v-for="d in designations"
              :key="d.id"
              :label="lineLabel(d)"
              :value="d.id"
            />
          </el-select>
          <el-icon class="pairing-link"><Link /></el-icon>
          <div v-if="isRowSaved(row)" class="pairing-saved-text" style="flex: 1;">
            {{ savedLineText(row.definitionId, definitions) }}
          </div>
          <el-select
            v-else
            v-model="row.definitionId"
            :placeholder="createDefinition ? '选择既有定义，或输入文本直接新建' : '选择定义'"
            size="small"
            style="flex: 1;"
            filterable
            :allow-create="!!createDefinition"
            default-first-option
            clearable
            @change="(v: string) => onDefinitionChange(row, v)"
          >
            <el-option
              v-for="d in definitions"
              :key="d.id"
              :label="lineLabel(d)"
              :value="d.id"
            />
          </el-select>
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
            <template #label="{ label, value }">
              <span v-if="isPendingContext(value)">…</span>
              <span v-else>{{ label }}</span>
            </template>
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
          <el-tag v-if="row.id" size="small" type="info" class="pairing-saved-tag">已保存</el-tag>
          <el-button
            type="danger"
            size="small"
            text
            circle
            class="pairing-delete-button"
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
import { useDefaultContext } from '@/composables/useDefaultContext';
import type { PairingForm, TextLineContextForm } from '@/types/common';
import { ref, onMounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
const { t } = useI18n();

interface LineOption {
  id: string;
  value: string;
  languageCode?: string;
  /** 是否已持久化；false/缺省 = 本次会话内经配对内联新建的 clientKey 行（文本仍可在配对里改） */
  persisted?: boolean;
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
   * 行改绑/清除/删除后，若原引用是本次内联新建的 clientKey 行（未持久化）
   * 且不再被任何配对引用，由宿主把它从 form 中移除，避免孤行随条目保存。
   * 已持久化的指称/定义实例永不移除（只读规则：改实例须到指称/定义页签）。
   */
  unlinkDesignation?: (id: string) => void;
  unlinkDefinition?: (id: string) => void;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  'update:modelValue': [value: PairingForm[]];
  /** 删除已保存的配对（row.id 存在）：由宿主弹确认（仅删关联 / 连同指称定义一并删除）。 */
  'deleteSavedRow': [row: PairingForm, index: number];
}>();

const contextOptions = ref<{ id: string; label: string }[]>([]);
const searchingContexts = ref(false);
const { defaultContextId, load: loadDefaultContext } = useDefaultContext();
/** 首次远程加载完成前，未命中的选中值显示占位而非原始 id */
const contextsLoaded = ref(false);

function isPendingContext(value: unknown): boolean {
  if (!value || typeof value !== 'string') return false;
  if (contextsLoaded.value) return false;
  if (props.selfContextOption && props.selfContextOption.id === value) return false;
  return !contextOptions.value.some((c) => c.id === value);
}

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
  return line.value || t('common.deletedEntry');
}

/** 已保存配对引用的展示文本：从行集合中解析，缺失时显示占位（不回退 id）。 */
function savedLineText(id: string, lines: LineOption[]): string {
  const line = lines.find((l) => l.id === id);
  return line ? lineLabel(line) : t('common.deletedEntry');
}

function findDesignation(id: string): LineOption | undefined {
  return props.designations.find((d) => d.id === id);
}

function findDefinition(id: string): LineOption | undefined {
  return props.definitions.find((d) => d.id === id);
}

/** 已保存配对：指称/定义引用只读（实例文本不可在配对里改）；上下文与可接受性仍可编辑。 */
function isRowSaved(row: PairingForm): boolean {
  return !!row.id;
}

/** 改绑/清除/删除后清理孤立的内联新建行（clientKey 且不再被任何配对引用）。 */
function cleanupOrphanLine(
  prevId: string | undefined,
  lines: LineOption[],
  unlink: ((id: string) => void) | undefined,
  rows: PairingForm[] = props.modelValue,
) {
  if (!prevId || !unlink) return;
  const line = lines.find((d) => d.id === prevId);
  if (!line || line.persisted) return;
  const stillReferenced = rows.some(
    (r) => r.designationId === prevId || r.definitionId === prevId,
  );
  if (!stillReferenced) unlink(prevId);
}

/**
 * 合并控件（filterable + allow-create）：选中既有 id = 关联；输入新文本 =
 * 交宿主建行走 clientKey 并关联；清空 = 解除关联。持久化实例文本不可改——
 * 要改须到指称/定义页签，或清除后输入新文本（建新实例，旧实例不受影响）。
 */
function onDesignationChange(row: PairingForm, val: string) {
  const prev = row.designationId || '';
  const v = (val || '').trim();
  if (!v) {
    row.designationId = '';
  } else if (findDesignation(v)) {
    row.designationId = v;
  } else if (props.createDesignation) {
    row.designationId = props.createDesignation(v);
  } else {
    return;
  }
  cleanupOrphanLine(prev, props.designations, props.unlinkDesignation);
}

function onDefinitionChange(row: PairingForm, val: string) {
  const prev = row.definitionId || '';
  const v = (val || '').trim();
  if (!v) {
    row.definitionId = '';
  } else if (findDefinition(v)) {
    row.definitionId = v;
  } else if (props.createDefinition) {
    row.definitionId = props.createDefinition(v);
  } else {
    return;
  }
  cleanupOrphanLine(prev, props.definitions, props.unlinkDefinition);
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
  loadDefaultContext();
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
    contextsLoaded.value = true;
  }
}

function addRow() {
  const newRow: PairingForm = {
    designationId: '',
    definitionId: '',
    // 系统默认上下文（Registry Context）作为预填值，用户可变更
    contextId: defaultContextId.value || '',
    acceptability: 'ADMITTED',
  };
  const next: PairingForm[] = [...props.modelValue, newRow];
  touched.value.add(next.length - 1);
  emit('update:modelValue', next);
}

function removeRow(index: number) {
  const removed = props.modelValue[index];
  if (removed?.id) {
    // 已保存配对：删除须宿主确认（仅删关联 / 连同指称定义一并删除）
    emit('deleteSavedRow', removed, index);
    return;
  }
  const next = props.modelValue.filter((_, i) => i !== index);
  const nextTouched = new Set<number>();
  next.forEach((_, i) => {
    if (touched.value.has(i < index ? i : i + 1)) nextTouched.add(i);
  });
  touched.value = nextTouched;
  emit('update:modelValue', next);
  if (removed) {
    cleanupOrphanLine(removed.designationId, props.designations, props.unlinkDesignation, next);
    cleanupOrphanLine(removed.definitionId, props.definitions, props.unlinkDefinition, next);
  }
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
.pairing-saved-tag {
  flex-shrink: 0;
}
/* 已保存配对的指称/定义引用：纯文本展示，正常颜色、可选中复制、不可编辑 */
.pairing-saved-text {
  display: flex;
  align-items: center;
  min-height: 24px;
  padding: 0 8px;
  font-size: 14px;
  color: var(--el-text-color-primary);
  user-select: text;
  -webkit-user-select: text;
  overflow-wrap: anywhere;
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
