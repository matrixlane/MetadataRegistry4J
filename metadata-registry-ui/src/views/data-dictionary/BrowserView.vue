<template>
  <div class="dictionary-browser">
    <!-- Header -->
    <div class="page-header">
      <div class="header-text">
        <h2>数据字典</h2>
        <p class="header-desc">统一浏览和搜索所有注册的元数据项</p>
      </div>
      <div class="header-actions">
        <el-tooltip content="刷新" placement="bottom">
          <el-button :icon="Refresh" circle @click="handleRefresh" />
        </el-tooltip>
      </div>
    </div>

    <!-- Search bar -->
    <div class="search-section">
      <el-input
        v-model="query.q"
        placeholder="搜索名称、标识符或定义..."
        clearable
        size="large"
        :prefix-icon="Search"
        class="global-search-input"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #append>
          <el-button :icon="Search" @click="handleSearch">搜索</el-button>
        </template>
      </el-input>
    </div>

    <!-- Filters: Tabs + Status + Context + Acceptability -->
    <div class="filter-section">
      <div class="type-tabs">
        <el-radio-group v-model="selectedType" size="default" @change="handleTypeChange as any">
          <el-radio-button value="">全部类型</el-radio-button>
          <el-radio-button
            v-for="t in entityTypes"
            :key="t.value"
            :value="t.value"
          >
            {{ t.label }}
          </el-radio-button>
        </el-radio-group>
      </div>
      <div class="extra-filters">
        <el-select
          v-model="selectedContext"
          placeholder="上下文"
          clearable
          style="width: 160px;"
          @change="handleContextChange"
        >
          <el-option
            v-for="c in contextOptions"
            :key="c.id"
            :label="c.label"
            :value="c.id"
          />
        </el-select>
        <el-select
          v-model="selectedAcceptability"
          placeholder="可接受性"
          clearable
          multiple
          collapse-tags
          collapse-tags-tooltip
          style="width: 180px;"
          @change="handleAcceptabilityChange"
        >
          <el-option
            v-for="a in acceptabilityOptions"
            :key="a.value"
            :label="a.label"
            :value="a.value"
          />
        </el-select>
        <el-select
          v-model="selectedStatus"
          placeholder="注册状态"
          clearable
          style="width: 160px;"
          @change="handleStatusChange"
        >
          <el-option
            v-for="s in registrationStatuses"
            :key="s.value"
            :label="s.label"
            :value="s.value"
          />
        </el-select>
      </div>
    </div>

    <!-- Keyword/active filter display -->
    <div v-if="activeFilters.length" class="active-filters">
      <span class="filter-label">当前筛选：</span>
      <el-tag
        v-for="f in activeFilters"
        :key="f.key"
        closable
        size="small"
        @close="removeFilter(f.key)"
      >
        {{ f.label }}
      </el-tag>
      <el-button text size="small" type="primary" @click="clearAllFilters">
        清除全部
      </el-button>
    </div>

    <!-- Results table -->
    <div class="content-card">
      <!-- Loading state -->
      <div v-if="loading" class="loading-state">
        <el-skeleton :rows="5" animated />
      </div>

      <!-- Empty state -->
      <div v-else-if="!rows.length" class="empty-state">
        <el-empty
          :description="hasAnyFilter ? '未找到匹配的元数据项，请调整搜索条件' : '暂无元数据项'"
        >
          <el-button v-if="hasAnyFilter" text type="primary" @click="clearAllFilters">
            清除所有筛选条件
          </el-button>
        </el-empty>
      </div>

      <!-- Table -->
      <el-table
        v-else
        :data="rows"
        v-loading="loading"
        stripe
        row-key="id"
        class="metadata-table"
        @row-click="handleRowClick"
      >
        <el-table-column label="名称" min-width="220">
          <template #default="{ row }">
            <div class="item-name-cell">
              <span class="item-name">{{ row.itemLabel || '-' }}</span>
              <el-tag v-if="row.hasPreferred" size="small" type="success" class="preferred-badge">PREFERRED</el-tag>
              <el-tag size="small" class="type-badge" :type="typeBadgeType(row.itemType)">
                {{ typeLabel(row.itemType) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="identifier" label="标识符" width="200" />
        <el-table-column prop="namespaceLabel" label="命名空间" width="140" />
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="registrationStatus" label="注册状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.registrationStatus)">
              {{ statusLabel(row.registrationStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creationDateTime" label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatDate(row.creationDateTime) }}
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <div v-if="total > 0" class="pagination-bar">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handlePageChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
const { t } = useI18n();
import { useRouter } from 'vue-router';
import { Search, Refresh } from '@element-plus/icons-vue';
import { metadataSearchApi, contextApi } from '@/api/resources';

const router = useRouter();

// --- State ---
const rows = ref<any[]>([]);
const total = ref(0);
const loading = ref(false);
const contextOptions = ref<{ id: string; label: string }[]>([]);

const query = reactive({
  q: undefined as string | undefined,
  type: undefined as string | undefined,
  status: undefined as string | undefined,
  contextId: undefined as string | undefined,
  acceptability: undefined as string[] | undefined,
});

const currentPage = ref(1);
const pageSize = ref(20);
const selectedType = ref('');
const selectedStatus = ref('');
const selectedContext = ref('');
const selectedAcceptability = ref<string[]>([]);

// --- Entity types ---
const entityTypes = [
  { value: 'DataElement', label: '数据元' },
  { value: 'DataElementConcept', label: '数据元概念' },
  { value: 'ObjectClass', label: '对象类' },
  { value: 'Property', label: '特性' },
  { value: 'ConceptualDomain', label: '概念域' },
  { value: 'ValueDomain', label: '值域' },
  { value: 'DataType', label: '数据类型' },
  { value: 'DataTypeScheme', label: '数据类型方案' },
  { value: 'UnitOfMeasure', label: '计量单位' },
  { value: 'MeasureClass', label: '计量类' },
];

// --- Registration statuses ---
const registrationStatuses = [
  { value: 'INCOMPLETE', label: '不完整' },
  { value: 'CANDIDATE', label: '候选' },
  { value: 'RECORDED', label: '已记录' },
  { value: 'QUALIFIED', label: '已合格' },
  { value: 'STANDARD', label: '标准' },
  { value: 'PREFERRED_STANDARD', label: '首选标准' },
  { value: 'SUPERSEDED', label: '已取代' },
  { value: 'RETIRED', label: '已退役' },
  { value: 'HISTORICAL', label: '历史' },
];

// --- Acceptability options ---
const acceptabilityOptions = [
  { value: 'PREFERRED', label: '首选' },
  { value: 'ADMITTED', label: '可接受' },
  { value: 'DEPRECATED', label: '不推荐使用' },
  { value: 'SUPERSEDED', label: '已取代' },
  { value: 'OBSOLETE', label: '已废弃' },
];

// --- Active filters display ---
const activeFilters = computed(() => {
  const filters: { key: string; label: string }[] = [];
  if (query.q) filters.push({ key: 'q', label: `关键词: "${query.q}"` });
  if (query.type) {
    const t = entityTypes.find(e => e.value === query.type);
    filters.push({ key: 'type', label: `类型: ${t?.label || query.type}` });
  }
  if (query.status) {
    const s = registrationStatuses.find(st => st.value === query.status);
    filters.push({ key: 'status', label: `状态: ${s?.label || query.status}` });
  }
  if (query.contextId) {
    const c = contextOptions.value.find(opt => opt.id === query.contextId);
    filters.push({ key: 'contextId', label: `上下文: ${c?.label || query.contextId}` });
  }
  if (query.acceptability && query.acceptability.length > 0) {
    const labels = query.acceptability.map(v => acceptabilityOptions.find(a => a.value === v)?.label || v);
    filters.push({ key: 'acceptability', label: `可接受性: ${labels.join(', ')}` });
  }
  return filters;
});

const hasAnyFilter = computed(() => {
  return !!query.q || !!query.type || !!query.status || !!query.contextId || !!(query.acceptability && query.acceptability.length > 0);
});

// --- Helper functions ---
function typeLabel(type: string): string {
  const t = entityTypes.find(e => e.value === type);
  return t?.label || type || '未知';
}

function typeBadgeType(type: string): import('@/types/common').ElTagType | undefined {
  return type === 'DataElement' ? 'success'
    : type === 'ValueDomain' ? 'warning'
    : type === 'ConceptualDomain' ? 'info'
    : type === 'ObjectClass' ? 'primary'
    : undefined;
}

function statusLabel(status?: string): string {
  const s = registrationStatuses.find(st => st.value === status);
  return s?.label || status || '未知';
}

function statusTagType(status?: string): import('@/types/common').ElTagType | undefined {
  switch (status) {
    case 'STANDARD':
    case 'PREFERRED_STANDARD':
      return 'success';
    case 'INCOMPLETE':
    case 'CANDIDATE':
      return 'info';
    case 'RECORDED':
    case 'QUALIFIED':
      return 'primary';
    case 'SUPERSEDED':
    case 'RETIRED':
    case 'HISTORICAL':
      return 'danger';
    default:
      return 'warning';
  }
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-';
  try {
    const d = new Date(dateStr);
    return d.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    });
  } catch {
    return dateStr;
  }
}

async function loadContexts() {
  try {
    const res = await contextApi.options();
    contextOptions.value = res || [];
  } catch {
    contextOptions.value = [];
  }
}

// --- Data fetching ---
async function fetchData() {
  loading.value = true;
  try {
    const params: Record<string, any> = { page: currentPage.value - 1, size: pageSize.value };
    if (query.q) params.q = query.q;
    if (query.type) params.type = query.type;
    if (query.status) params.status = query.status;
    if (query.contextId) params.contextId = query.contextId;
    if (query.acceptability && query.acceptability.length > 0) params.acceptability = query.acceptability;

    const res = await metadataSearchApi.search(params as any);
    rows.value = res.content || [];
    total.value = res.totalElements || 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  currentPage.value = 1;
  fetchData();
}

function handleTypeChange(val: string) {
  query.type = val || undefined;
  currentPage.value = 1;
  fetchData();
}

function handleStatusChange(val: string) {
  query.status = val || undefined;
  currentPage.value = 1;
  fetchData();
}

function handleContextChange(val: string) {
  query.contextId = val || undefined;
  currentPage.value = 1;
  fetchData();
}

function handleAcceptabilityChange(val: string[]) {
  query.acceptability = val && val.length > 0 ? val : undefined;
  currentPage.value = 1;
  fetchData();
}

function handlePageChange() {
  fetchData();
}

function handleRefresh() {
  fetchData();
}

function removeFilter(key: string) {
  if (key === 'q') { query.q = undefined; }
  if (key === 'type') { query.type = undefined; selectedType.value = ''; }
  if (key === 'status') { query.status = undefined; selectedStatus.value = ''; }
  if (key === 'contextId') { query.contextId = undefined; selectedContext.value = ''; }
  if (key === 'acceptability') { query.acceptability = undefined; selectedAcceptability.value = []; }
  currentPage.value = 1;
  fetchData();
}

function clearAllFilters() {
  query.q = undefined;
  query.type = undefined;
  query.status = undefined;
  query.contextId = undefined;
  query.acceptability = undefined;
  selectedType.value = '';
  selectedStatus.value = '';
  selectedContext.value = '';
  selectedAcceptability.value = [];
  currentPage.value = 1;
  fetchData();
}

function handleRowClick(row: any) {
  router.push({
    name: 'MetadataDetail',
    params: { id: row.id },
  });
}

onMounted(() => {
  loadContexts();
  fetchData();
});
</script>

<style scoped lang="scss">
.dictionary-browser {
  padding-bottom: 24px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;

  .header-text {
    h2 {
      margin: 0;
      font-size: 24px;
      font-weight: 700;
      color: #1f2937;
    }

    .header-desc {
      margin: 6px 0 0;
      color: #6b7280;
      font-size: 14px;
    }
  }

  .header-actions {
    display: flex;
    gap: 8px;
  }
}

// --- Search ---
.search-section {
  margin-bottom: 16px;

  .global-search-input {
    :deep(.el-input__wrapper) {
      border-radius: 8px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06), 0 4px 16px rgba(0, 0, 0, 0.03);
    }

    :deep(.el-input-group__append) {
      border-radius: 0 8px 8px 0;
    }
  }
}

// --- Filters ---
.filter-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;

  .type-tabs {
    flex: 1;
    min-width: 0;

    :deep(.el-radio-group) {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
    }
  }

  .extra-filters {
    flex-shrink: 0;
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }
}

// --- Active filters ---
.active-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 8px;
  flex-wrap: wrap;

  .filter-label {
    font-size: 13px;
    color: #6b7280;
  }
}

// --- Content ---
.content-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #f1f5f9;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 4px 16px rgba(0, 0, 0, 0.03);
  overflow: hidden;
}

.metadata-table {
  cursor: pointer;

  :deep(.el-table__row) {
    transition: background 0.15s;

    &:hover {
      background: #f0f7ff !important;
    }
  }

  :deep(.el-table__body tr) {
    cursor: pointer;
  }
}

.item-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;

  .item-name {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-weight: 500;
  }

  .type-badge {
    flex-shrink: 0;
  }

  .preferred-badge {
    flex-shrink: 0;
    font-weight: 600;
  }
}

.loading-state {
  padding: 40px 24px;
}

.empty-state {
  padding: 48px 24px;
  display: flex;
  justify-content: center;
}

.pagination-bar {
  padding: 16px 24px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #f1f5f9;
}
</style>