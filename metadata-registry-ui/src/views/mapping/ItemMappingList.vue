<template>
  <div class="item-mapping-list">
    <!-- Header -->
    <div class="header-section">
      <div class="header-left">
        <h2>{{ $t('itemMapping.title') }}</h2>
      </div>
      <div class="header-right">
        <el-button type="primary" :icon="Plus" @click="openCreate">
          {{ $t('itemMapping.create') }}
        </el-button>
      </div>
    </div>

    <!-- Filters -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item :label="$t('itemMapping.subject')">
          <div style="width: 280px">
            <SmartItemPicker
              v-model="filters.subjectItemId"
              :scope="'all'"
              clearable
              :placeholder="$t('itemMapping.subjectPlaceholder')"
            />
          </div>
        </el-form-item>
        <el-form-item :label="$t('itemMapping.degreeLabel')">
          <el-select v-model="filters.degree" clearable style="width: 220px" :placeholder="$t('common.pleaseSelect')">
            <el-option v-for="d in DEGREES" :key="d" :value="d" :label="degreeLabel(d)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button :icon="RefreshLeft" @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card shadow="never" class="table-card">
      <div class="batch-bar">
        <span class="batch-bar-text">{{ $t('common.selectedCount', { count: selectedCount }) }}</span>
        <el-button
          type="danger"
          :icon="Delete"
          :disabled="selectedCount === 0 || batchDeleting"
          :loading="batchDeleting"
          @click="handleBatchDelete"
        >
          {{ $t('common.batchDelete') }}
        </el-button>
      </div>
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="rows"
        stripe
        row-key="id"
        class="modern-table"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column :label="$t('itemMapping.subjects')" min-width="160">
          <template #default="{ row }">
            <el-tooltip effect="light" placement="top" popper-class="endpoint-tip-popper">
              <template #content>
                <div class="endpoint-tip-list" :class="{ scrollable: row.subjects.length > 15 }">
                  <div v-for="(ep, i) in row.subjects" :key="i" class="endpoint-tip-line">
                    {{ labelFor(ep.itemId) }}
                  </div>
                </div>
              </template>
              <div class="endpoint-cell">
                <el-tag v-for="(ep, i) in row.subjects" :key="i" size="small" class="endpoint-tag">
                  {{ endpointTagText(ep) }}
                </el-tag>
              </div>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column :label="$t('itemMapping.degreeLabel')" width="180" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="degreeTagType(row.degree)">{{ degreeLabel(row.degree) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('itemMapping.objects')" min-width="160">
          <template #default="{ row }">
            <el-tooltip effect="light" placement="top" popper-class="endpoint-tip-popper">
              <template #content>
                <div class="endpoint-tip-list" :class="{ scrollable: row.objects.length > 15 }">
                  <div v-for="(ep, i) in row.objects" :key="i" class="endpoint-tip-line">
                    {{ labelFor(ep.itemId) }}
                  </div>
                </div>
              </template>
              <div class="endpoint-cell">
                <el-tag v-for="(ep, i) in row.objects" :key="i" size="small" class="endpoint-tag">
                  {{ endpointTagText(ep) }}
                </el-tag>
              </div>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column :label="$t('itemMapping.transformationRule')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.transformationRule" class="rule-text">{{ row.transformationRule }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('itemMapping.registrationStatus')" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.registrationStatus" size="small" type="info">
              {{ $t(`registrationStatus.${row.registrationStatus}`) }}
            </el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.action')" width="220" align="center">
          <template #default="{ row }">
            <div class="action-group">
              <el-button size="small" type="primary" text :icon="Edit" @click="openEdit(row)">
                {{ $t('common.edit') }}
              </el-button>
              <el-button size="small" type="warning" text :icon="Share" @click="openHierarchy(row)">
                {{ $t('itemMapping.hierarchy') }}
              </el-button>
              <el-button size="small" type="danger" text :icon="Delete" @click="handleDelete(row)">
                {{ $t('common.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="$t('itemMapping.empty')" />
        </template>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- Create / Edit dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? $t('itemMapping.edit') : $t('itemMapping.create')"
      width="680px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="140px">
        <el-form-item :label="$t('itemMapping.subjects')" prop="subjects">
          <div class="endpoint-block">
            <SmartItemPicker
              v-model="subjectPickerValue"
              :scope="'all'"
              allow-multiple
              :placeholder="$t('itemMapping.subjectsPlaceholder')"
            />
            <div v-for="ep in form.subjects" :key="ep.itemId" class="endpoint-row">
              <el-button
                size="small"
                type="danger"
                text
                :icon="Close"
                class="endpoint-remove"
                :aria-label="$t('itemMapping.removeEndpoint')"
                @click="removeEndpoint(form.subjects, ep.itemId)"
              />
              <span class="endpoint-label">{{ labelFor(ep.itemId) }}</span>
              <el-input
                v-model="ep.variableName"
                size="small"
                clearable
                :placeholder="$t('itemMapping.subjectVariableNamePerItem')"
              />
            </div>
          </div>
        </el-form-item>
        <el-form-item :label="$t('itemMapping.objects')" prop="objects">
          <div class="endpoint-block">
            <SmartItemPicker
              v-model="objectPickerValue"
              :scope="'all'"
              allow-multiple
              :placeholder="$t('itemMapping.objectsPlaceholder')"
            />
            <div v-for="ep in form.objects" :key="ep.itemId" class="endpoint-row">
              <el-button
                size="small"
                type="danger"
                text
                :icon="Close"
                class="endpoint-remove"
                :aria-label="$t('itemMapping.removeEndpoint')"
                @click="removeEndpoint(form.objects, ep.itemId)"
              />
              <span class="endpoint-label">{{ labelFor(ep.itemId) }}</span>
              <el-input
                v-model="ep.variableName"
                size="small"
                clearable
                :placeholder="$t('itemMapping.subjectVariableNamePerItem')"
              />
            </div>
          </div>
        </el-form-item>
        <el-form-item :label="$t('itemMapping.degreeLabel')" prop="degree">
          <el-select v-model="form.degree" style="width: 100%" :placeholder="$t('common.pleaseSelect')">
            <el-option v-for="d in DEGREES" :key="d" :value="d" :label="degreeLabel(d)" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('itemMapping.transformationRule')" prop="transformationRule">
          <el-input v-model="form.transformationRule" type="textarea" :rows="3" clearable />
        </el-form-item>
        <el-form-item :label="$t('itemMapping.transformationRuleNotation')" prop="transformationRuleNotation">
          <el-input v-model="form.transformationRuleNotation" clearable />
        </el-form-item>
        <el-form-item :label="$t('common.description')" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- Hierarchy dialog -->
    <el-dialog
      v-model="hierarchyVisible"
      :title="$t('itemMapping.hierarchyTitle')"
      width="560px"
      destroy-on-close
    >
      <div v-loading="hierarchyLoading" class="hierarchy-body">
        <template v-if="hierarchyRow">
          <el-descriptions :column="1" border size="small" class="hierarchy-desc">
            <el-descriptions-item :label="$t('itemMapping.currentMapping')">
              {{ mappingSummary(hierarchyRow) }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('itemMapping.superset')">
              <span v-if="hierarchyCurrentSuperset" class="endpoint-text">{{ hierarchyCurrentSuperset }}</span>
              <span v-else class="muted">{{ $t('itemMapping.noSuperset') }}</span>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('itemMapping.subsets')">
              <template v-if="hierarchySubsets.length > 0">
                <div v-for="(s, i) in hierarchySubsets" :key="i" class="endpoint-text">{{ s }}</div>
              </template>
              <span v-else class="muted">{{ $t('itemMapping.noSubsets') }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-form label-width="140px" style="margin-top: 16px">
            <el-form-item :label="$t('itemMapping.setSuperset')">
              <el-select
                v-model="hierarchySupersetId"
                clearable
                style="width: 100%"
                :placeholder="$t('itemMapping.supersetPlaceholder')"
              >
                <el-option
                  v-for="m in hierarchyCandidates"
                  :key="m.id"
                  :value="m.id"
                  :label="mappingSummary(m)"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </template>
      </div>
      <template #footer>
        <el-button @click="hierarchyVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="hierarchySaving" @click="handleSaveHierarchy">
          {{ $t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { Plus, Search, RefreshLeft, Edit, Delete, Share, Close } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import SmartItemPicker from '@/components/SmartItemPicker.vue';
import { itemMappingApi } from '@/api/resources';
import { useItemLabelCache } from '@/composables/useItemLabelCache';
import { useBatchDelete } from '@/composables/useBatchDelete';
import type {
  ItemMappingResponse,
  ItemMappingRequest,
  MappingDegree,
  PageQuery,
  PageResponse,
} from '@/types/common';

const { t } = useI18n();
const { requestAll: requestLabels, labelFor, designationFor } = useItemLabelCache();

const DEGREES: MappingDegree[] = [
  'related to',
  'same as',
  'semantically equivalent',
  'semantically similar',
  'derived from',
  'has derivation',
  'broader than',
  'narrower than',
  'child of',
  'parent of',
  'contains',
  'contained in',
  'instance of',
  'has instance',
];

/** i18n 键用大写枚举名，传输送值用标准小写值 */
function degreeName(d: MappingDegree): string {
  return t(`itemMapping.degree.${d.replace(/ /g, '_').toUpperCase()}`);
}

function degreeLabel(d: MappingDegree): string {
  return `${degreeName(d)} (${d})`;
}

function degreeTagType(d: MappingDegree): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  if (d === 'same as' || d === 'semantically equivalent') return 'success';
  if (d === 'related to') return 'info';
  if (d === 'semantically similar') return 'primary';
  return 'warning';
}

/** 表格单元格 tag 文本：仅指称（缺指称回退业务标识）；有变量名按现有格式后缀 */
function endpointTagText(ep: { itemId: string; variableName: string | null }): string {
  const designation = designationFor(ep.itemId);
  if (ep.variableName) return `${ep.variableName} (${designation})`;
  return designation;
}

/** 项目端点展示（摘要/确认消息）：变量名 + 「标识 | 指称」 */
function endpointText(ep: { itemId: string; variableName: string | null }): string {
  const label = labelFor(ep.itemId);
  if (ep.variableName) return `${ep.variableName} (${label})`;
  return label;
}

/** 表单端点行：仅保留 itemId + 变量名（展示标签经共享缓存反查） */
interface FormEndpoint {
  itemId: string;
  variableName: string;
}

/** 多选变化合并：按 itemId 保留既有 variableName，移除未勾选项 */
function mergeEndpoints(list: FormEndpoint[], ids: string | (string | null)[] | null | undefined): void {
  const raw = ids == null ? [] : Array.isArray(ids) ? ids : [ids];
  const next = raw.filter((id): id is string => !!id);
  const prev = new Map(list.map((ep) => [ep.itemId, ep]));
  list.splice(
    0,
    list.length,
    ...next.map((itemId) => ({
      itemId,
      variableName: prev.get(itemId)?.variableName ?? '',
    }))
  );
  requestLabels(next);
}

/** 行内 ✕ 快捷移除：从多选值中剔除该项目（变量名一并丢弃） */
function removeEndpoint(list: FormEndpoint[], itemId: string): void {
  const idx = list.findIndex((ep) => ep.itemId === itemId);
  if (idx >= 0) list.splice(idx, 1);
  formRef.value?.validateField(list === form.subjects ? 'subjects' : 'objects').catch(() => undefined);
}

function mappingSummary(m: ItemMappingResponse): string {
  const subj = m.subjects.map(endpointText).join(', ');
  const obj = m.objects.map(endpointText).join(', ');
  return `${subj} —[${degreeName(m.degree)}]→ ${obj}`;
}

// ── List state ────────────────────────────────────────────────────
const rows = ref<ItemMappingResponse[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);
const tableRef = ref<{ clearSelection?: () => void }>();
const filters = reactive<{ subjectItemId: string | null; degree: MappingDegree | '' }>({
  subjectItemId: null,
  degree: '',
});

const {
  selectedCount,
  batchDeleting,
  handleSelectionChange,
  handleBatchDelete,
} = useBatchDelete({
  deleteFn: (id) => itemMappingApi.delete(id),
  reload: fetchData,
  getTableRef: () => tableRef.value,
});

async function fetchData() {
  loading.value = true;
  try {
    const params: PageQuery & { subjectItemId?: string; degree?: string } = {
      page: page.value - 1,
      size: size.value,
      sort: 'id,desc',
    };
    if (filters.subjectItemId) params.subjectItemId = filters.subjectItemId;
    if (filters.degree) params.degree = filters.degree;
    const res: PageResponse<ItemMappingResponse> = await itemMappingApi.list(params);
    rows.value = res.content || [];
    total.value = res.totalElements || 0;
    requestLabels(rows.value.flatMap((r) => [...r.subjects, ...r.objects].map((ep) => ep.itemId)));
  } catch (err: any) {
    ElMessage.error(err?.message || t('itemMapping.loadError'));
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  page.value = 1;
  fetchData();
}

function handleReset() {
  filters.subjectItemId = null;
  filters.degree = '';
  handleSearch();
}

// ── Create / Edit ─────────────────────────────────────────────────
const dialogVisible = ref(false);
const saving = ref(false);
const editingId = ref<string | null>(null);
const formRef = ref<FormInstance>();
const form = reactive({
  subjects: [] as FormEndpoint[],
  objects: [] as FormEndpoint[],
  degree: '' as MappingDegree | '',
  transformationRule: '',
  transformationRuleNotation: '',
  description: '',
});

/** SmartItemPicker 多选 v-model 桥：勾选变化合并进 subjects/objects 并保留变量名 */
const subjectPickerValue = computed<string | string[] | null>({
  get: () => form.subjects.map((s) => s.itemId),
  set: (v) => {
    mergeEndpoints(form.subjects, v);
    formRef.value?.validateField('subjects').catch(() => undefined);
  },
});
const objectPickerValue = computed<string | string[] | null>({
  get: () => form.objects.map((s) => s.itemId),
  set: (v) => {
    mergeEndpoints(form.objects, v);
    formRef.value?.validateField('objects').catch(() => undefined);
  },
});

const formRules: FormRules = {
  subjects: [{
    validator: (_rule, _value, callback) =>
      form.subjects.length > 0 ? callback() : callback(new Error(t('itemMapping.subjectsRequired'))),
    trigger: 'change',
  }],
  objects: [{
    validator: (_rule, _value, callback) =>
      form.objects.length > 0 ? callback() : callback(new Error(t('itemMapping.objectsRequired'))),
    trigger: 'change',
  }],
  degree: [{ required: true, message: () => t('itemMapping.degreeRequired'), trigger: 'change' }],
};

function toFormEndpoints(eps: { itemId: string; variableName: string | null }[]): FormEndpoint[] {
  requestLabels(eps.map((ep) => ep.itemId));
  return eps.map((ep) => ({
    itemId: ep.itemId,
    variableName: ep.variableName ?? '',
  }));
}

function openCreate() {
  editingId.value = null;
  dialogVisible.value = true;
}

function openEdit(row: ItemMappingResponse) {
  editingId.value = row.id;
  form.subjects = toFormEndpoints(row.subjects);
  form.objects = toFormEndpoints(row.objects);
  form.degree = row.degree;
  form.transformationRule = row.transformationRule ?? '';
  form.transformationRuleNotation = row.transformationRuleNotation ?? '';
  form.description = row.description ?? '';
  dialogVisible.value = true;
}

function resetForm() {
  editingId.value = null;
  form.subjects = [];
  form.objects = [];
  form.degree = '';
  form.transformationRule = '';
  form.transformationRuleNotation = '';
  form.description = '';
  formRef.value?.clearValidate();
}

async function handleSave() {
  if (!formRef.value) return;
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    const payload: ItemMappingRequest = {
      subjects: form.subjects.map((s) => ({ itemId: s.itemId, variableName: s.variableName || null })),
      objects: form.objects.map((o) => ({ itemId: o.itemId, variableName: o.variableName || null })),
      degree: form.degree as MappingDegree,
      description: form.description || null,
      transformationRule: form.transformationRule || null,
      transformationRuleNotation: form.transformationRuleNotation || null,
    };
    if (editingId.value) {
      await itemMappingApi.update(editingId.value, payload);
    } else {
      await itemMappingApi.create({ ...payload, registration: null });
    }
    ElMessage.success(t('common.saveSuccess'));
    dialogVisible.value = false;
    fetchData();
  } catch (err: any) {
    ElMessage.error(err?.message || t('itemMapping.saveError'));
  } finally {
    saving.value = false;
  }
}

// ── Delete ────────────────────────────────────────────────────────
async function handleDelete(row: ItemMappingResponse) {
  try {
    await ElMessageBox.confirm(
      t('itemMapping.confirmDelete', { summary: mappingSummary(row) }),
      t('common.confirmDelete'),
      { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') }
    );
  } catch {
    return;
  }
  try {
    await itemMappingApi.delete(row.id);
    ElMessage.success(t('common.deleteSuccess'));
    if (rows.value.length === 1 && page.value > 1) page.value -= 1;
    fetchData();
  } catch (err: any) {
    ElMessage.error(err?.message || t('itemMapping.deleteError'));
  }
}

// ── Hierarchy ─────────────────────────────────────────────────────
const hierarchyVisible = ref(false);
const hierarchyLoading = ref(false);
const hierarchySaving = ref(false);
const hierarchyRow = ref<ItemMappingResponse | null>(null);
const hierarchySupersetId = ref<string | null>(null);
const hierarchySubsetIds = ref<string[]>([]);

const hierarchyCandidates = computed(() => rows.value.filter((m) => m.id !== hierarchyRow.value?.id));

const hierarchyCurrentSuperset = computed(() => {
  if (!hierarchySupersetId.value) return '';
  const m = rows.value.find((r) => r.id === hierarchySupersetId.value);
  return m ? mappingSummary(m) : t('common.unknownMapping');
});

const hierarchySubsets = computed(() =>
  hierarchySubsetIds.value
    .map((id) => rows.value.find((r) => r.id === id))
    .filter((m): m is ItemMappingResponse => !!m)
    .map((m) => mappingSummary(m))
);

async function openHierarchy(row: ItemMappingResponse) {
  hierarchyRow.value = row;
  hierarchySupersetId.value = null;
  hierarchySubsetIds.value = [];
  hierarchyVisible.value = true;
  hierarchyLoading.value = true;
  try {
    const res = await itemMappingApi.getHierarchy(row.id);
    hierarchySupersetId.value = res.supersetMappingId;
    hierarchySubsetIds.value = res.subsetMappingIds || [];
  } catch (err: any) {
    ElMessage.error(err?.message || t('itemMapping.hierarchyLoadError'));
  } finally {
    hierarchyLoading.value = false;
  }
}

async function handleSaveHierarchy() {
  if (!hierarchyRow.value) return;
  hierarchySaving.value = true;
  try {
    await itemMappingApi.putHierarchy(hierarchyRow.value.id, hierarchySupersetId.value);
    ElMessage.success(t('common.saveSuccess'));
    hierarchyVisible.value = false;
  } catch (err: any) {
    ElMessage.error(err?.message || t('itemMapping.hierarchySaveError'));
  } finally {
    hierarchySaving.value = false;
  }
}

// ── Init ──────────────────────────────────────────────────────────
fetchData();
</script>

<style scoped lang="scss">
.item-mapping-list {
  padding: 20px;

  .header-section {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    h2 {
      margin: 0;
      font-size: 22px;
      font-weight: 600;
      color: #1f2937;
    }
  }

  .filter-card {
    margin-bottom: 16px;

    :deep(.el-card__body) {
      padding: 16px 20px;
    }
  }

  .table-card {
    .batch-bar {
      display: flex;
      align-items: center;
      gap: 12px;
      padding-bottom: 12px;

      .batch-bar-text {
        font-size: 13px;
        color: var(--el-text-color-secondary);
      }
    }

    .endpoint-cell {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
    }

    .action-group {
      display: flex;
      align-items: center;
      justify-content: center;
      flex-wrap: nowrap;
      gap: 4px;
      white-space: nowrap;

      // EP 默认相邻按钮 12px 外边距与 flex gap 叠加，清零后统一由 gap 控制
      .el-button + .el-button {
        margin-left: 0;
      }
    }

    .endpoint-tag {
      margin: 2px 4px 2px 0;
      font-family: ui-monospace, SFMono-Regular, monospace;
    }

    .rule-text {
      font-size: 13px;
      color: var(--el-text-color-regular);
    }

    .muted {
      color: var(--el-text-color-placeholder);
    }
  }

  .pagination-bar {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
  }

  .endpoint-block {
    width: 100%;
    display: flex;
    flex-direction: column;
    gap: 6px;

    .endpoint-row {
      display: flex;
      align-items: center;
      gap: 4px;

      .endpoint-remove {
        flex-shrink: 0;
        padding: 4px;
      }

      .endpoint-label {
        flex: 0 0 42%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-family: ui-monospace, SFMono-Regular, monospace;
        font-size: 12px;
        color: var(--el-text-color-regular);
      }
    }
  }

  .hierarchy-body {
    min-height: 80px;

    .hierarchy-desc {
      margin-bottom: 8px;
    }

    .endpoint-text {
      font-family: ui-monospace, SFMono-Regular, monospace;
      font-size: 13px;
    }

    .muted {
      color: var(--el-text-color-placeholder);
    }
  }
}
</style>

<!-- tooltip popper 挂载于 body，需非 scoped 样式：浅色主题纵列列表；仅端点超多(>15)时才限高滚动 -->
<style>
.endpoint-tip-popper.el-popper.is-light {
  background: #f7fbf8;
  border: 1px solid #dcebe0;
  box-shadow: 0 4px 16px rgba(46, 92, 64, 0.12);
}
.endpoint-tip-popper.el-popper.is-light .el-popper__arrow::before {
  background: #f7fbf8;
  border-color: #dcebe0;
}
.endpoint-tip-popper .endpoint-tip-list {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding: 2px 0;
}
.endpoint-tip-popper .endpoint-tip-list.scrollable {
  max-height: 480px;
  overflow-y: auto;
}
.endpoint-tip-popper .endpoint-tip-line {
  font-family: ui-monospace, SFMono-Regular, monospace;
  font-size: 12.5px;
  line-height: 1.6;
  white-space: nowrap;
  color: #2f3e35;
}
</style>
