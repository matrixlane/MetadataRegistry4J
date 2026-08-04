<template>
  <div>
    <h2 style="margin-top: 0;">合并评审</h2>

    <div class="review-header" v-if="mr">
      <el-tag :type="mr.status === 'UNDER_REVIEW' ? 'warning' : 'info'">{{ mr.status }}</el-tag>
      <span class="mr-meta">来源：{{ mr.sourceNodeName || mr.sourceRai }}</span>
      <span class="mr-meta">提交人：{{ mr.submittedBy || '-' }}</span>
      <div style="margin-left: auto; display: flex; gap: 8px;">
        <el-button type="success" :loading="approving" :disabled="!canApprove" @click="approve">批准并入</el-button>
        <el-button type="danger" plain @click="$router.back()">返回</el-button>
      </div>
    </div>

    <el-table :data="items" v-loading="loading" style="width: 100%">
      <el-table-column prop="remoteItemKey" label="对端标识" min-width="200" />
      <el-table-column prop="itemLabel" label="名称" min-width="140">
        <template #default="{ row }">{{ row.itemLabel || '-' }}</template>
      </el-table-column>
      <el-table-column label="类别" width="150">
        <template #default="{ row }">
          <el-tag :type="changeTypeColor(row.changeType)" size="small">{{ changeTypeLabel(row.changeType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="版本（来源/合并基/本地）" min-width="170">
        <template #default="{ row }">
          <template v-if="row.changeType === 'CONFLICT'">
            <span class="version-triplet">
              {{ row.sourceVersion ?? '-' }} / {{ row.baseVersion ?? '-' }} / {{ row.localVersion ?? '-' }}
            </span>
          </template>
          <span v-else style="color: var(--el-text-color-secondary);">-</span>
        </template>
      </el-table-column>
      <el-table-column label="裁决" width="130">
        <template #default="{ row }">
          <el-tag v-if="row.resolution !== 'PENDING'" size="small">{{ resolutionLabel(row.resolution) }}</el-tag>
          <span v-else style="color: var(--el-text-color-secondary);">待裁决</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <template v-if="mr?.status === 'UNDER_REVIEW'">
            <template v-if="row.changeType === 'CONFLICT'">
              <el-button link type="success" size="small" @click="decide(row, 'ACCEPT')">采用传入</el-button>
              <el-button link type="danger" size="small" @click="decide(row, 'REJECT')">保留现有</el-button>
            </template>
            <template v-else>
              <el-button link type="success" size="small" @click="decide(row, 'ACCEPT')">接受</el-button>
              <el-button link type="danger" size="small" @click="decide(row, 'REJECT')">拒绝</el-button>
            </template>
            <el-button v-if="row.changeType === 'MATCH_CANDIDATE'" link type="primary" size="small"
                     @click="decide(row, 'LINK_EXISTING')">确认匹配</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { federationApi, type MergeRequestView, type MergeRequestItemView } from '@/api/federation';

const route = useRoute();
const router = useRouter();
const mrId = route.params.id as string;

const mr = ref<MergeRequestView | null>(null);
const items = ref<MergeRequestItemView[]>([]);
const loading = ref(false);
const approving = ref(false);

const canApprove = computed(() =>
  mr.value?.status === 'UNDER_REVIEW' &&
  items.value.every(i => i.resolution !== 'PENDING' || ['ADD', 'UPDATE'].includes(i.changeType))
);

async function load() {
  loading.value = true;
  try {
    const mrs = await federationApi.listMergeRequests();
    mr.value = mrs.find(m => m.id === mrId) || null;
    items.value = await federationApi.getMergeRequestItems(mrId);
  } finally {
    loading.value = false;
  }
}

async function decide(row: MergeRequestItemView, resolution: string) {
  let linkedId: string | undefined;
  if (resolution === 'LINK_EXISTING') {
    // 简化：前端暂不选本地条目，交由后端按标识符自动匹配
    linkedId = undefined;
  }
  await federationApi.decideItem(mrId, row.id, resolution, linkedId);
  await load();
}

async function approve() {
  approving.value = true;
  try {
    const result = await federationApi.approveMergeRequest(mrId);
    ElMessage.success(`并入完成：${result.merged} 项，跳过 ${result.skipped} 项`);
    router.push('/federation/merge-requests');
  } finally {
    approving.value = false;
  }
}

function changeTypeLabel(t: string) {
  return { ADD: '新增', UPDATE: '更新', CONFLICT: '冲突', MATCH_CANDIDATE: '匹配候选' }[t] || t;
}
function changeTypeColor(t: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
    ADD: 'success', UPDATE: 'primary', CONFLICT: 'danger', MATCH_CANDIDATE: 'warning',
  };
  return map[t] || 'info';
}
function resolutionLabel(r: string) {
  return { ACCEPT: '接受', REJECT: '拒绝', LINK_EXISTING: '链接既有' }[r] || r;
}

onMounted(load);
</script>

<style scoped>
.review-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.mr-meta {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.version-triplet {
  font-family: var(--el-font-family-monospace, monospace);
  font-size: 12px;
}
</style>
