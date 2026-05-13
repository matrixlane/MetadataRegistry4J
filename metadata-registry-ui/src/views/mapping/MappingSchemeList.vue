<template>
  <div class="mapping-scheme-list">
    <!-- Header -->
    <div class="header-section">
      <div class="header-left">
        <h2>{{ $t('mappingScheme.title') }}</h2>
        <p class="subtitle">{{ $t('mappingScheme.subtitle') }}</p>
      </div>
      <div class="header-right">
        <el-button type="success" :icon="Plus" @click="handleCreate">
          {{ $t('mappingScheme.create') }}
        </el-button>
      </div>
    </div>

    <!-- Search bar -->
    <el-card shadow="never" class="search-card">
      <el-form :inline="true" @submit.prevent="handleSearch">
        <el-form-item>
          <el-input
            v-model="query.q"
            :placeholder="$t('common.search')"
            clearable
            style="width: 300px"
            :prefix-icon="Search"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">
            {{ $t('common.search') }}
          </el-button>
          <el-button :icon="RefreshRight" @click="handleReset">
            {{ $t('common.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Empty state -->
    <el-card v-if="!loading && rows.length === 0" shadow="never" class="empty-card">
      <el-empty :description="$t('mappingScheme.empty')">
        <el-button type="success" :icon="Plus" @click="handleCreate">
          {{ $t('mappingScheme.create') }}
        </el-button>
      </el-empty>
    </el-card>

    <!-- Data table -->
    <el-card v-else shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="rows"
        stripe
        row-key="id"
        @sort-change="handleSort"
        class="modern-table"
      >
        <el-table-column prop="name" :label="$t('mappingScheme.name')" width="200" sortable show-overflow-tooltip />
        <el-table-column prop="version" :label="$t('mappingScheme.version')" width="100" sortable align="center" />
        <el-table-column prop="status" :label="$t('mappingScheme.status')" width="120" sortable align="center">
          <template #default="{ row }">
            <el-tag
              :type="statusTagType(row.status)"
              size="small"
              effect="plain"
            >
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('mappingScheme.sourceUri')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="uri-text">{{ row.sourceUri }}</code>
          </template>
        </el-table-column>
        <el-table-column :label="$t('mappingScheme.targetUri')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="uri-text">{{ row.targetUri }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('common.createdAt')" width="170" sortable>
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="$t('common.updatedAt')" width="170" sortable>
          <template #default="{ row }">
            {{ formatTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-group">
              <!-- DRAFT: edit / delete / publish -->
              <template v-if="row.status === 'DRAFT'">
                <el-button size="small" type="primary" text :icon="Edit" @click="handleEdit(row)">
                  {{ $t('common.edit') }}
                </el-button>
                <el-button size="small" type="success" text :icon="Upload" @click="handlePublish(row)">
                  {{ $t('mappingScheme.publish') }}
                </el-button>
                <el-button size="small" type="danger" text :icon="Delete" @click="handleDelete(row)">
                  {{ $t('common.delete') }}
                </el-button>
              </template>
              <!-- PUBLISHED: new version / archive -->
              <template v-if="row.status === 'PUBLISHED'">
                <el-button size="small" type="warning" text :icon="Plus" @click="handleNewVersion(row)">
                  {{ $t('mappingScheme.newVersion') }}
                </el-button>
                <el-button size="small" type="info" text :icon="FolderDelete" @click="handleArchive(row)">
                  {{ $t('mappingScheme.archive') }}
                </el-button>
              </template>
              <!-- ARCHIVED: read-only, no actions -->
              <span v-if="row.status === 'ARCHIVED'" class="readonly-hint">—</span>
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
    </el-card>

    <!-- Create / Edit Dialog -->
    <el-dialog
      v-model="formVisible"
      :title="isEditing ? $t('mappingScheme.edit') : $t('mappingScheme.create')"
      width="600px"
      :close-on-click-modal="false"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="130px"
        @submit.prevent="handleFormSubmit"
      >
        <el-form-item :label="$t('mappingScheme.name')" prop="name">
          <el-input v-model="form.name" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('mappingScheme.sourceUri')" prop="sourceUri">
          <el-input v-model="form.sourceUri" maxlength="2048" show-word-limit placeholder="https://..." />
        </el-form-item>
        <el-form-item :label="$t('mappingScheme.targetUri')" prop="targetUri">
          <el-input v-model="form.targetUri" maxlength="2048" show-word-limit placeholder="https://..." />
        </el-form-item>
        <el-form-item :label="$t('common.description')" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="4000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="formSaving" @click="handleFormSubmit">
          {{ $t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  Search,
  RefreshRight,
  Plus,
  Edit,
  Delete,
  Upload,
  FolderDelete,
} from '@element-plus/icons-vue';
import { mappingSchemeApi } from '@/api/resources';
import type { PageResponse, PageQuery } from '@/types/common';

const { t } = useI18n();

// ==================== State ====================

const loading = ref(false);
const rows = ref<any[]>([]);
const total = ref(0);
const formVisible = ref(false);
const formSaving = ref(false);
const isEditing = ref(false);
const editingId = ref<string | null>(null);
const formRef = ref<any>();

const query = reactive<PageQuery>({
  page: 1,
  size: 20,
});

const form = reactive({
  name: '',
  sourceUri: '',
  targetUri: '',
  description: '',
});

const formRules = {
  name: [
    { required: true, message: '方案名称不能为空', trigger: 'blur' },
    { max: 255, message: '方案名称最多 255 个字符', trigger: 'blur' },
  ],
  sourceUri: [
    { required: true, message: '源标准 URI 不能为空', trigger: 'blur' },
    { max: 2048, message: '源标准 URI 最多 2048 个字符', trigger: 'blur' },
  ],
  targetUri: [
    { required: true, message: '目标标准 URI 不能为空', trigger: 'blur' },
    { max: 2048, message: '目标标准 URI 最多 2048 个字符', trigger: 'blur' },
  ],
  description: [{ max: 4000, message: '描述最多 4000 个字符', trigger: 'blur' }],
};

// ==================== Helpers ====================

function statusTagType(status: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' | undefined {
  switch (status) {
    case 'DRAFT': return 'warning';
    case 'PUBLISHED': return 'success';
    case 'ARCHIVED': return 'info';
    default: return 'info';
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case 'DRAFT': return '草稿';
    case 'PUBLISHED': return '已发布';
    case 'ARCHIVED': return '已归档';
    default: return status;
  }
}

function formatTime(iso: string | undefined): string {
  if (!iso) return '-';
  return new Date(iso).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

// ==================== Data Fetching ====================

async function fetchData() {
  loading.value = true;
  try {
    const params: Record<string, unknown> = { ...query };
    if (params.page) params.page = Number(params.page) - 1;
    const res: PageResponse<any> = await mappingSchemeApi.list(params as PageQuery);
    rows.value = res.content || [];
    total.value = res.totalElements || 0;
  } catch (err: any) {
    ElMessage.error(err?.message || '加载失败');
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
  fetchData();
}

function handleSort({ prop, order }: { prop?: string; order?: string }) {
  query.sort = prop && order ? `${prop},${order === 'ascending' ? 'asc' : 'desc'}` : undefined;
  fetchData();
}

// ==================== CRUD ====================

function resetForm() {
  form.name = '';
  form.sourceUri = '';
  form.targetUri = '';
  form.description = '';
  isEditing.value = false;
  editingId.value = null;
  formRef.value?.resetFields();
}

async function handleCreate() {
  resetForm();
  formVisible.value = true;
}

async function handleEdit(row: any) {
  resetForm();
  isEditing.value = true;
  editingId.value = row.id;
  form.name = row.name;
  form.sourceUri = row.sourceUri;
  form.targetUri = row.targetUri;
  form.description = row.description || '';
  formVisible.value = true;
}

async function handleFormSubmit() {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return;
    formSaving.value = true;
    try {
      if (isEditing.value && editingId.value) {
        await mappingSchemeApi.update(editingId.value, { ...form });
      } else {
        await mappingSchemeApi.create({ ...form });
      }
      ElMessage.success(t('mappingScheme.saveSuccess'));
      formVisible.value = false;
      fetchData();
    } catch (err: any) {
      // Error already handled by request interceptor
    } finally {
      formSaving.value = false;
    }
  });
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      t('mappingScheme.confirmDelete', { name: row.name }),
      t('common.confirm'),
      { type: 'warning', confirmButtonText: t('common.delete') }
    );
    await mappingSchemeApi.delete(row.id);
    ElMessage.success(t('mappingScheme.deleteSuccess'));
    fetchData();
  } catch {
    // cancelled
  }
}

// ==================== Lifecycle Operations ====================

async function handlePublish(row: any) {
  try {
    await ElMessageBox.confirm(
      t('mappingScheme.confirmPublish', { name: row.name }),
      t('common.confirm'),
      { type: 'info', confirmButtonText: t('mappingScheme.publish') }
    );
    await mappingSchemeApi.transition(row.id, 'PUBLISHED');
    ElMessage.success(t('mappingScheme.publishSuccess'));
    fetchData();
  } catch {
    // cancelled or error
  }
}

async function handleArchive(row: any) {
  try {
    await ElMessageBox.confirm(
      t('mappingScheme.confirmArchive', { name: row.name }),
      t('common.confirm'),
      { type: 'warning', confirmButtonText: t('mappingScheme.archive') }
    );
    await mappingSchemeApi.transition(row.id, 'ARCHIVED');
    ElMessage.success(t('mappingScheme.archiveSuccess'));
    fetchData();
  } catch {
    // cancelled
  }
}

async function handleNewVersion(row: any) {
  try {
    await ElMessageBox.confirm(
      t('mappingScheme.confirmNewVersion', { name: row.name, version: row.version }),
      t('common.confirm'),
      { type: 'info', confirmButtonText: t('mappingScheme.newVersion') }
    );
    await mappingSchemeApi.createNewVersion(row.id);
    ElMessage.success(t('mappingScheme.newVersionSuccess'));
    fetchData();
  } catch {
    // cancelled
  }
}

// ==================== Lifecycle ====================

onMounted(fetchData);
</script>

<style scoped lang="scss">
.mapping-scheme-list {
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

  .search-card {
    margin-bottom: 16px;

    :deep(.el-card__body) {
      padding: 16px 20px;
    }
  }

  .empty-card {
    :deep(.el-card__body) {
      padding: 60px 20px;
    }
  }

  .table-card {
    overflow: hidden;

    :deep(.el-card__body) {
      padding: 0;
    }
  }

  .uri-text {
    font-size: 12px;
    color: #6b7280;
    background: #f3f4f6;
    padding: 2px 6px;
    border-radius: 3px;
    word-break: break-all;
  }

  .action-group {
    display: flex;
    align-items: center;
    justify-content: center;
    flex-wrap: nowrap;
    gap: 2px;
  }

  .readonly-hint {
    color: #9ca3af;
    font-size: 13px;
  }

  .pagination-bar {
    padding: 16px 24px;
    display: flex;
    justify-content: flex-end;
    border-top: 1px solid #f1f5f9;
  }
}

// Responsive: 1280px+
@media (min-width: 1280px) {
  .mapping-scheme-list {
    max-width: 1400px;
    margin: 0 auto;
  }
}

// Modern table styling
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
</style>
