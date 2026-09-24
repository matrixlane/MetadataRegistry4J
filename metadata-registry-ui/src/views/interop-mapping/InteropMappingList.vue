<template>
  <div class="interop-mapping-list">
    <!-- Header -->
    <div class="header-section">
      <div class="header-left">
        <h2>互联互通匹配结果管理</h2>
        <p class="subtitle">查看、确认、调整数据元匹配结果</p>
      </div>
      <div class="header-right">
        <el-button type="primary" :icon="Refresh" @click="fetchData">
          刷新
        </el-button>
      </div>
    </div>

    <!-- Quality Score Card -->
    <el-card v-if="qualityScore && qualityScore.totalCount > 0" shadow="never" class="quality-card">
      <div class="quality-grid">
        <div class="quality-item">
          <div class="quality-value" :class="scoreClass(qualityScore.score)">{{ qualityScore.score.toFixed(1) }}</div>
          <div class="quality-label">对齐质量评分</div>
        </div>
        <div class="quality-divider" />
        <div class="quality-item">
          <div class="quality-value success">{{ qualityScore.matchedCount }}</div>
          <div class="quality-label">已匹配 ({{ qualityScore.matchedRate.toFixed(1) }}%)</div>
        </div>
        <div class="quality-item">
          <div class="quality-value warning">{{ qualityScore.pendingCount }}</div>
          <div class="quality-label">待确认 ({{ qualityScore.pendingRate.toFixed(1) }}%)</div>
        </div>
        <div class="quality-item">
          <div class="quality-value danger">{{ qualityScore.unmatchedCount }}</div>
          <div class="quality-label">未匹配 ({{ qualityScore.unmatchedRate.toFixed(1) }}%)</div>
        </div>
        <div class="quality-item">
          <div class="quality-value">{{ qualityScore.totalCount }}</div>
          <div class="quality-label">总数据元</div>
        </div>
      </div>
    </el-card>

    <!-- Filters -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item label="数据集">
          <el-select v-model="query.datasetId" placeholder="选择数据集" clearable style="width: 220px" @change="handleDatasetChange">
            <el-option v-for="ds in datasets" :key="ds.id" :label="ds.name" :value="ds.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="已匹配" value="AUTO_MATCHED" />
            <el-option label="待确认" value="PENDING" />
            <el-option label="已确认" value="CONFIRMED" />
            <el-option label="已拒绝" value="REJECTED" />
            <el-option label="未匹配" value="UNMATCHED" />
          </el-select>
        </el-form-item>
        <el-form-item label="置信度">
          <el-slider v-model="confidenceRange" range :min="0" :max="100" :step="5" style="width: 200px" />
          <span class="confidence-label">{{ confidenceRange[0] }}% - {{ confidenceRange[1] }}%</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- Batch actions -->
      <div v-if="pendingCount > 0" class="batch-bar">
        <el-alert :title="`当前数据集有 ${pendingCount} 条待确认记录`" type="info" :closable="false" show-icon>
          <template #default>
            <el-button size="small" type="success" :icon="CircleCheck" @click="handleBatchConfirm">批量接受</el-button>
            <el-button size="small" type="danger" :icon="CircleClose" @click="handleBatchReject">批量拒绝</el-button>
          </template>
        </el-alert>
      </div>
    </el-card>

    <!-- Empty state guidance -->
    <el-card v-if="emptyState?.isEmpty" shadow="never" class="empty-card">
      <el-empty description="暂无匹配任务">
        <template #description>
          <p>暂无匹配任务</p>
          <p class="empty-hint">{{ emptyState.message }}</p>
        </template>
        <el-button type="primary" @click="$router.push('/data-elements')">去导入数据元</el-button>
      </el-empty>
    </el-card>

    <el-card v-else-if="emptyState?.hasEmptyLocalMetadata" shadow="never" class="empty-card">
      <el-empty description="本地元数据不足">
        <template #description>
          <p>本地元数据不足</p>
          <p class="empty-hint">{{ emptyState.message }}</p>
        </template>
        <el-button type="primary" @click="$router.push('/administered-items')">去完善本地元数据</el-button>
      </el-empty>
    </el-card>

    <!-- Data table -->
    <el-card v-else shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" stripe row-key="id" class="modern-table">
        <el-table-column type="index" width="50" align="center" />
        <el-table-column label="互联互通数据元" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="element-name">{{ row.interopDataElementName || '—' }}</div>
            <el-tag size="small" type="info">{{ row.strategy }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="建议匹配本地项" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="element-name">{{ row.localItemName || localItemLabel(row.localItemId) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="120" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.round(row.confidenceScore * 100)"
              :color="confidenceColor"
              :stroke-width="16"
              :show-text="true"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="匹配理由" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.reason || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-group">
              <template v-if="row.status === 'PENDING'">
                <el-button size="small" type="success" text :icon="Check" @click="handleConfirm(row)">确认</el-button>
                <el-button size="small" type="danger" text :icon="Close" @click="handleReject(row)">拒绝</el-button>
                <el-button size="small" type="primary" text :icon="Edit" @click="handleManualMap(row)">指定</el-button>
              </template>
              <template v-if="row.status === 'CONFIRMED' || row.status === 'AUTO_MATCHED'">
                <el-button size="small" type="warning" text :icon="RefreshLeft" @click="handleUndo(row)">撤销</el-button>
              </template>
              <template v-if="row.status === 'REJECTED'">
                <el-button size="small" type="warning" text :icon="RefreshLeft" @click="handleUndo(row)">撤销</el-button>
                <el-button size="small" type="info" text :icon="DocumentAdd" @click="handleMarkUnmatched(row)">标记新建</el-button>
              </template>
              <template v-if="row.status === 'UNMATCHED'">
                <el-button size="small" type="warning" text :icon="RefreshLeft" @click="handleUndo(row)">撤销</el-button>
              </template>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- Manual Map Dialog -->
    <el-dialog v-model="manualMapVisible" title="手动指定本地元数据项" width="500px" destroy-on-close>
      <el-form ref="manualMapFormRef" :model="manualMapForm" :rules="manualMapRules" label-width="120px">
        <el-form-item label="目标本地项">
          <SmartItemPicker
            v-model="manualMapForm.localItemId"
            :scope="'all'"
            clearable
            placeholder="搜索本地元数据项（标识符 / 名称）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualMapVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualMapSaving" @click="submitManualMap">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  Search,
  RefreshRight,
  Refresh,
  Check,
  Close,
  Edit,
  RefreshLeft,
  DocumentAdd,
  CircleCheck,
  CircleClose,
} from '@element-plus/icons-vue';
import { interopMappingApi } from '@/api/resources';
import SmartItemPicker from '@/components/SmartItemPicker.vue';
import { useItemLabelCache } from '@/composables/useItemLabelCache';
import type { PageQuery } from '@/types/common';

const { requestAll: requestLabels, labelFor } = useItemLabelCache();

/** 本地项展示：优先后端给定名称，缺失时经共享缓存反查「标识符 | 指称」，不显示 id */
function localItemLabel(localItemId: string | null | undefined): string {
  return labelFor(localItemId);
}

// ==================== State ====================

const loading = ref(false);
const rows = ref<any[]>([]);
const total = ref(0);
const pendingCount = ref(0);
const qualityScore = ref<any>(null);
const emptyState = ref<any>(null);
const confidenceRange = ref<[number, number]>([0, 100]);

const currentUserId = ref('00000000-0000-0000-0000-000000000001'); // TODO: from auth store

// Dataset mock — in production this would come from an API
const datasets = ref([
  { id: 'dataset-1', name: '患者基本信息数据集' },
  { id: 'dataset-2', name: '门诊处方数据集' },
  { id: 'dataset-3', name: '住院病案首页数据集' },
]);

const query = reactive<PageQuery & { datasetId?: string; status?: string; minConfidence?: number; maxConfidence?: number }>({
  page: 1,
  size: 20,
});

const manualMapVisible = ref(false);
const manualMapSaving = ref(false);
const manualMapFormRef = ref<any>();
const manualMapForm = reactive({ localItemId: '', mappingId: '' });
const manualMapRules = {
  localItemId: [{ required: true, message: '请输入本地元数据项 ID', trigger: 'change' }],
};

// ==================== Helpers ====================

const confidenceColor = [
  { color: '#f56c6c', percentage: 60 },
  { color: '#e6a23c', percentage: 85 },
  { color: '#67c23a', percentage: 100 },
];

function statusTagType(status: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' | undefined {
  switch (status) {
    case 'AUTO_MATCHED': return 'success';
    case 'CONFIRMED': return 'success';
    case 'PENDING': return 'warning';
    case 'REJECTED': return 'danger';
    case 'UNMATCHED': return 'info';
    default: return 'info';
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case 'AUTO_MATCHED': return '已匹配';
    case 'CONFIRMED': return '已确认';
    case 'PENDING': return '待确认';
    case 'REJECTED': return '已拒绝';
    case 'UNMATCHED': return '未匹配';
    default: return status;
  }
}

function scoreClass(score: number): string {
  if (score >= 80) return 'success';
  if (score >= 60) return 'warning';
  return 'danger';
}

// ==================== Data Fetching ====================

async function fetchData() {
  if (!query.datasetId) {
    rows.value = [];
    total.value = 0;
    return;
  }

  loading.value = true;
  try {
    const params: Record<string, unknown> = {
      ...query,
      datasetId: query.datasetId,
    };
    if (params.page) params.page = Number(params.page) - 1;
    if (query.status) params.status = query.status;
    if (confidenceRange.value[0] > 0) params.minConfidence = confidenceRange.value[0] / 100;
    if (confidenceRange.value[1] < 100) params.maxConfidence = confidenceRange.value[1] / 100;

    const res = await interopMappingApi.list(params as PageQuery);
    rows.value = res.content || [];
    total.value = res.totalElements || 0;
    requestLabels(rows.value.map((r: any) => r.localItemId));

    await fetchQualityScore();
    await fetchEmptyCheck();
    await fetchPendingCount();
  } catch (err: any) {
    ElMessage.error(err?.message || '加载失败');
  } finally {
    loading.value = false;
  }
}

async function fetchQualityScore() {
  if (!query.datasetId) return;
  try {
    const res = await interopMappingApi.qualityScore(query.datasetId);
    qualityScore.value = res;
  } catch {
    qualityScore.value = null;
  }
}

async function fetchEmptyCheck() {
  if (!query.datasetId) return;
  try {
    const res = await interopMappingApi.emptyCheck(query.datasetId);
    emptyState.value = res;
  } catch {
    emptyState.value = null;
  }
}

async function fetchPendingCount() {
  if (!query.datasetId) {
    pendingCount.value = 0;
    return;
  }
  try {
    const count = await interopMappingApi.countPending(query.datasetId);
    pendingCount.value = Number(count);
  } catch {
    pendingCount.value = 0;
  }
}

function handleSearch() {
  query.page = 1;
  fetchData();
}

function handleReset() {
  query.page = 1;
  query.status = undefined;
  confidenceRange.value = [0, 100];
  fetchData();
}

function handleDatasetChange() {
  query.page = 1;
  fetchData();
}

// ==================== Individual Actions ====================

async function handleConfirm(row: any) {
  try {
    await interopMappingApi.confirm(row.id, currentUserId.value);
    ElMessage.success('已确认匹配');
    fetchData();
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败');
  }
}

async function handleReject(row: any) {
  try {
    await interopMappingApi.reject(row.id, currentUserId.value);
    ElMessage.success('已拒绝匹配');
    fetchData();
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败');
  }
}

function handleManualMap(row: any) {
  manualMapForm.mappingId = row.id;
  manualMapForm.localItemId = '';
  manualMapVisible.value = true;
}

async function submitManualMap() {
  if (!manualMapFormRef.value) return;
  await manualMapFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return;
    manualMapSaving.value = true;
    try {
      await interopMappingApi.manualMap(manualMapForm.mappingId, manualMapForm.localItemId, currentUserId.value);
      ElMessage.success('手动指定成功');
      manualMapVisible.value = false;
      fetchData();
    } catch (err: any) {
      ElMessage.error(err?.message || '操作失败');
    } finally {
      manualMapSaving.value = false;
    }
  });
}

