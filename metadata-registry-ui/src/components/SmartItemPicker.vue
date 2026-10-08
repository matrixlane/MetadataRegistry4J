<!--
  SmartItemPicker — 跨 Item 关联 picker 统一组件（smart-item-picker）。

  核心行为：
  - 默认展示业务标识符（VD.00001）而非 UUID
  - 远程搜索：用户停止输入 ≥250ms 触发 `/metadata/search`，AbortController 取消上一次
  - 类型 chip 多选过滤
  - 命名空间 scope：默认当前 / 全部 / 指定 namespace
  - Smart-query 解析：NS.ID 精确匹配 / 当前 namespace identifier / 中文模糊
  - 已选项永远在结果集头部
  - 单选 / 多选两种模式
  - 编辑态回填：value mount 时按 id 反查；回填完成前选中值显示加载占位而非原始 id
-->
<template>
  <el-select
    v-model="currentValue"
    :placeholder="resolvedPlaceholder"
    :disabled="disabled"
    :clearable="clearable"
    :filterable="true"
    :remote="true"
    :remote-method="onRemoteSearch"
    :loading="picker.loading.value"
    :multiple="allowMultiple"
    :collapse-tags="allowMultiple"
    collapse-tags-tooltip
    style="width: 100%;"
    :aria-label="resolvedPlaceholder"
    @visible-change="onVisibleChange"
    @change="onChange"
  >
    <template #header v-if="showAdvancedFilter">
      <div class="smart-picker-advanced">
        <div class="smart-picker-filter-row">
          <span class="smart-picker-filter-label">{{ t('smartPicker.typeFilter.label') }}:</span>
          <el-checkbox-group v-model="typeFilter" size="small">
            <el-checkbox-button
              v-for="type in availableTypes"
              :key="type"
              :value="type"
            >
              {{ typeLabel(type) }}
            </el-checkbox-button>
          </el-checkbox-group>
        </div>
      </div>
    </template>

    <el-option
      v-for="hit in picker.hits.value"
      :key="hit.id"
      :label="formatLabel(hit)"
      :value="hit.itemId"
    >
      <div class="smart-picker-option">
        <el-tag v-if="hit.itemType" size="small" :type="typeTagType(hit.itemType)">
          {{ typeShortLabel(hit.itemType) }}
        </el-tag>
        <span class="smart-picker-identifier">{{ hit.identifier || hit.itemLabel || t('common.unresolvedEntry') }}</span>
        <span class="smart-picker-sep" v-if="hit.itemLabel && hit.identifier && hit.itemLabel !== hit.identifier">──</span>
        <span class="smart-picker-label" v-if="hit.itemLabel && hit.itemLabel !== hit.identifier">{{ hit.itemLabel }}</span>
        <el-tag
          v-if="hit.matchType === 'PARTIAL'"
          size="small"
          type="warning"
          effect="plain"
          class="smart-picker-partial"
        >近似</el-tag>
        <span class="smart-picker-namespace" v-if="hit.namespaceLabel">{{ hit.namespaceLabel }}</span>
      </div>
    </el-option>

    <template #label="{ label, value }">
      <span v-if="isPendingValue(value)" class="smart-picker-pending">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>{{ t('smartPicker.loading') }}</span>
      </span>
      <span v-else-if="isUnresolvedValue(value)" class="smart-picker-unresolved">
        {{ t('common.unresolvedEntry') }}
      </span>
      <span v-else>{{ label }}</span>
    </template>

    <template #empty>
      <div class="smart-picker-empty">
        <template v-if="picker.loading.value">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>{{ t('smartPicker.loading') }}</span>
        </template>
        <template v-else>
          <span>{{ t('smartPicker.empty') }}</span>
        </template>
      </div>
    </template>
  </el-select>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue';
import { Loading } from '@element-plus/icons-vue';
import { useSmartPicker, type SmartPickerScope } from '@/composables/useSmartPicker';
import type { AdministeredItemSearchHit } from '@/types/common';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();

interface ActiveRegistrarLite {
  namespaceId?: string;
  namespaceShorthand?: string;
}

