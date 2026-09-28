<template>
  <div class="multilingual-editor">
    <div
      v-for="(row, index) in modelValue"
      :key="index"
      class="multilingual-card"
    >
      <!-- 正文内容为主区域：占满整行、字号更大，便于阅读与编辑 -->
      <div class="multilingual-row value-row">
        <el-input
          v-model="row.value"
          :type="multiline ? 'textarea' : 'text'"
          :autosize="multiline ? { minRows: 2, maxRows: 8 } : undefined"
          :placeholder="$t('multilingual.valuePlaceholder', { label })"
          class="value-input"
        />
        <div class="row-actions">
          <el-tooltip
            v-if="aiGenerate"
            :content="$t('aiGenerate.button')"
            placement="top"
          >
            <el-button
              size="small"
              text
              circle
              type="primary"
              data-testid="ai-generate-btn"
              @click="emit('ai-generate', index)"
              @mouseenter="emit('ai-generate-hover', true, index)"
              @mouseleave="emit('ai-generate-hover', false, index)"
            >
              <el-icon><MagicStick /></el-icon>
            </el-button>
          </el-tooltip>
          <el-tooltip
            v-if="isIsolated(index)"
            :content="row.isolatedException ? '已标记为例外' : '无上下文且未被配对引用，点击标记为例外'"
            placement="top"
          >
            <el-button
              size="small"
              text
              circle
              :type="row.isolatedException ? 'success' : 'warning'"
              @click="toggleException(index)"
              data-testid="isolated-toggle"
            >
              <el-icon><Warning /></el-icon>
            </el-button>
          </el-tooltip>
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
      </div>

      <!-- 语言与来源文档为次要元数据：置于正文下方，弱化显示 -->
      <div class="meta-row">
        <span class="meta-label">{{ $t('multilingual.languagePlaceholder') }}</span>
        <el-select
          v-model="row.languageId"
          :placeholder="$t('multilingual.languagePlaceholder')"
          style="width: 130px; flex-shrink: 0;"
          size="small"
          class="meta-select"
        >
          <template #label="{ label, value }">
            <span v-if="isPendingOption(value, languages, languagesLoaded)">…</span>
            <span v-else>{{ label }}</span>
          </template>
          <el-option
            v-for="lang in languages"
            :key="lang.id"
            :label="lang.languageIdentifier"
            :value="lang.id"
          />
        </el-select>
        <span class="meta-label">{{ $t('multilingual.sourcePlaceholder') }}</span>
        <el-select
          v-model="row.sourceId"
          :placeholder="$t('multilingual.sourcePlaceholder')"
          size="small"
          style="width: 200px; flex-shrink: 0;"
          clearable
          class="meta-select"
        >
          <template #label="{ label, value }">
            <span v-if="isPendingOption(value, referenceDocuments, refDocsLoaded)">…</span>
            <span v-else>{{ label }}</span>
          </template>
          <el-option
            v-for="doc in referenceDocuments"
            :key="doc.id"
            :label="doc.title || doc.identifier || '—'"
            :value="doc.id"
          />
        </el-select>
      </div>

      <div class="context-section">
        <div class="context-header" @click="toggleExpand(index)">
          <el-icon size="12"><component :is="expanded[index] ? ArrowDown : ArrowRight" /></el-icon>
          <span>{{ $t('multilingual.contextAssociation') }}</span>
          <el-tag v-if="row.contexts?.length" size="small" type="info" round style="margin-left: 8px;">
            {{ row.contexts.length }}
          </el-tag>
          <el-tag
            v-if="namingConventionOptions && namingConventionOptions.length > 0"
            size="small"
            :type="(row.namingConventionIds || []).length > 0 ? 'success' : (requireNamingConventions ? 'danger' : 'info')"
            round
            style="margin-left: 8px;"
          >
            {{ $t('multilingual.namingConvention') }} {{ (row.namingConventionIds || []).length }}
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
                :placeholder="$t('multilingual.contextPlaceholder')"
                size="small"
                style="width: 180px;"
                filterable
                remote
                reserve-keyword
                :remote-method="searchContexts"
                :loading="searchingContexts"
              >
                <template #label="{ label, value }">
                  <span v-if="isPendingOption(value, contextOptions, contextsLoaded)">…</span>
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
                v-model="ctx.acceptability"
                :placeholder="$t('multilingual.acceptabilityPlaceholder')"
                size="small"
                style="width: 140px;"
                @change="updateConflicts"
              >
                <el-option :label="$t('acceptability.PREFERRED')" value="PREFERRED" />
                <el-option :label="$t('acceptability.ADMITTED')" value="ADMITTED" />
                <el-option :label="$t('acceptability.DEPRECATED')" value="DEPRECATED" />
                <el-option :label="$t('acceptability.SUPERSEDED')" value="SUPERSEDED" />
                <el-option :label="$t('acceptability.OBSOLETE')" value="OBSOLETE" />
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
            <div v-if="namingConventionOptions && namingConventionOptions.length > 0" class="naming-convention-row">
              <span class="nc-label">{{ $t('multilingual.namingConvention') }}</span>
              <el-select
                v-model="row.namingConventionIds"
                multiple
                collapse-tags
                collapse-tags-tooltip
                :placeholder="$t('multilingual.namingConventionPlaceholder')"
                size="small"
                style="flex: 1;"
                :class="{ 'is-error': requireNamingConventions && !(row.namingConventionIds || []).length }"
              >
                <el-option
                  v-for="nc in namingConventionOptions"
                  :key="nc.id"
                  :label="nc.label || nc.scopeRule || '—'"
                  :value="nc.id"
                />
              </el-select>
            </div>
            <el-button
              type="primary"
              size="small"
              text
              @click="addContext(index)"
            >
              <el-icon><Plus /></el-icon>
              {{ $t('multilingual.addContext') }}
            </el-button>
          </div>
        </el-collapse-transition>
      </div>
    </div>
    <el-button
      class="add-row-button"
      :class="{ 'is-empty': !modelValue.length }"
      type="primary"
      size="small"
      text
      @click="addRow"
    >
      <el-icon><Plus /></el-icon>
      {{ $t('multilingual.addLabel', { label }) }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
const { t } = useI18n();
import { Plus, Delete, ArrowRight, ArrowDown, Warning, MagicStick } from '@element-plus/icons-vue';
import { languageApi, contextApi, referenceDocumentApi } from '@/api/resources';
import { useDefaultContext } from '@/composables/useDefaultContext';
import type { TextLineForm, PairingForm, NamingConventionResponse } from '@/types/common';

interface Props {
  modelValue: TextLineForm[];
  label: string;
  multiline?: boolean;
  namingConventionOptions?: NamingConventionResponse[];
  requireNamingConventions?: boolean;
  pairings?: PairingForm[];
  defaultLanguageId?: string;
  /** 自引用上下文选项（上下文条目关联其自身；新建时值为哨兵 NIL UUID） */
  selfContextOption?: { id: string; label: string };
  /** 行内「AI 生成」按钮（字段级生成入口；宿主负责门控与浮层） */
  aiGenerate?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  multiline: false,
});
const emit = defineEmits<{
  'update:modelValue': [value: TextLineForm[]];
  conflicts: [conflicts: { type: 'PREFERRED' | 'ISOLATED'; message: string; index: number }[]];
  'ai-generate': [index: number];
  'ai-generate-hover': [hovered: boolean, index: number];
}>();

