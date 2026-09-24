<template>
  <div>
    <h2 style="margin-top: 0;">{{ $t('nav.administeredItems') }}</h2>

    <!-- 注册状态过滤：包一层 fetchFn 透传后端 status 参数 -->
    <div class="page-card" style="margin-bottom: 16px;">
      <el-form :inline="true">
        <el-form-item :label="$t('administeredItem.filterStatus')">
          <el-select
            v-model="statusFilter"
            clearable
            style="width: 200px;"
            data-test="status-filter"
            @change="handleStatusChange"
          >
            <el-option :label="$t('administeredItem.filterAll')" value="" />
            <el-option
              v-for="s in REGISTRATION_STATUSES"
              :key="s"
              :label="$t('registrationStatus.' + s)"
              :value="s"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <DataTablePage
      ref="tableRef"
      :fetch-fn="fetchItems"
      :can-create="false"
      :can-edit="false"
      :can-delete="false"
    >
      <template #columns>
        <el-table-column prop="identifier" label="标识符" width="160" sortable />
        <el-table-column label="名称">
          <template #default="{ row }">
            {{ row.designations?.find((d: any) => d.languageCode === 'zh-CN')?.value || row.designations?.[0]?.value || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="registrationStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.registrationStatus)">
              {{ (row.registrationStatus ? $t('registrationStatus.' + row.registrationStatus) : '-') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="effectiveDate" label="生效日期" width="160" />
      </template>
      <template #actions="{ row }">
        <el-button size="small" text type="primary" data-test="open-process" @click="openProcess(row)">
          {{ $t('administeredItem.processButton') }}
        </el-button>
      </template>
    </DataTablePage>

    <!-- 推进流程面板：列出当前状态的可达迁移与审批进度 -->
    <el-drawer
      v-model="processVisible"
      :title="$t('administeredItem.processDrawerTitle')"
      size="480px"
      data-test="process-drawer"
    >
      <div v-if="processItem" class="process-current" data-test="process-current">
        <span class="process-current__label">{{ $t('administeredItem.currentStatus') }}</span>
        <el-tag size="small" :type="statusType(processItem.registrationStatus as string)">
          {{ processItem.registrationStatus ? $t('registrationStatus.' + processItem.registrationStatus) : '-' }}
        </el-tag>
      </div>

      <el-empty v-if="!transitionsLoading && !transitions.length" :description="$t('administeredItem.noTransitions')" />

      <div v-for="tr in transitions" :key="tr.to" class="transition-item" data-test="transition-item">
        <div class="transition-item__head">
          <span class="transition-item__name">
            {{
              tr.displayName ||
              $t('administeredItem.defaultTransitionName', { status: $t('registrationStatus.' + tr.to) })
            }}
          </span>
          <el-tag size="small" :type="statusType(tr.to)">{{ $t('registrationStatus.' + tr.to) }}</el-tag>
          <el-tag v-if="tr.requiresApproval" size="small" type="warning">
            {{ $t('administeredItem.requiresApproval') }}
          </el-tag>
        </div>
        <div v-if="tr.requiresApproval" class="transition-item__progress">
          {{ $t('administeredItem.progress', { approved: tr.approvedCount, quorum: tr.quorum }) }}
        </div>
        <div class="transition-item__actions">
          <!-- 无需审核：具备写权限的用户直接执行 -->
          <el-button
            v-if="tr.canExecute"
            type="primary"
            size="small"
            data-test="execute-btn"
            @click="openExecute(tr)"
          >
            {{ $t('administeredItem.execute') }}
          </el-button>
          <!-- 需审核：合格 Registrar 批准；已批准过的置灰 -->
          <el-button
            v-else-if="tr.requiresApproval && tr.approvedByMe"
            size="small"
            disabled
            data-test="approved-btn"
          >
            {{ $t('administeredItem.approved') }}
          </el-button>
          <el-button
            v-else-if="tr.canApprove"
            type="warning"
            size="small"
            data-test="approve-btn"
            @click="approve(tr)"
          >
            {{ $t('administeredItem.approve') }}
          </el-button>
          <!-- 无权限用户只读 -->
          <span v-else class="transition-item__readonly">{{ $t('administeredItem.readonly') }}</span>
        </div>
      </div>
    </el-drawer>

    <!-- 执行状态推进（无需审核）：备注可选 -->
    <el-dialog
      v-model="executeVisible"
      :title="$t('administeredItem.executeDialogTitle')"
      width="480px"
      :close-on-click-modal="false"
      data-test="execute-dialog"
    >
      <el-form label-position="top">
        <el-form-item :label="$t('administeredItem.currentStatus')">
          <el-tag size="small" :type="statusType(processItem?.registrationStatus as string)">
            {{ processItem?.registrationStatus ? $t('registrationStatus.' + processItem.registrationStatus) : '-' }}
          </el-tag>
        </el-form-item>
        <el-form-item :label="$t('administeredItem.filterStatus')">
          <el-tag size="small" type="success">{{ $t('registrationStatus.' + executeTarget?.to) }}</el-tag>
        </el-form-item>
        <el-form-item :label="$t('administeredItem.noteLabel')">
          <el-input
            v-model="executeNote"
            type="textarea"
            :rows="3"
            :placeholder="$t('administeredItem.notePlaceholder')"
            data-test="execute-note"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="executeVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="acting"
          data-test="execute-confirm"
          @click="confirmExecute"
        >
          {{ $t('administeredItem.execute') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { ElMessage } from 'element-plus';
import { administeredItemApi, registrationProcessApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import type { ItemTransitionView, PageQuery, ElTagType } from '@/types/common';

const { t } = useI18n();

/** 注册状态全量取值（含可作初始状态的文档状态）。 */
const REGISTRATION_STATUSES = [
  'INCOMPLETE', 'CANDIDATE', 'RECORDED', 'QUALIFIED', 'STANDARD',
  'PREFERRED_STANDARD', 'SUPERSEDED', 'RETIRED', 'HISTORICAL', 'APPLICATION',
] as const;

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const statusFilter = ref('');

/** 包一层 fetchFn：在 DataTablePage 查询参数上叠加注册状态过滤 */
function fetchItems(params: Record<string, unknown>) {
  const query: PageQuery = { ...(params as PageQuery) };
  if (statusFilter.value) {
    query.status = statusFilter.value;
  }
  return administeredItemApi.list(query);
}

function handleStatusChange() {
  tableRef.value?.fetchData();
}

function statusType(status?: string): ElTagType | undefined {
  switch (status) {
    case 'STANDARD': return 'success';
    case 'PREFERRED_STANDARD': return 'success';
    case 'INCOMPLETE': return 'info';
    case 'CANDIDATE': return undefined;
    case 'RETIRED': return 'danger';
    default: return 'warning';
  }
}

// ── 推进流程面板 ───────────────────────────────────────────────────────

const processVisible = ref(false);
const processItem = ref<Record<string, unknown> | null>(null);
const transitions = ref<ItemTransitionView[]>([]);
const transitionsLoading = ref(false);
const acting = ref(false);

async function openProcess(row: Record<string, unknown>) {
  processItem.value = row;
  processVisible.value = true;
  await loadTransitions();
}

async function loadTransitions() {
  if (!processItem.value?.id) return;
  transitionsLoading.value = true;
  try {
    transitions.value = await registrationProcessApi.availableTransitions(processItem.value.id as string);
  } catch {
    transitions.value = [];
    ElMessage.error(t('administeredItem.loadTransitionsError'));
  } finally {
    transitionsLoading.value = false;
  }
}

function refreshAfterAction(message: string) {
  ElMessage.success(message);
  // 状态可能已变化：刷新面板进度与列表数据
  loadTransitions();
  tableRef.value?.fetchData();
}

// ── 执行（无需审核） ───────────────────────────────────────────────────

const executeVisible = ref(false);
const executeTarget = ref<ItemTransitionView | null>(null);
const executeNote = ref('');

function openExecute(tr: ItemTransitionView) {
  executeTarget.value = tr;
  executeNote.value = '';
  executeVisible.value = true;
}

async function confirmExecute() {
  if (!processItem.value?.id || !executeTarget.value) return;
  acting.value = true;
  try {
    await registrationProcessApi.execute(
      processItem.value.id as string,
      executeTarget.value.to,
      executeNote.value || undefined,
    );
    executeVisible.value = false;
    refreshAfterAction(t('administeredItem.executeSuccess'));
  } catch {
    // 全局拦截器已提示错误
  } finally {
    acting.value = false;
  }
}

// ── 批准（需审核） ─────────────────────────────────────────────────────

async function approve(tr: ItemTransitionView) {
  if (!processItem.value?.id) return;
  acting.value = true;
  try {
    await registrationProcessApi.approve(processItem.value.id as string, tr.to);
    refreshAfterAction(t('administeredItem.approveSuccess'));
  } catch {
    // 全局拦截器已提示错误
  } finally {
    acting.value = false;
  }
}
</script>

<style scoped lang="scss">
.process-current {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;

  &__label {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.transition-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 12px;

  &__head {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  &__name {
    font-weight: 600;
    font-size: 14px;
  }

  &__progress {
    margin-top: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__actions {
    margin-top: 10px;
    display: flex;
    align-items: center;
  }

  &__readonly {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