const props = defineProps<{
  modelValue?: string | string[] | null;
  /** 限制可选项类型；空数组 = 全部 */
  itemTypes?: string[];
  scope?: SmartPickerScope;
  /** 当前命名空间（fallback） */
  currentNamespace?: ActiveRegistrarLite | null;
  placeholder?: string;
  required?: boolean;
  disabled?: boolean;
  clearable?: boolean;
  /** 多选模式 */
  allowMultiple?: boolean;
  /** 是否展示类型 chip 高级过滤条 */
  showAdvancedFilter?: boolean;
  /** 特例：选中态与选项只显示名称（不拼标识符/版本/时间），如值域的数据类型下拉 */
  labelOnly?: boolean;
  /** 打开下拉且无已选值时，用空查询拉取一页默认选项（点击输入框即出下拉菜单） */
  browseOnOpen?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', v: string | string[] | null): void;
  (e: 'change', v: string | string[] | null, hit: AdministeredItemSearchHit | null): void;
}>();

const currentValue = ref<string | string[] | null>(props.modelValue ?? (props.allowMultiple ? [] : null));

watch(() => props.modelValue, (v) => {
  currentValue.value = v ?? (props.allowMultiple ? [] : null);
});

const nsForPicker = computed(() => {
  const c = props.currentNamespace;
  if (!c) return null;
  return { id: c.namespaceId, shorthandPrefix: c.namespaceShorthand };
});
const picker = useSmartPicker(nsForPicker.value as { id?: string; shorthandPrefix?: string } | null);

const typeFilter = ref<string[]>(props.itemTypes ?? []);
const selectedTypes = computed<string[]>(() => {
  const f = typeFilter.value.length > 0 ? typeFilter.value : (props.itemTypes ?? []);
  return f;
});

const availableTypes = computed<string[]>(() => {
  const set = new Set<string>(props.itemTypes ?? []);
  if (set.size === 0) {
    return ['DataElement', 'DataElementConcept', 'ConceptualDomain', 'ValueDomain',
            'ObjectClass', 'Property', 'DataType', 'UnitOfMeasure', 'Namespace'];
  }
  return Array.from(set);
});

const resolvedPlaceholder = computed(() => {
  if (props.placeholder) return props.placeholder;
  return t('smartPicker.placeholder.default');
});

const TYPE_SHORT_LABELS: Record<string, string> = {
  DataElement: 'DE',
  DataElementConcept: 'DEC',
  ConceptualDomain: 'CD',
  ValueDomain: 'VD',
  ObjectClass: 'OC',
  Property: 'P',
  DataType: 'DT',
  DataTypeScheme: 'DTS',
  UnitOfMeasure: 'UOM',
  MeasureClass: 'MC',
  Dimensionality: 'DIM',
  Namespace: 'NS',
  ConstraintSet: 'CS',
  ReferenceDocument: 'RD',
  ClassificationScheme: 'CSCH',
  Context: 'CTX',
  Organization: 'ORG',
  Individual: 'IND',
  Contact: 'CTC',
  DocumentType: 'DTY',
};

const TYPE_LABELS: Record<string, string> = {
  DataElement: '数据元',
  DataElementConcept: '数据元概念',
  ConceptualDomain: '概念域',
  ValueDomain: '值域',
  ObjectClass: '对象类',
  Property: '特性',
  DataType: '数据类型',
  DataTypeScheme: '数据类型方案',
  UnitOfMeasure: '计量单位',
  MeasureClass: '计量类',
  Dimensionality: '维度',
  Namespace: '命名空间',
  ConstraintSet: '约束集',
  ReferenceDocument: '引用文档',
  ClassificationScheme: '分类方案',
  Context: '上下文',
  Organization: '组织',
  Individual: '个人',
  Contact: '联系人',
  DocumentType: '文档类型',
};

function typeShortLabel(t?: string | null): string {
  if (!t) return '';
  return TYPE_SHORT_LABELS[t] || t;
}

function typeLabel(t: string): string {
  return TYPE_LABELS[t] || t;
}

function typeTagType(t?: string | null): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  if (!t) return 'info';
  if (t.includes('Enumerated') || t === 'DataElement') return 'success';
  if (t === 'Namespace') return 'primary';
  if (t.startsWith('Reference')) return 'warning';
  return 'info';
}

/**
 * 选中态与选项标签：「业务标识 | 指称」。
 * 界面禁显内部 UUID，且不含版本号/时间戳等次要信息（全局统一格式）。
 */
function formatLabel(hit: AdministeredItemSearchHit): string {
  if (props.labelOnly) {
    return hit.itemLabel || hit.identifier || t('common.unresolvedEntry');
  }
  const parts: string[] = [];
  if (hit.identifier) parts.push(hit.identifier);
  if (hit.itemLabel && hit.itemLabel !== hit.identifier) parts.push(hit.itemLabel);
  return parts.length > 0 ? parts.join(' | ') : t('common.unresolvedEntry');
}