const languages = ref<{ id: string; languageIdentifier: string }[]>([]);
const contextOptions = ref<{ id: string; label: string }[]>([]);
const referenceDocuments = ref<{ id: string; title?: string; identifier?: string }[]>([]);
const { defaultContextId, load: loadDefaultContext } = useDefaultContext();
const languagesLoaded = ref(false);
const refDocsLoaded = ref(false);
const searchingContexts = ref(false);
const contextsLoaded = ref(false);
const expanded = ref<Record<number, boolean>>({});

/** 选项列表加载完成前，未命中的选中值显示加载占位而非原始 id */
function isPendingOption(value: unknown, options: { id: string }[], done: boolean): boolean {
  if (!value || typeof value !== 'string') return false;
  if (done) return false;
  return !options.some((o) => o.id === value);
}

function isIsolated(index: number): boolean {
  const row = props.modelValue[index];
  if (!row) return false;
  if (!row.value?.trim()) return false;
  if (row.contexts && row.contexts.length > 0) return false;
  const key = row.id || row.clientKey;
  return !(props.pairings || []).some((p) => p.designationId === key || p.definitionId === key);
}

function preferredConflicts() {
  const conflicts: { type: 'PREFERRED'; message: string; index: number }[] = [];
  const keyMap = new Map<string, number[]>();
  props.modelValue.forEach((row, index) => {
    if (!row.contexts) return;
    row.contexts.forEach((ctx) => {
      if (ctx.acceptability === 'PREFERRED') {
        const key = `${row.languageId}|${ctx.contextId}`;
        const indices = keyMap.get(key) || [];
        indices.push(index);
        keyMap.set(key, indices);
      }
    });
  });
  keyMap.forEach((indices) => {
    if (indices.length > 1) {
      indices.forEach((index) => {
        conflicts.push({ type: 'PREFERRED', message: '同一语言+上下文下存在多个首选', index });
      });
    }
  });
  return conflicts;
}

