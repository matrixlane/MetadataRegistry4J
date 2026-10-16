<template>
  <div>
    <h2 style="margin-top: 0;">联邦节点</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="fetchNodes"
      :delete-fn="federationApi.deleteNode"
      @create="dialogVisible = true"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="name" label="名称" width="220" />
        <el-table-column prop="rai" label="注册机构标识 (RAI)" width="160" />
        <el-table-column prop="baseUrl" label="地址" show-overflow-tooltip>
          <template #default="{ row }">{{ row.baseUrl || '（离线节点）' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <el-dialog v-model="dialogVisible" title="登记联邦节点" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="节点名称" required>
          <el-input v-model="form.name" placeholder="例如：团队版-部门服务器" />
        </el-form-item>
        <el-form-item label="地址（可空表示离线节点）">
          <el-input v-model="form.baseUrl" placeholder="http://team-server:8083" />
        </el-form-item>
        <el-form-item label="注册机构标识 (RAI)" required>
          <el-input v-model="form.rai" placeholder="例如 RA" />
        </el-form-item>
        <el-form-item label="预共享令牌">
          <el-input v-model="form.authToken" type="password" show-password placeholder="对端验证用，服务端加密存储" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleCreate">登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import { federationApi } from '@/api/federation';
import DataTablePage from '@/components/DataTablePage.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialogVisible = ref(false);
const saving = ref(false);
const form = reactive({ name: '', baseUrl: '', rai: '', authToken: '' });

async function fetchNodes() {
  const items = await federationApi.listNodes();
  return { content: items, page: 0, size: items.length, totalElements: items.length, totalPages: 1 };
}

async function handleCreate() {
  if (!form.name.trim() || !form.rai.trim()) {
    ElMessage.error('节点名称与 RAI 为必填项');
    return;
  }
  saving.value = true;
  try {
    await federationApi.createNode({
      name: form.name,
      baseUrl: form.baseUrl || undefined,
      rai: form.rai,
      authToken: form.authToken || undefined,
    });
    ElMessage.success('节点已登记');
    dialogVisible.value = false;
    form.name = ''; form.baseUrl = ''; form.rai = ''; form.authToken = '';
    tableRef.value?.fetchData();
  } finally {
    saving.value = false;
  }
}

async function handleDelete(row: Record<string, unknown>) {
  try {
    await federationApi.deleteNode(row.id as string);
    tableRef.value?.fetchData();
  } catch {
    // 失败提示已由 axios 拦截器统一弹出；此处吞掉避免未处理拒绝冒泡到错误边界导致整页崩溃
  }
}
</script>