function doSearch(q: string) {
  const parsed = picker.parseQuery(q);
  if (parsed.mode === 'exact') {
    picker.search({
      exact: { identifier: parsed.identifier, namespaceShorthand: parsed.namespaceShorthand },
      type: selectedTypes.value.length > 0 ? selectedTypes.value : undefined,
      scope: props.scope ?? 'current',
    });
  } else {
    picker.search({
      q: parsed.q,
      type: selectedTypes.value.length > 0 ? selectedTypes.value : undefined,
      scope: props.scope ?? 'current',
    });
  }
}

function onRemoteSearch(q: string) {
  if (!q || q.trim() === '') {
    picker.clear();
    return;
  }
  doSearch(q);
}

function onVisibleChange(open: boolean) {
  if (!open) return;
  if (props.browseOnOpen && !currentValue.value && picker.hits.value.length === 0) {
    doSearch('');
  }
  if (currentValue.value && picker.hits.value.length === 0) {
    const ids = Array.isArray(currentValue.value) ? currentValue.value : [currentValue.value];
    for (const id of ids) {
      if (id && typeof id === 'string') {
        picker.fetchById(id).then((hit) => {
          if (hit) {
      picker.hits.value = [hit, ...picker.hits.value.filter((h) => h.itemId !== hit.itemId)];
          }
        });
      }
    }
  }
}

function onChange(v: string | string[] | null) {
  currentValue.value = v;
  emit('update:modelValue', v);
  if (!v || (Array.isArray(v) && v.length === 0)) {
    emit('change', v, null);
    return;
  }
  const id = Array.isArray(v) ? v[0] : v;
const hit = picker.hits.value.find((h) => h.itemId === id || h.id === id);
  if (hit) {
    selectedLabels.set(hit.itemId, formatLabel(hit));
    emit('change', v, hit);
  } else if (typeof id === 'string') {
    picker.fetchById(id).then((found) => {
      if (found) selectedLabels.set(found.itemId, formatLabel(found));
      emit('change', v, found);
    });
  } else {
    emit('change', v, null);
  }
}

/** 回填完成前，未命中选项的选中值不渲染原始 id，改显示加载占位。 */
const backfillDone = ref(false);

/** 已选值的展示标签缓存：勾选/回填命中时记录；始终未命中的值不回显原始 id */
const selectedLabels = reactive(new Map<string, string>());

function isPendingValue(value: unknown): boolean {
  if (!value || typeof value !== 'string') return false;
  if (backfillDone.value) return false;
  return !picker.hits.value.some((h) => h.itemId === value || h.id === value);
}

function isUnresolvedValue(value: unknown): boolean {
  if (!value || typeof value !== 'string') return false;
  if (!backfillDone.value) return false;
  if (picker.hits.value.some((h) => h.itemId === value || h.id === value)) return false;
  return !selectedLabels.has(value);
}

onMounted(async () => {
  if (!currentValue.value) {
    backfillDone.value = true;
    return;
  }
  const ids = (Array.isArray(currentValue.value) ? currentValue.value : [currentValue.value]).filter(
    (x): x is string => !!x
  );
  // 串行反查：useSmartPicker 的 fetchById 会 abort 上一次未完成请求，
  // 并发 Promise.all 会导致只有最后一个 id 反查成功（多选编辑出现"未解析项目"的根因）。
  const found: AdministeredItemSearchHit[] = [];
  for (const id of ids) {
    const hit = await picker.fetchById(id);
    if (hit) {
      found.push(hit);
      selectedLabels.set(hit.itemId, formatLabel(hit));
    }
  }
  if (found.length > 0) {
    // 反查命中写入 options（picker.hits），多选 tag 由 EP 从 options 解析标签
    picker.hits.value = found;
  }
  backfillDone.value = true;
});
</script>

<style scoped>
.smart-picker-advanced {
  padding: 4px 8px;
}
.smart-picker-filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.smart-picker-filter-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.smart-picker-option {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 13px;
}
.smart-picker-identifier {
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-weight: 600;
}
.smart-picker-sep {
  color: var(--el-text-color-placeholder);
}
.smart-picker-label {
  color: var(--el-text-color-regular);
}
.smart-picker-namespace {
  margin-left: auto;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.smart-picker-partial {
  margin-left: 4px;
}
.smart-picker-meta {
  flex-basis: 100%;
  margin-left: 28px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.smart-picker-pending {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.smart-picker-unresolved {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-style: italic;
}
.smart-picker-empty {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
