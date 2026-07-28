<template>
  <div class="detail-view">
    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <el-skeleton :rows="8" animated />
    </div>

    <!-- Error -->
    <div v-else-if="error" class="error-state">
      <el-result icon="error" title="加载失败" :sub-title="error">
        <template #extra>
          <el-button type="primary" @click="router.back()">返回</el-button>
        </template>
      </el-result>
    </div>

    <!-- Detail content -->
    <template v-else-if="item">
      <!-- Breadcrumb -->
      <div class="breadcrumb-bar">
        <el-breadcrumb>
          <el-breadcrumb-item :to="{ name: 'DataDictionary' }">
            数据字典
          </el-breadcrumb-item>
          <el-breadcrumb-item>{{ typeLabel(item.itemType) }}</el-breadcrumb-item>
          <el-breadcrumb-item>{{ item.itemLabel || item.identifier || '详情' }}</el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <!-- Header card -->
      <div class="header-card">
        <div class="header-top">
          <div class="header-info">
            <div class="header-type-badge">
              <el-tag :type="typeBadgeType(item.itemType)" size="small">
                {{ typeLabel(item.itemType) }}
              </el-tag>
            </div>
            <h1 class="header-title">{{ item.itemLabel || '未命名项' }}</h1>
            <p class="header-id">{{ item.identifier || '无标识符' }}</p>
          </div>
          <div class="header-status">
            <el-tag :type="statusTagType(item.registrationStatus)" size="large" effect="dark">
              {{ statusLabel(item.registrationStatus) }}
            </el-tag>
          </div>
        </div>
      </div>

      <!-- Detail sections -->
      <div class="detail-grid">
        <!-- Core attributes -->
        <el-card shadow="hover" class="detail-card">
          <template #header>
            <div class="card-header">
              <el-icon><InfoFilled /></el-icon>
              <span>核心属性</span>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="名称" min-width="160">
              {{ item.itemLabel || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="标识符">
              {{ item.identifier || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="版本">
              {{ item.version || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="实体类型">
              {{ typeLabel(item.itemType) }}
            </el-descriptions-item>
            <el-descriptions-item label="注册状态">
              <el-tag size="small" :type="statusTagType(item.registrationStatus)">
                {{ statusLabel(item.registrationStatus) }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- Registration info -->
        <el-card shadow="hover" class="detail-card">
          <template #header>
            <div class="card-header">
              <el-icon><Files /></el-icon>
              <span>注册信息</span>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="命名空间">
              {{ item.namespaceLabel || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="注册机构">
              {{ item.registrationAuthorityLabel || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatDateTime(item.creationDateTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="最后修改">
              {{ formatDateTime(item.lastChangeDateTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="变更说明">
              {{ item.changeDescription || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="生效日期">
              {{ formatDateTime(item.stateEffectiveDate) }}
            </el-descriptions-item>
            <el-descriptions-item label="管理备注">
              {{ item.administrativeNote || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </div>

      <!-- Tabs -->
      <el-tabs v-model="activeTab" class="detail-tabs">
        <el-tab-pane label="概览" name="overview" />
        <el-tab-pane label="版本历史" name="versions" />
      </el-tabs>

      <!-- Tab content: Overview -->
      <div v-show="activeTab === 'overview'" class="tab-content">
        <!-- Core attributes -->
        <div class="detail-grid">
          <el-card shadow="hover" class="detail-card">
            <template #header>
              <div class="card-header">
                <el-icon><InfoFilled /></el-icon>
                <span>核心属性</span>
              </div>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="名称" min-width="160">
                {{ item.itemLabel || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="标识符">
                {{ item.identifier || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="版本">
                {{ item.version || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="实体类型">
                {{ typeLabel(item.itemType) }}
              </el-descriptions-item>
              <el-descriptions-item label="注册状态">
                <el-tag size="small" :type="statusTagType(item.registrationStatus)">
                  {{ statusLabel(item.registrationStatus) }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- Registration info -->
          <el-card shadow="hover" class="detail-card">
            <template #header>
              <div class="card-header">
                <el-icon><Files /></el-icon>
                <span>注册信息</span>
              </div>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="命名空间">
                {{ item.namespaceLabel || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="注册机构">
                {{ item.registrationAuthorityLabel || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="创建时间">
                {{ formatDateTime(item.creationDateTime) }}
              </el-descriptions-item>
              <el-descriptions-item label="最后修改">
                {{ formatDateTime(item.lastChangeDateTime) }}
              </el-descriptions-item>
              <el-descriptions-item label="变更说明">
                {{ item.changeDescription || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="生效日期">
                {{ formatDateTime(item.stateEffectiveDate) }}
              </el-descriptions-item>
              <el-descriptions-item label="管理备注">
                {{ item.administrativeNote || '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </div>
      </div>

      <!-- Tab content: Version History -->
      <div v-show="activeTab === 'versions'" class="tab-content">
        <VersionHistoryTimeline :item-id="item.id" />
      </div>

      <!-- Actions -->
      <div class="action-bar">
        <el-button @click="router.back()">返回列表</el-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { InfoFilled, Files } from '@element-plus/icons-vue';
import { metadataSearchApi } from '@/api/resources';
import { administeredItemApi } from '@/api/resources';
import VersionHistoryTimeline from '@/components/VersionHistoryTimeline.vue';

const route = useRoute();
const router = useRouter();

const item = ref<any>(null);
const loading = ref(true);
const error = ref('');
const activeTab = ref('overview');

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

const registrationStatuses = [
  { value: 'INCOMPLETE', label: '不完整' },
  { value: 'SUBMITTED', label: '已提交' },
  { value: 'RECORDED', label: '已记录' },
  { value: 'QUALIFIED', label: '已合格' },
  { value: 'STANDARD', label: '标准' },
  { value: 'PREFERRED', label: '首选' },
  { value: 'SUPERSEDED', label: '已取代' },
  { value: 'RETIRED', label: '已退役' },
  { value: 'HISTORICAL', label: '历史' },
];

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
    case 'PREFERRED':
      return 'success';
    case 'INCOMPLETE':
    case 'SUBMITTED':
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

function formatDateTime(dateStr?: string): string {
  if (!dateStr) return '-';
  try {
    const d = new Date(dateStr);
    return d.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return dateStr;
  }
}

onMounted(async () => {
  const id = route.params.id as string;
  if (!id) {
    error.value = '缺少元数据 ID';
    loading.value = false;
    return;
  }

  try {
    const res = await administeredItemApi.get(id);
    item.value = res;
  } catch (e: any) {
    error.value = e?.response?.data?.message || e?.message || '加载失败';
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped lang="scss">
.detail-view {
  max-width: 960px;
  margin: 0 auto;
  padding-bottom: 40px;
}

.loading-state {
  padding: 40px 24px;
}

.error-state {
  padding: 40px 24px;
}

// --- Breadcrumb ---
.breadcrumb-bar {
  margin-bottom: 20px;
}

// --- Header card ---
.header-card {
  background: linear-gradient(135deg, #f0f7ff 0%, #f8fafc 100%);
  border-radius: 12px;
  padding: 24px 28px;
  margin-bottom: 20px;
  border: 1px solid #e0edff;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: linear-gradient(90deg, #2563eb, #3b82f6);
  }

  .header-top {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
  }

  .header-info {
    flex: 1;
    min-width: 0;
  }

  .header-type-badge {
    margin-bottom: 8px;
  }

  .header-title {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
    color: #1f2937;
    line-height: 1.3;
  }

  .header-id {
    margin: 6px 0 0;
    color: #6b7280;
    font-size: 14px;
    font-family: 'Consolas', 'Courier New', monospace;
  }

  .header-status {
    flex-shrink: 0;
  }
}

// --- Detail grid ---
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
}

.detail-card {
  border-radius: 12px;
  border: 1px solid #f1f5f9;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 4px 16px rgba(0, 0, 0, 0.03);

  :deep(.el-card__header) {
    padding: 14px 20px;
    border-bottom: 1px solid #f3f4f6;
    background: #fafbfc;
  }

  :deep(.el-card__body) {
    padding: 0;
  }

  .card-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 600;
    font-size: 14px;
    color: #374151;
  }

  :deep(.el-descriptions__title) {
    font-size: 13px;
  }
}

// --- Action bar ---
.action-bar {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
}

.detail-tabs {
  margin-bottom: 16px;
}

.tab-content {
  min-height: 200px;
}
</style>
