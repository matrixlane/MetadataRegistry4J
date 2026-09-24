<template>
  <div>
    <div class="page-card" style="margin-bottom: 16px;">
      <el-form :inline="true" @submit.prevent="handleSearch" class="search-form">
        <div class="search-form-left">
          <el-form-item label="关键词" class="search-item">
            <el-input
              v-model="query.q"
              placeholder="搜索标识符或名称"
              clearable
              style="width: 260px;"
              :prefix-icon="Search"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item class="search-item">
            <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
            <el-button :icon="RefreshRight" @click="handleReset">重置</el-button>
          </el-form-item>
        </div>
        <div class="search-form-right" v-if="canCreate">
          <el-button
            type="success"
            :icon="Plus"
            data-testid="data-table-create-btn"
            @click="$emit('create')"
          >
            新建
          </el-button>
        </div>
      </el-form>
    </div>

    <div class="page-card table-card">
      <div v-if="batchEnabled" class="batch-bar">
        <span class="batch-bar-text">{{ t('common.selectedCount', { count: selectedCount }) }}</span>
        <el-button
          type="danger"
          :icon="Delete"
          :disabled="selectedCount === 0 || batchDeleting"
          :loading="batchDeleting"
          @click="handleBatchDelete"
        >
          {{ t('common.batchDelete') }}
        </el-button>
      </div>
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="rows"
        stripe
        row-key="id"
        @sort-change="handleSort"
        @selection-change="handleSelectionChange"
        class="modern-table"
      >
        <el-table-column v-if="selectable || batchEnabled" type="selection" width="48" />
        <slot name="columns" />
        <el-table-column
          v-if="showAction"
          label="操作"
          :width="actionWidth"
          fixed="right"
          align="center"
        >
          <template #default="{ row }">
            <div class="action-group">
              <el-button
                v-if="canEdit"
                size="small"
                type="primary"
                text
                :icon="Edit"
                @click="$emit('edit', row)"
              >
                编辑
              </el-button>
              <el-button
                v-if="canDelete"
                size="small"
                type="danger"
                text
                :icon="Delete"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
              <slot name="actions" :row="row" />
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import type { PageResponse } from '@/types/common';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search, RefreshRight, Plus, Edit, Delete } from '@element-plus/icons-vue';

interface Props {
  fetchFn: (params: Record<string, unknown>) => Promise<PageResponse<any>>;
  canCreate?: boolean;
  canEdit?: boolean;
  canDelete?: boolean;
  showAction?: boolean;
  /** 操作列宽度：附加操作按钮（actions 插槽）较多时由页面调宽 */
  actionWidth?: number;
  /** 启用多选列；与 deleteFn 一起决定批量条显隐 */
  selectable?: boolean;
  /** 批量删除函数：对每个选中的 id 异步调用；reject 时计入失败 */
  deleteFn?: (id: string) => Promise<void>;
}

const props = withDefaults(defineProps<Props>(), {
  canCreate: true,
  canEdit: true,
  canDelete: true,
  showAction: true,
  actionWidth: 160,
  selectable: false,
});

const emit = defineEmits<{
  create: [];
  edit: [row: Record<string, unknown>];
  delete: [row: Record<string, unknown>];
  'selection-change': [rows: Record<string, unknown>[]];
}>();

const { t } = useI18n();

const tableRef = ref<InstanceType<typeof import('element-plus').ElTable>>();
const loading = ref(false);
const rows = ref<Record<string, unknown>[]>([]);
const total = ref(0);

const query = reactive({
  page: 1,
  size: 20,
  sort: undefined as string | undefined,
  q: undefined as string | undefined,
});

const selectedRows = ref<Record<string, unknown>[]>([]);
const batchDeleting = ref(false);

const batchEnabled = computed(() => props.canDelete !== false && !!props.deleteFn);
const selectedCount = computed(() => selectedRows.value.length);

async function fetchData() {
  loading.value = true;
  try {
    const res = await props.fetchFn({ ...query, page: query.page - 1 });
    rows.value = res.content || [];
    total.value = res.totalElements || 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  query.page = 1;
  fetchData();
}

function handleReset() {
  query.page = 1;
  query.q = undefined;
  query.sort = undefined;
  fetchData();
}

function handleSort({ prop, order }: { prop?: string; order?: string }) {
  query.sort = prop && order ? `${prop},${order === 'ascending' ? 'asc' : 'desc'}` : undefined;
  fetchData();
}

function handleDelete(row: Record<string, unknown>) {
  ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
    .then(() => {
      emit('delete', row);
    })
    .catch(() => {});
}

function handleSelectionChange(rows: Record<string, unknown>[]) {
  selectedRows.value = rows;
  emit('selection-change', rows);
}

async function handleBatchDelete() {
  const ids = selectedRows.value.map((r) => r.id).filter((id): id is string => typeof id === 'string');
  if (ids.length === 0) return;
  try {
    await ElMessageBox.confirm(
      `确认删除选中的 ${ids.length} 条记录？`,
      '提示',
      { type: 'warning' }
    );
  } catch {
    return;
  }
  batchDeleting.value = true;
  const results = await Promise.allSettled(ids.map((id) => props.deleteFn!(id)));
  batchDeleting.value = false;
  const succeeded = results.filter((r) => r.status === 'fulfilled').length;
  const failed = ids.length - succeeded;
  if (failed === 0) {
    ElMessage.success(`已删除 ${ids.length} 条记录`);
  } else {
    const firstReason =
      results.find((r) => r.status === 'rejected')?.reason instanceof Error
        ? (results.find((r) => r.status === 'rejected')!.reason as Error).message
        : '未知错误';
    ElMessage.warning(`成功 ${succeeded} 条，失败 ${failed} 条：${firstReason}`);
  }
  selectedRows.value = [];
  tableRef.value?.clearSelection();
  await fetchData();
}

onMounted(fetchData);

watch(() => [query.q], () => {
  if (query.q === '') query.q = undefined;
});

defineExpose({ fetchData });
</script>

<style scoped lang="scss">
.search-form {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;

  .search-form-left {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
  }

  .search-form-right {
    display: flex;
    align-items: center;
  }

  .search-item {
    margin-bottom: 0;
    margin-right: 0;
  }
}

.table-card {
  padding: 0;
  overflow: hidden;

  :deep(.el-card__body) {
    padding: 0;
  }
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 24px;
  border-bottom: 1px solid #f1f5f9;
}

.batch-bar-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.modern-table {
  --el-table-border-color: transparent;

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

.action-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.pagination-bar {
  padding: 16px 24px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #f1f5f9;
}
</style>