async function handleUndo(row: any) {
  try {
    await ElMessageBox.confirm('确定要撤销此操作吗？', '撤销确认', { type: 'warning' });
    await interopMappingApi.undo(row.id);
    ElMessage.success('已撤销');
    fetchData();
  } catch {
    // cancelled
  }
}

async function handleMarkUnmatched(row: any) {
  try {
    await ElMessageBox.confirm('标记为未匹配将生成待办记录，是否继续？', '确认', { type: 'info' });
    await interopMappingApi.markUnmatched(row.id);
    ElMessage.success('已标记为未匹配');
    fetchData();
  } catch {
    // cancelled
  }
}

// ==================== Batch Actions ====================

async function handleBatchConfirm() {
  try {
    await ElMessageBox.confirm(
      `确定要批量接受当前数据集的 ${pendingCount.value} 条待确认记录吗？`,
      '批量确认',
      { type: 'warning', confirmButtonText: '确认接受' }
    );
    const res = await interopMappingApi.batchConfirm(query.datasetId!, currentUserId.value);
    ElMessage.success(`批量接受成功，共 ${res.affectedCount} 条记录`);
    fetchData();
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '批量操作失败');
    }
  }
}

async function handleBatchReject() {
  try {
    await ElMessageBox.confirm(
      `确定要批量拒绝当前数据集的 ${pendingCount.value} 条待确认记录吗？`,
      '批量拒绝',
      { type: 'error', confirmButtonText: '确认拒绝' }
    );
    const res = await interopMappingApi.batchReject(query.datasetId!, currentUserId.value);
    ElMessage.success(`批量拒绝成功，共 ${res.affectedCount} 条记录`);
    fetchData();
  } catch (err: any) {
    if (err !== 'cancel') {
      ElMessage.error(err?.message || '批量操作失败');
    }
  }
}

