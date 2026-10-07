<template>
  <div>
    <h2 style="margin-top: 0;">{{ $t('workspace.title') }}</h2>

    <el-tabs v-model="activeTab" type="border-card" @tab-change="onTabChange">
      <el-tab-pane name="DRAFTS">
        <template #label>
          <span>{{ $t('workspace.tabs.drafts') }}</span>
          <el-badge v-if="summary.drafts > 0" :value="summary.drafts" class="workspace-tab-badge" />
        </template>
      </el-tab-pane>
      <el-tab-pane name="PENDING">
        <template #label>
          <span>{{ $t('workspace.tabs.pending') }}</span>
          <el-badge v-if="summary.pending > 0" :value="summary.pending" class="workspace-tab-badge" />
        </template>
      </el-tab-pane>
      <el-tab-pane name="REJECTED">
        <template #label>
          <span>{{ $t('workspace.tabs.rejected') }}</span>
          <el-badge v-if="summary.rejected > 0" :value="summary.rejected" class="workspace-tab-badge" />
        </template>
      </el-tab-pane>
    </el-tabs>

    <div class="page-card" style="margin-bottom: 16px; margin-top: 16px;">
      <DataTablePage
        ref="tableRef"
        :fetch-fn="fetchItems"
        :selectable="true"
        :can-create="false"
        :can-edit="false"
        :can-delete="false"
        :show-action="true"
        :action-width="280"
        @selection-change="onSelectionChange"
      >
        <template #columns>
          <el-table-column prop="label" :label="$t('workspace.columns.name')" min-width="180" />
          <el-table-column prop="itemType" :label="$t('workspace.columns.type')" width="140">
            <template #default="{ row }">
              {{ typeLabel(row.itemType) }}
            </template>
          </el-table-column>
          <el-table-column prop="registrationStatus" :label="$t('workspace.columns.registrationStatus')" width="130">
            <template #default="{ row }">
              <el-tag size="small" :type="registrationStatusType(row.registrationStatus)">
                {{ row.registrationStatus ? $t('registrationStatus.' + row.registrationStatus) : '—' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="administrativeStatus" :label="$t('workspace.columns.administrativeStatus')" width="150">
            <template #default="{ row }">
              {{ administrativeStatusLabel(row.administrativeStatus) }}
            </template>
          </el-table-column>
          <el-table-column prop="version" :label="$t('workspace.columns.version')" width="100" />
        </template>
        <template #actions="{ row }">
          <el-button
            v-if="row.flags?.canSubmit"
            size="small"
            type="primary"
            text
            data-testid="row-submit"
            @click="openSubmit(row)"
          >
            {{ $t('workspace.actions.submit') }}
          </el-button>
          <el-button
            v-if="row.flags?.canRequestCandidate"
            size="small"
            type="primary"
            text
            data-testid="row-request-candidate"
            @click="openRequestCandidate(row)"
          >
            {{ $t('workspace.actions.requestCandidate') }}
          </el-button>
          <el-button
            v-if="row.flags?.canRequestQualification"
            size="small"
            type="warning"
            text
            data-testid="row-request-qualification"
            @click="requestQualification(row)"
          >
            {{ $t('workspace.actions.requestQualification') }}
          </el-button>
          <el-button
            v-if="row.flags?.canApprove"
            size="small"
            type="success"
            text
            data-testid="row-approve"
            @click="approve(row)"
          >
            {{ $t('workspace.actions.approve') }}
          </el-button>
          <el-button
            v-if="row.flags?.canReject"
            size="small"
            type="danger"
            text
            data-testid="row-reject"
            @click="reject(row)"
          >
            {{ $t('workspace.actions.reject') }}
          </el-button>
          <el-button
            v-if="row.flags?.canRollback"
            size="small"
            type="info"
            text
            data-testid="row-rollback"
            @click="rollback(row)"
          >
            {{ $t('workspace.actions.rollback') }}
          </el-button>
          <el-button
            size="small"
            type="primary"
            text
            data-testid="row-revise"
            @click="revise(row)"
          >
            {{ $t('workspace.actions.revise') }}
          </el-button>
        </template>
        <template #batch-actions>
          <el-button
            type="primary"
            :icon="TopRight"
            :disabled="selectedSubmittableIds.length === 0"
            data-testid="batch-submit-btn"
            @click="openBatchSubmit"
          >
            {{ $t('workspace.actions.submit') }}
          </el-button>
        </template>
      </DataTablePage>
    </div>

    <!-- 批量/单行提交与请求成为候选共用提交信息弹窗 -->
    <SubmissionRecordPicker
      ref="pickerRef"
      :title="pickerTitle"
      @confirm="onPickerConfirm"
    />

    <!-- 修订：复用 ItemFormDialog -->
    <ItemFormDialog
      ref="dialogRef"
      :item-type="reviseItemType"
      :submit-fn="reviseSubmitFn"
      @success="refreshAll"
    >
      <template #fields />
    </ItemFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { ElMessage, ElMessageBox } from 'element-plus';
import { TopRight } from '@element-plus/icons-vue';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';
import SubmissionRecordPicker from './SubmissionRecordPicker.vue';
import { useWorkspaceBadge } from '@/composables/useWorkspaceBadge';
import { useActiveRegistrar } from '@/composables/useActiveRegistrar';
const activeRegistrarState = useActiveRegistrar();
import {
  workspaceApi,
  myContactApi,
  registrationProcessApi,
  administeredItemApi,
  dataElementApi,
  dataElementConceptApi,
  conceptualDomainApi,
  valueDomainApi,
  objectClassApi,
  propertyApi,
  dataTypeApi,
  dataTypeSchemeApi,
  unitOfMeasureApi,
  measureClassApi,
  dimensionalityApi,
  valueMeaningApi,
  contextApi,
} from '@/api/resources';
import type {
  PageQuery,
  WorkspaceItemView,
  WorkspaceItemResponse,
  WorkspaceSummaryResponse,
  SubmissionInfo,
  MyContactResponse,
  RegistrationStatus,
  ElTagType,
} from '@/types/common';

const { t } = useI18n();
const { refresh: refreshBadge } = useWorkspaceBadge();

const activeTab = ref<WorkspaceItemView>('DRAFTS');
const summary = ref<WorkspaceSummaryResponse>({ drafts: 0, pending: 0, rejected: 0 });
const summaryLoading = ref(false);
const tableRef = ref<InstanceType<typeof DataTablePage>>();
const pickerRef = ref<InstanceType<typeof SubmissionRecordPicker>>();
const dialogRef = ref<InstanceType<typeof ItemFormDialog>>();
const selectedRows = ref<WorkspaceItemResponse[]>([]);
const pickerMode = ref<'submit-single' | 'submit-batch' | 'request-candidate'>('submit-single');
const pickerTargetRow = ref<WorkspaceItemResponse | null>(null);

const pickerTitle = computed(() => {
  if (pickerMode.value === 'request-candidate') return t('workspace.requestCandidateDialogTitle');
  if (pickerMode.value === 'submit-batch') return t('workspace.submitDialogTitle');
  return t('workspace.submitDialogTitleSingle');
});

const selectedSubmittableIds = computed(() =>
  selectedRows.value.filter((r) => r.flags?.canSubmit).map((r) => r.itemId)
);

const ITEM_TYPE_MAP: Record<string, { labelKey: string; itemTypeKey: string; api: { get: (id: string) => Promise<unknown>; update: (id: string, data: Record<string, unknown>) => Promise<unknown>; create: (data: Record<string, unknown>) => Promise<unknown> } }> = {
  DataElement: { labelKey: 'meta.itemType.dataElement', itemTypeKey: 'dataElement', api: dataElementApi },
  DataElementConcept: { labelKey: 'meta.itemType.dataElementConcept', itemTypeKey: 'dataElementConcept', api: dataElementConceptApi },
  ConceptualDomain: { labelKey: 'meta.itemType.conceptualDomain', itemTypeKey: 'conceptualDomain', api: conceptualDomainApi },
  ValueDomain: { labelKey: 'meta.itemType.valueDomain', itemTypeKey: 'valueDomain', api: valueDomainApi },
  ObjectClass: { labelKey: 'meta.itemType.objectClass', itemTypeKey: 'objectClass', api: objectClassApi },
  Property: { labelKey: 'meta.itemType.property', itemTypeKey: 'property', api: propertyApi },
  DataType: { labelKey: 'meta.itemType.dataType', itemTypeKey: 'dataType', api: dataTypeApi },
  DataTypeScheme: { labelKey: 'meta.itemType.dataTypeScheme', itemTypeKey: 'dataTypeScheme', api: dataTypeSchemeApi },
  UnitOfMeasure: { labelKey: 'meta.itemType.unitOfMeasure', itemTypeKey: 'unitOfMeasure', api: unitOfMeasureApi },
  MeasureClass: { labelKey: 'meta.itemType.measureClass', itemTypeKey: 'measureClass', api: measureClassApi },
  Dimensionality: { labelKey: 'meta.itemType.dimensionality', itemTypeKey: 'dimensionality', api: dimensionalityApi },
  ValueMeaning: { labelKey: 'meta.itemType.valueMeaning', itemTypeKey: 'valueMeaning', api: valueMeaningApi },
  Context: { labelKey: 'meta.itemType.context', itemTypeKey: 'context', api: contextApi },
};

function typeLabel(itemType?: string | null): string {
  if (!itemType) return '—';
  return t(ITEM_TYPE_MAP[itemType]?.labelKey || '', itemType);
}

function registrationStatusType(status?: RegistrationStatus | string | null): ElTagType | undefined {
  switch (status) {
    case 'STANDARD':
    case 'PREFERRED_STANDARD':
      return 'success';
    case 'INCOMPLETE':
      return 'info';
    case 'RETIRED':
    case 'REJECTED':
      return 'danger';
    default:
      return 'warning';
  }
}

function administrativeStatusKey(value?: string | null): string | undefined {
  if (!value) return undefined;
  const normalized = value.trim().toUpperCase().replace(/\s+/g, '_');
  return normalized;
}

function administrativeStatusLabel(value?: string | null): string {
  if (!value) return '—';
  const key = administrativeStatusKey(value);
  const mapped = key ? t('administrativeStatus.' + key, '') : '';
  return mapped || value;
}

function fetchItems(params: Record<string, unknown>) {
  const query: PageQuery = { ...(params as PageQuery) };
  return workspaceApi.items(activeTab.value, query.page, query.size);
}

function onSelectionChange(rows: Record<string, unknown>[]) {
  selectedRows.value = rows as unknown as WorkspaceItemResponse[];
}

function onTabChange() {
  selectedRows.value = [];
  tableRef.value?.fetchData();
}

async function loadSummary() {
  summaryLoading.value = true;
  try {
    summary.value = await workspaceApi.summary();
  } catch {
    ElMessage.error(t('workspace.loadSummaryError'));
  } finally {
    summaryLoading.value = false;
  }
}

function refreshAll() {
  loadSummary();
  refreshBadge();
  tableRef.value?.fetchData();
}

onMounted(() => {
  loadSummary();
});

// ── 提交 / 请求成为候选（共用 SubmissionRecordPicker）────────────────────────

/**
 * 提交入口预检（acg-submission）：当前机构 = 活动 Registrar 身份所属组织
 * （active-registrar-ownership）；账号在该机构下无任何联系人身份时，提示并
 * 阻止打开 picker。仅当无活动身份（或活动身份查询失败）时才放宽为"任一身份
 * 组织均可"。
 */
async function ensureSubmissionIdentity(): Promise<boolean> {
  let identities: MyContactResponse[];
  try {
    identities = await myContactApi.list();
  } catch {
    // 身份列表加载失败时不阻塞入口，由后端提交校验兜底
    return true;
  }
  const bound = (identities || []).filter((i) => i.organizationId);
  if (bound.length === 0) {
    await alertIdentityRequired();
    return false;
  }
  // 当前机构 = 活动 Registrar 身份组织；activeOrgId 三态：
  // undefined = 无活动身份/查询失败（放宽），string = 活动身份组织，null = 有活动身份但身份不在列表中
  let activeOrgId: string | null | undefined;
  try {
    await activeRegistrarState.load();
    const activeRegistrarId = activeRegistrarState.active?.registrarId;
    if (activeRegistrarId) {
      activeOrgId = bound.find((i) => i.id === activeRegistrarId)?.organizationId ?? null;
    }
  } catch {
    // 活动身份查询失败时放宽为任一身份组织
  }
  const hasIdentityInCurrentOrg = activeOrgId === undefined
    ? true
    : bound.some((i) => i.organizationId === activeOrgId);
  if (!hasIdentityInCurrentOrg) {
    await alertIdentityRequired();
    return false;
  }
  return true;
}

async function alertIdentityRequired() {
  try {
    await ElMessageBox.alert(
      t('workspace.submissionIdentityRequiredHint'),
      t('workspace.submissionIdentityRequired'),
      { type: 'warning' }
    );
  } catch {
    // 用户关闭提示框
  }
}

async function openSubmit(row: WorkspaceItemResponse) {
  if (!(await ensureSubmissionIdentity())) return;
  pickerMode.value = 'submit-single';
  pickerTargetRow.value = row;
  pickerRef.value?.open();
}

async function openBatchSubmit() {
  if (selectedSubmittableIds.value.length === 0) {
    ElMessage.warning(t('workspace.noEligibleItems'));
    return;
  }
  if (!(await ensureSubmissionIdentity())) return;
  pickerMode.value = 'submit-batch';
  pickerTargetRow.value = null;
  pickerRef.value?.open();
}

async function openRequestCandidate(row: WorkspaceItemResponse) {
  if (!(await ensureSubmissionIdentity())) return;
  pickerMode.value = 'request-candidate';
  pickerTargetRow.value = row;
  pickerRef.value?.open();
}

async function onPickerConfirm(submission: SubmissionInfo) {
  try {
    if (pickerMode.value === 'submit-single' && pickerTargetRow.value) {
      await workspaceApi.submit([pickerTargetRow.value.itemId], submission);
      ElMessage.success(t('workspace.submitSuccess'));
    } else if (pickerMode.value === 'submit-batch') {
      const results = await workspaceApi.submit(selectedSubmittableIds.value, submission);
      const ok = results.filter((r) => r.success).length;
      const fail = results.length - ok;
      if (fail === 0) {
        ElMessage.success(t('workspace.submitSuccess'));
      } else {
        const firstReason = results.find((r) => !r.success)?.message || '';
        ElMessage.warning(t('common.batchDeleteResult', { success: ok, fail, reason: firstReason ? `：${firstReason}` : '' }));
      }
    } else if (pickerMode.value === 'request-candidate' && pickerTargetRow.value) {
      await workspaceApi.requestCandidate(pickerTargetRow.value.itemId, submission);
      ElMessage.success(t('workspace.requestCandidateSuccess'));
    }
    refreshAll();
  } catch {
    // request interceptor already shows error
  }
}

// ── 申请合格 / 批准 / 拒绝 / 回退 ───────────────────────────────────────────

async function requestQualification(row: WorkspaceItemResponse) {
  try {
    await administeredItemApi.requestQualification(row.itemId);
    ElMessage.success(t('workspace.actions.requestQualification') + t('common.saveSuccess'));
    refreshAll();
  } catch {
    // request interceptor already shows error
  }
}

async function approve(row: WorkspaceItemResponse) {
  try {
    const transitions = await registrationProcessApi.availableTransitions(row.itemId);
    const approvable = transitions.find((tr) => tr.canApprove);
    if (!approvable) {
      ElMessage.warning(t('administeredItem.noTransitions'));
      return;
    }
    await registrationProcessApi.approve(row.itemId, approvable.to);
    ElMessage.success(t('workspace.approveSuccess'));
    refreshAll();
  } catch {
    // request interceptor already shows error
  }
}

async function reject(row: WorkspaceItemResponse) {
  try {
    await ElMessageBox.confirm(t('workspace.rejectConfirmMessage'), t('workspace.rejectConfirmTitle'), { type: 'warning' });
    await administeredItemApi.reject(row.itemId, '');
    ElMessage.success(t('workspace.rejectSuccess'));
    refreshAll();
  } catch (err: any) {
    if (err !== 'cancel') {
      // request interceptor already shows error
    }
  }
}

async function rollback(row: WorkspaceItemResponse) {
  try {
    await ElMessageBox.confirm(t('workspace.rollbackConfirmMessage'), t('workspace.rollbackConfirmTitle'), { type: 'warning' });
    await workspaceApi.rollback(row.itemId);
    ElMessage.success(t('workspace.rollbackSuccess'));
    refreshAll();
  } catch (err: any) {
    if (err !== 'cancel') {
      // request interceptor already shows error
    }
  }
}

// ── 修订 ──────────────────────────────────────────────────────────────────

const reviseType = ref<{ itemTypeKey: string; submitFn: (data: Record<string, unknown>) => Promise<any> } | null>(null);

const reviseItemType = computed(() => reviseType.value?.itemTypeKey || 'dataElement');
const reviseSubmitFn = computed(() =>
  reviseType.value?.submitFn ||
  (() => {
    ElMessage.error(t('common.unknownEntry'));
    return Promise.reject(new Error('No revise type selected'));
  })
);

async function revise(row: WorkspaceItemResponse) {
  const mapping = ITEM_TYPE_MAP[row.itemType];
  if (!mapping) {
    ElMessage.error(t('common.unknownEntry'));
    return;
  }
  try {
    const fullItem = (await mapping.api.get(row.itemId)) as Record<string, unknown>;
    reviseType.value = {
      itemTypeKey: mapping.itemTypeKey,
      submitFn: (data: Record<string, unknown>) =>
        data.id ? mapping.api.update(data.id as string, data) : mapping.api.create(data),
    };
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    await dialogRef.value?.open(fullItem);
  } catch {
    ElMessage.error(t('workspace.loadItemsError'));
  }
}
</script>

<style scoped lang="scss">
.workspace-tab-badge {
  margin-left: 8px;
  vertical-align: middle;
}
</style>
