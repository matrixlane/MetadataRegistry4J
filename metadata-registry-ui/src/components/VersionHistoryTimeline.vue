<template>
  <div class="version-history">
    <div v-if="loading" class="loading-state">
      <el-skeleton :rows="6" animated />
    </div>
    <div v-else-if="versions.length === 0" class="empty-state">
      <el-empty description="暂无版本历史" />
    </div>
    <div v-else>
      <!-- Version selection for diff -->
      <div class="diff-selector">
        <el-form inline size="small">
          <el-form-item label="基准版本">
            <el-select v-model="baseVersion" placeholder="选择基准版本" style="width: 140px">
              <el-option
                v-for="v in versions"
                :key="v.versionNumber"
                :label="`v${v.versionNumber} (${formatDate(v.changedAt)})`"
                :value="v.versionNumber"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="对比版本">
            <el-select v-model="compareVersion" placeholder="选择对比版本" style="width: 140px">
              <el-option
                v-for="v in versions"
                :key="v.versionNumber"
                :label="`v${v.versionNumber} (${formatDate(v.changedAt)})`"
                :value="v.versionNumber"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" size="small" @click="showDiff" :disabled="!canDiff">
              对比差异
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- Timeline -->
      <el-timeline>
        <el-timeline-item
          v-for="v in versions"
          :key="v.id"
          :type="timelineType(v.changeType)"
          :icon="timelineIcon(v.changeType)"
          :timestamp="formatDateTime(v.changedAt)"
        >
          <div class="version-card">
            <div class="version-header">
              <span class="version-badge">v{{ v.versionNumber }}</span>
              <el-tag size="small" :type="changeTypeTag(v.changeType)">
                {{ changeTypeLabel(v.changeType) }}
              </el-tag>
              <span class="version-user">{{ v.changedBy }}</span>
            </div>
            <div class="version-summary">{{ v.changeSummary || '-' }}</div>
            <div class="version-actions">
              <el-button link size="small" @click="viewSnapshot(v.versionNumber)">
                查看快照
              </el-button>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>

    <!-- Diff Dialog -->
    <el-dialog
      v-model="diffVisible"
      title="版本差异对比"
      width="900px"
      destroy-on-close
    >
      <VersionDiffViewer v-if="diffData" :diff="diffData" />
    </el-dialog>

    <!-- Snapshot Dialog -->
    <el-dialog
      v-model="snapshotVisible"
      title="版本快照"
      width="700px"
      destroy-on-close
    >
      <div v-if="snapshotData" class="snapshot-view">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="版本号">v{{ snapshotData.versionNumber }}</el-descriptions-item>
          <el-descriptions-item label="操作类型">{{ changeTypeLabel(snapshotData.changeType) }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ snapshotData.changedBy }}</el-descriptions-item>
          <el-descriptions-item label="操作时间">{{ formatDateTime(snapshotData.changedAt) }}</el-descriptions-item>
          <el-descriptions-item label="变更摘要">{{ snapshotData.changeSummary || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-divider />
        <pre class="snapshot-json">{{ JSON.stringify(snapshotData.snapshot, null, 2) }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { versionHistoryApi } from '@/api/resources';
import VersionDiffViewer from './VersionDiffViewer.vue';
import {
  CircleCheck,
  EditPen,
  Delete,
  Plus,
} from '@element-plus/icons-vue';

const props = defineProps<{
  itemId: string;
}>();

const versions = ref<any[]>([]);
const loading = ref(true);
const baseVersion = ref<number | undefined>(undefined);
const compareVersion = ref<number | undefined>(undefined);
const diffVisible = ref(false);
const diffData = ref<any>(null);
const snapshotVisible = ref(false);
const snapshotData = ref<any>(null);

const canDiff = computed(() => {
  return baseVersion.value !== undefined
    && compareVersion.value !== undefined
    && baseVersion.value !== compareVersion.value;
});

onMounted(async () => {
  try {
    const res = await versionHistoryApi.list(props.itemId);
    versions.value = res || [];
    if (versions.value.length >= 2) {
      baseVersion.value = versions.value[versions.value.length - 1]?.versionNumber;
      compareVersion.value = versions.value[0]?.versionNumber;
    }
  } catch (e: any) {
    // ignore
  } finally {
    loading.value = false;
  }
});

async function showDiff() {
  if (!canDiff.value) return;
  try {
    const res = await versionHistoryApi.diff(
      props.itemId,
      baseVersion.value!,
      compareVersion.value!
    );
    diffData.value = res;
    diffVisible.value = true;
  } catch {
    // ignore
  }
}

async function viewSnapshot(versionNumber: number) {
  try {
    const res = await versionHistoryApi.snapshot(props.itemId, versionNumber);
    snapshotData.value = res;
    snapshotVisible.value = true;
  } catch {
    // ignore
  }
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-';
  try {
    const d = new Date(dateStr);
    return d.toLocaleDateString('zh-CN');
  } catch {
    return dateStr;
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

function timelineType(type?: string): any {
  switch (type) {
    case 'CREATE': return 'primary';
    case 'UPDATE': return 'warning';
    case 'DELETE': return 'danger';
    default: return 'info';
  }
}

function timelineIcon(type?: string): any {
  switch (type) {
    case 'CREATE': return Plus;
    case 'UPDATE': return EditPen;
    case 'DELETE': return Delete;
    default: return CircleCheck;
  }
}

function changeTypeTag(type?: string): any {
  switch (type) {
    case 'CREATE': return 'success';
    case 'UPDATE': return 'warning';
    case 'DELETE': return 'danger';
    default: return 'info';
  }
}

function changeTypeLabel(type?: string): string {
  switch (type) {
    case 'CREATE': return '新增';
    case 'UPDATE': return '修改';
    case 'DELETE': return '删除';
    default: return type || '未知';
  }
}
</script>

<style scoped lang="scss">
.version-history {
  padding: 8px 0;
}

.loading-state {
  padding: 24px;
}

.empty-state {
  padding: 40px 0;
}

.diff-selector {
  margin-bottom: 20px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
}

.version-card {
  padding: 8px 0;
}

.version-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.version-badge {
  font-weight: 700;
  color: var(--el-color-primary);
  font-size: 14px;
}

.version-user {
  color: #6b7280;
  font-size: 12px;
}

.version-summary {
  color: #374151;
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 4px;
}

.version-actions {
  display: flex;
  gap: 8px;
}

.snapshot-view {
  .snapshot-json {
    background: #f8fafc;
    padding: 16px;
    border-radius: 8px;
    overflow-x: auto;
    font-size: 12px;
    line-height: 1.6;
    max-height: 500px;
    overflow-y: auto;
  }
}
</style>
