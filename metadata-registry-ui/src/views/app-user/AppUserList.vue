<template>
  <div>
    <h2 style="margin-top: 0;">用户管理</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="appUserApi.list"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="username" label="用户名" width="120" sortable />
        <el-table-column prop="displayName" label="显示名" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.role === 'ADMIN' ? 'danger' : row.role === 'REGISTRAR' ? 'warning' : 'info'">
              {{ row.role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="启用" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" disabled />
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="用户"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="用户名" prop="username" required>
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.role">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="注册人员" value="REGISTRAR" />
            <el-option label="查看者" value="VIEWER" />
          </el-select>
        </el-form-item>
        <el-form-item label="密码" :required="!form.id">
          <el-input v-model="form.password" type="password" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { appUserApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return appUserApi.update(data.id as string, data);
  return appUserApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await appUserApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