function isolatedWarnings() {
  return props.modelValue
    .map((row, index) => ({ row, index }))
    .filter(({ row, index }) => isIsolated(index) && !row.isolatedException)
    .map(({ index }) => ({ type: 'ISOLATED' as const, message: '该行无上下文且未被配对引用', index }));
}

function updateConflicts() {
  emit('conflicts', [...preferredConflicts(), ...isolatedWarnings()]);
}

function toggleException(index: number) {
  const next = [...props.modelValue];
  next[index] = { ...next[index], isolatedException: !next[index].isolatedException };
  emit('update:modelValue', next);
  updateConflicts();
}

onMounted(async () => {
  loadDefaultContext();
  try {
    const langRes = await languageApi.list({ size: 100 });
    languages.value = (langRes.content || []).map((l: any) => ({
      id: l.id,
      languageIdentifier: l.languageIdentifier || l.identifier || '',
    }));
    await searchContexts('');
  } catch {
    // fallback
  } finally {
    languagesLoaded.value = true;
  }
  try {
    const refRes = await referenceDocumentApi.list({ size: 200 });
    referenceDocuments.value = (refRes.content || []).map((d: any) => ({
      id: d.id,
      title: d.title,
      identifier: d.identifier,
    }));
  } catch {
    // fallback
  } finally {
    refDocsLoaded.value = true;
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
    contextsLoaded.value = true;
  }
}

function toggleExpand(index: number) {
  expanded.value[index] = !expanded.value[index];
}

/** 新建行的前端临时键：未持久化行没有后端 id，配对功能以此引用该行。 */
function newClientKey(): string {
  return typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `ck-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function addRow() {
  const next = [...props.modelValue, {
    clientKey: newClientKey(),
    languageId: props.defaultLanguageId || '',
    value: '',
    isolatedException: false,
    // 系统默认上下文（Registry Context）作为预填值，用户可变更
    contexts: defaultContextId.value
      ? [{ contextId: defaultContextId.value, acceptability: 'ADMITTED' as const }]
      : [],
  }];
  emit('update:modelValue', next);
  updateConflicts();
}

function removeRow(index: number) {
  const next = props.modelValue.filter((_, i) => i !== index);
  emit('update:modelValue', next);
  updateConflicts();
}

function addContext(rowIndex: number) {
  const next = [...props.modelValue];
  if (!next[rowIndex].contexts) {
    next[rowIndex].contexts = [];
  }
  next[rowIndex].contexts!.push({ contextId: defaultContextId.value || '', acceptability: 'ADMITTED' });
  emit('update:modelValue', next);
  updateConflicts();
  expanded.value[rowIndex] = true;
}

function removeContext(rowIndex: number, ctxIndex: number) {
  const next = [...props.modelValue];
  next[rowIndex].contexts!.splice(ctxIndex, 1);
  emit('update:modelValue', next);
  updateConflicts();
}

defineExpose({ isIsolated, preferredConflicts, isolatedWarnings, updateConflicts });
</script>

<style scoped>
.multilingual-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
  align-self: stretch;
  width: 100%;
}
.multilingual-card {
  align-self: stretch;
  background: #f8fafc;
  border-radius: 8px;
  padding: 10px 12px;
  border: 1px solid #f1f5f9;
}
.add-row-button.is-empty {
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
.add-row-button.is-empty:hover {
  border-color: var(--el-color-primary);
  background: #f0f7f2;
}
.multilingual-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.value-input {
  flex: 1;
  font-size: 14px;
}
.value-input :deep(textarea.el-textarea__inner) {
  line-height: 1.6;
}
.row-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  margin-top: 4px;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e2e8f0;
}
.meta-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
.meta-select {
  opacity: 0.9;
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
.naming-convention-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e2e8f0;
}
.nc-label {
  font-size: 12px;
  color: #6b7280;
  white-space: nowrap;
}
.is-error .el-input__wrapper {
  box-shadow: 0 0 0 1px #f56c6c inset;
}
</style>
*** End of File
