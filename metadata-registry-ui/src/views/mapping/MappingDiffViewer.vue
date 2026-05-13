<template>
  <div class="mapping-diff-viewer">
    <!-- Header -->
    <div class="diff-header">
      <div class="header-left">
        <el-button text :icon="ArrowLeft" @click="$router.back()">
          {{ $t('common.back') }}
        </el-button>
        <div class="version-tags">
          <el-tag size="small" type="info">{{ $t('mappingDiff.base') }}: {{ diffData?.baseVersion }}</el-tag>
          <el-icon class="arrow-icon"><ArrowRight /></el-icon>
          <el-tag size="small" type="primary">{{ $t('mappingDiff.compare') }}: {{ diffData?.compareVersion }}</el-tag>
        </div>
      </div>
      <div class="header-right">
        <el-select
          v-model="mappingTypeFilter"
          size="small"
          clearable
          :placeholder="$t('mappingDiff.filterByType')"
          style="width: 140px; margin-right: 12px;"
          @change="fetchDiff"
        >
          <el-option label="EQUIVALENT" value="EQUIVALENT" />
          <el-option label="DERIVED" value="DERIVED" />
          <el-option label="AGGREGATED" value="AGGREGATED" />
          <el-option label="SUBDIVIDED" value="SUBDIVIDED" />
        </el-select>
        <el-radio-group v-model="filterType" size="small">
          <el-radio-button label="all">{{ $t('mappingDiff.all') }} ({{ totalCount }})</el-radio-button>
          <el-radio-button label="added">{{ $t('mappingDiff.added') }} ({{ addedCount }})</el-radio-button>
          <el-radio-button label="removed">{{ $t('mappingDiff.removed') }} ({{ removedCount }})</el-radio-button>
          <el-radio-button label="modified">{{ $t('mappingDiff.modified') }} ({{ modifiedCount }})</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- Empty / Identical -->
    <el-card v-if="diffData?.identical" shadow="never" class="empty-card">
      <el-empty :description="$t('mappingDiff.identical')">
        <template #image>
          <el-icon :size="60" color="#67c23a"><CircleCheck /></el-icon>
        </template>
      </el-empty>
    </el-card>

    <!-- Diff Table -->
    <el-card v-else shadow="never" class="diff-card">
      <div class="diff-summary">
        <el-alert
          :title="$t('mappingDiff.summary', { added: addedCount, removed: removedCount, modified: modifiedCount })"
          type="info"
          :closable="false"
          show-icon
        />
      </div>

      <el-table
        :data="filteredDiffs"
        stripe
        class="modern-table"
        v-loading="loading"
      >
        <el-table-column width="120" align="center">
          <template #header>
            <span>{{ $t('mappingDiff.type') }}</span>
          </template>
          <template #default="{ row }">
            <el-tag
              :type="diffTagType(row.diffType)"
              size="small"
              effect="dark"
            >
              {{ diffTypeLabel(row.diffType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column min-width="200">
          <template #header>
            <span>{{ $t('mappingDiff.source') }}</span>
          </template>
          <template #default="{ row }">
            <code class="mapping-code">{{ row.source }}</code>
          </template>
        </el-table-column>

        <el-table-column min-width="200">
          <template #header>
            <span>{{ $t('mappingDiff.target') }}</span>
          </template>
          <template #default="{ row }">
            <code class="mapping-code">{{ row.target }}</code>
          </template>
        </el-table-column>

        <el-table-column width="140" align="center">
          <template #header>
            <span>{{ $t('mappingDiff.mappingType') }}</span>
          </template>
          <template #default="{ row }">
            <template v-if="row.diffType !== 'modified'">
              <el-tag size="small" effect="plain">{{ row.mappingType }}</el-tag>
            </template>
            <template v-else>
              <div class="diff-field">
                <span v-if="row.changedFields.includes('mappingType')" class="field-old">{{ row.from.mappingType }}</span>
                <span v-else>{{ row.to.mappingType }}</span>
                <el-icon v-if="row.changedFields.includes('mappingType')" class="arrow-icon-small"><ArrowRight /></el-icon>
                <span v-if="row.changedFields.includes('mappingType')" class="field-new">{{ row.to.mappingType }}</span>
              </div>
            </template>
          </template>
        </el-table-column>

        <el-table-column min-width="200">
          <template #header>
            <span>{{ $t('common.description') }}</span>
          </template>
          <template #default="{ row }">
            <template v-if="row.diffType !== 'modified'">
              <span class="desc-text">{{ row.description || '-' }}</span>
            </template>
            <template v-else>
              <div class="diff-field">
                <span v-if="row.changedFields.includes('description')" class="field-old">{{ row.from.description || '-' }}</span>
                <span v-else class="desc-text">{{ row.to.description || '-' }}</span>
                <template v-if="row.changedFields.includes('description')">
                  <el-icon class="arrow-icon-small"><ArrowRight /></el-icon>
                  <span class="field-new">{{ row.to.description || '-' }}</span>
                </template>
              </div>
            </template>
          </template>
        </el-table-column>

        <el-table-column min-width="200">
          <template #header>
            <span>{{ $t('mappingDiff.transformationRule') }}</span>
          </template>
          <template #default="{ row }">
            <template v-if="row.diffType !== 'modified'">
              <span class="desc-text">{{ row.transformationRule || '-' }}</span>
            </template>
            <template v-else>
              <div class="diff-field">
                <span v-if="row.changedFields.includes('transformationRule')" class="field-old">{{ row.from.transformationRule || '-' }}</span>
                <span v-else class="desc-text">{{ row.to.transformationRule || '-' }}</span>
                <template v-if="row.changedFields.includes('transformationRule')">
                  <el-icon class="arrow-icon-small"><ArrowRight /></el-icon>
                  <span class="field-new">{{ row.to.transformationRule || '-' }}</span>
                </template>
              </div>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          :total="filteredDiffs.length"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { ElMessage } from 'element-plus';
import { ArrowLeft, ArrowRight, CircleCheck } from '@element-plus/icons-vue';
import { mappingSchemeApi } from '@/api/resources';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();

// ==================== State ====================

const loading = ref(false);
const diffData = ref<any>(null);
const filterType = ref<'all' | 'added' | 'removed' | 'modified'>('all');
const mappingTypeFilter = ref<string | undefined>(undefined);
const page = ref(1);
const pageSize = ref(20);

// ==================== Computed ====================

const addedCount = computed(() => diffData.value?.added?.length || 0);
const removedCount = computed(() => diffData.value?.removed?.length || 0);
const modifiedCount = computed(() => diffData.value?.modified?.length || 0);
const totalCount = computed(() => diffData.value?.totalDiffCount || 0);

interface UnifiedDiffRow {
  diffType: 'added' | 'removed' | 'modified';
  source: string;
  target: string;
  mappingType?: string;
  description?: string;
  transformationRule?: string;
  from?: any;
  to?: any;
  changedFields?: string[];
}

const allDiffs = computed((): UnifiedDiffRow[] => {
  if (!diffData.value) return [];
  const result: UnifiedDiffRow[] = [];

  (diffData.value.added || []).forEach((item: any) => {
    result.push({ diffType: 'added', ...item });
  });
  (diffData.value.removed || []).forEach((item: any) => {
    result.push({ diffType: 'removed', ...item });
  });
  (diffData.value.modified || []).forEach((item: any) => {
    result.push({ diffType: 'modified', ...item });
  });

  return result;
});

const filteredDiffs = computed(() => {
  let rows = allDiffs.value;
  if (filterType.value !== 'all') {
    rows = rows.filter(r => r.diffType === filterType.value);
  }
  // Client-side pagination
  const start = (page.value - 1) * pageSize.value;
  return rows.slice(start, start + pageSize.value);
});

// ==================== Helpers ====================

function diffTagType(type: string): 'success' | 'danger' | 'warning' | 'info' | undefined {
  switch (type) {
    case 'added': return 'success';
    case 'removed': return 'danger';
    case 'modified': return 'warning';
    default: return 'info';
  }
}

function diffTypeLabel(type: string): string {
  switch (type) {
    case 'added': return t('mappingDiff.added');
    case 'removed': return t('mappingDiff.removed');
    case 'modified': return t('mappingDiff.modified');
    default: return type;
  }
}

// ==================== Data Fetching ====================

async function fetchDiff() {
  const schemeId = route.params.id as string;
  const baseId = route.query.baseId as string;
  const compareId = route.query.compareId as string;

  if (!schemeId || !baseId || !compareId) {
    ElMessage.error(t('mappingDiff.missingParams'));
    router.back();
    return;
  }

  loading.value = true;
  try {
    const res = await mappingSchemeApi.diff(schemeId, baseId, compareId, mappingTypeFilter.value);
    diffData.value = res;
  } catch (err: any) {
    ElMessage.error(err?.message || t('mappingDiff.loadError'));
  } finally {
    loading.value = false;
  }
}

// ==================== Lifecycle ====================

onMounted(fetchDiff);
</script>

<style scoped lang="scss">
.mapping-diff-viewer {
  padding: 20px;

  .diff-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    flex-wrap: wrap;
    gap: 12px;

    .header-left {
      display: flex;
      align-items: center;
      gap: 16px;

      .version-tags {
        display: flex;
        align-items: center;
        gap: 8px;

        .arrow-icon {
          color: #909399;
        }
      }
    }
  }

  .empty-card {
    :deep(.el-card__body) {
      padding: 80px 20px;
    }
  }

  .diff-card {
    overflow: hidden;

    :deep(.el-card__body) {
      padding: 0;
    }

    .diff-summary {
      padding: 16px 20px;
      border-bottom: 1px solid #f1f5f9;
    }
  }

  .mapping-code {
    font-size: 12px;
    color: #374151;
    background: #f3f4f6;
    padding: 2px 6px;
    border-radius: 3px;
    word-break: break-all;
  }

  .desc-text {
    font-size: 13px;
    color: #4b5563;
  }

  .diff-field {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 13px;

    .field-old {
      color: #f56c6c;
      text-decoration: line-through;
      background: #fef0f0;
      padding: 2px 4px;
      border-radius: 3px;
    }

    .field-new {
      color: #67c23a;
      background: #f0f9eb;
      padding: 2px 4px;
      border-radius: 3px;
    }

    .arrow-icon-small {
      color: #909399;
      font-size: 12px;
    }
  }

  .pagination-bar {
    padding: 16px 24px;
    display: flex;
    justify-content: flex-end;
    border-top: 1px solid #f1f5f9;
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

// Responsive
@media (min-width: 1280px) {
  .mapping-diff-viewer {
    max-width: 1600px;
    margin: 0 auto;
  }
}
</style>