// ==================== Lifecycle ====================

onMounted(() => {
  if (datasets.value.length > 0) {
    query.datasetId = datasets.value[0].id;
    fetchData();
  }
});
</script>

<style scoped lang="scss">
.interop-mapping-list {
  padding: 20px;

  .header-section {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    .header-left {
      h2 {
        margin: 0;
        font-size: 22px;
        font-weight: 600;
        color: #1f2937;
      }

      .subtitle {
        margin: 4px 0 0;
        color: #6b7280;
        font-size: 13px;
      }
    }
  }

  .quality-card {
    margin-bottom: 16px;

    :deep(.el-card__body) {
      padding: 20px 24px;
    }

    .quality-grid {
      display: flex;
      align-items: center;
      gap: 32px;
    }

    .quality-item {
      text-align: center;
    }

    .quality-value {
      font-size: 28px;
      font-weight: 700;
      color: #1f2937;

      &.success { color: #67c23a; }
      &.warning { color: #e6a23c; }
      &.danger { color: #f56c6c; }
    }

    .quality-label {
      margin-top: 4px;
      font-size: 13px;
      color: #6b7280;
    }

    .quality-divider {
      width: 1px;
      height: 48px;
      background: #e5e7eb;
    }
  }

  .filter-card {
    margin-bottom: 16px;

    :deep(.el-card__body) {
      padding: 16px 20px;
    }

    .confidence-label {
      margin-left: 12px;
      font-size: 13px;
      color: #6b7280;
      min-width: 80px;
    }

    .batch-bar {
      margin-top: 12px;

      :deep(.el-alert__content) {
        display: flex;
        align-items: center;
        gap: 12px;
      }
    }
  }

  .empty-card {
    :deep(.el-card__body) {
      padding: 60px 20px;
    }

    .empty-hint {
      color: #6b7280;
      font-size: 13px;
      margin-top: 8px;
    }
  }

  .table-card {
    overflow: hidden;

    :deep(.el-card__body) {
      padding: 0;
    }
  }

  .element-name {
    font-weight: 500;
    color: #1f2937;
    margin-bottom: 4px;
  }

  .action-group {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-wrap: nowrap;
    gap: 2px;
  }

  .pagination-bar {
    padding: 16px 24px;
    display: flex;
    justify-content: flex-end;
    border-top: 1px solid #f1f5f9;
  }
}

// Responsive
@media (min-width: 1280px) {
  .interop-mapping-list {
    max-width: 1400px;
    margin: 0 auto;
  }
}

// Modern table styling
.modern-table {
  --el-table-border-color: transparent;

  // 主表头底色
  :deep(.el-table__header-wrapper) {
    th.el-table__cell {
      background: #f8fafc;
      font-weight: 600;
      color: #374151;
      font-size: 13px;
      height: 48px;
      border-bottom: 1px solid #f1f5f9;
    }
  }

  :deep(.el-table__row) {
    transition: background 0.2s;

    td.el-table__cell {
      border-bottom: 1px solid #f8fafc;
      padding: 12px 0;
    }

    &:hover {
      background: #f8fafc;
    }
  }
}
</style>
