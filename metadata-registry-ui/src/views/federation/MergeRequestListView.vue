<template>
  <div>
    <h2 style="margin-top: 0;">合并请求</h2>

    <div class="mr-toolbar">
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 180px" @change="load">
        <el-option label="DRAFT" value="DRAFT" />
        <el-option label="SUBMITTED" value="SUBMITTED" />
        <el-option label="UNDER_REVIEW" value="UNDER_REVIEW" />
        <el-option label="MERGED" value="MERGED" />
        <el-option label="REJECTED" value="REJECTED" />
        <el-option label="FAILED" value="FAILED" />
      </el-select>
      <el-input v-model="bundlePath" placeholder="合并包文件路径（离线导入）" style="width: 380px" clearable />
      <el-button type="primary" plain :disabled="!bundlePath" @click="importBundle">导入合并包</el-button>
      <el-button :icon="Refresh" circle @click="load" />
    </div>

    <el-table :data="rows" v-loading="loading" style="width: 100%">
      <el-table-column label="来源节点" min-width="160">
        <template #default="{ row }">{{ row.sourceNodeName || row.sourceRai || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submittedBy" label="提交人" width="110" />
      <el-table-column prop="submittedAt" label="提交时间" width="180" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="submit(row)">提交</el-button>
          <el-button v-if="row.status === 'SUBMITTED'" link type="primary" @click="startReview(row)">开始评审</el-button>
          <el-button v-if="row.status === 'UNDER_REVIEW' || row.status === 'SUBMITTED'" link type="success"
                   @click="$router.push(`/federation/merge-requests/${row.id}`)">评审</el-button>
          <el-button v-if="row.status === 'UNDER_REVIEW'" link type="danger" @click="reject(row)">拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import { federationApi, type MergeRequestView } from '@/api/federation';

const rows = ref<MergeRequestView[]>([]);
const loading = ref(false);
const statusFilter = ref('');
const bundlePath = ref('');

async function load() {
  loading.value = true;
  try {
    rows.value = await federationApi.listMergeRequests(statusFilter.value || undefined);
  } finally {
    loading.value = false;
  }
}

async function importBundle() {
  try {
    const mr = await federationApi.importBundle(bundlePath.value);
    ElMessage.success(`合并包已导入（来源节点：${mr.sourceNodeName || '未知节点'}）`);
    bundlePath.value = '';
    load();
  } catch { /* 拦截器已提示 */ }
}

async function submit(row: MergeRequestView) {
  await federationApi.submitMergeRequest(row.id);
  load();
}

async function startReview(row: MergeRequestView) {
  await federationApi.startReview(row.id);
  load();
}

async function reject(row: MergeRequestView) {
  await federationApi.rejectMergeRequest(row.id);
  load();
}

function statusType(status: string) {
  switch (status) {
    case 'MERGED': return 'success';
    case 'UNDER_REVIEW': return 'warning';
    case 'REJECTED':
    case 'FAILED': return 'danger';
    default: return 'info';
  }
}

onMounted(load);
</script>

<style scoped>
.mr-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}
</style>
