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
        <div class="search-form-right">
          <el-form-item v-if="canCreate">
            <el-button type="success" :icon="Plus" @click="$emit('create')">新建</el-button>
          </el-form-item>
        </div>
      </el-form>
    </div>

    <div class="page-card table-card">
      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        row-key="id"
        @sort-change="handleSort"
        class="modern-table"
      >
        <slot name="columns" />
        <el-table-column
          v-if="showAction"
          label="操作"
          width="160"
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
import { reactive, ref, watch, onMounted } from 'vue';
import type { PageResponse } from '@/types/common';
import { ElMessageBox, ElMessage } from 'element-plus';
import { Search, RefreshRight, Plus, Edit, Delete } from '@element-plus/icons-vue';

interface Props {
  fetchFn: (params: Record<string, unknown>) => Promise<PageResponse<any>>;
  canCreate?: boolean;
  canEdit?: boolean;
  canDelete?: boolean;
  showAction?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  canCreate: true,
  canEdit: true,
  canDelete: true,
  showAction: true,
});

const emit = defineEmits<{
  create: [];
  edit: [row: Record<string, unknown>];
  delete: [row: Record<string, unknown>];
}>();

const loading = ref(false);
const rows = ref<Record<string, unknown>[]>([]);
const total = ref(0);

const query = reactive({
  page: 1,
  size: 20,
  sort: undefined as string | undefined,
  q: undefined as string | undefined,
});

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
