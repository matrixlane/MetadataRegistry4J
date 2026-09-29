<template>
  <div>
    <h2 style="margin-top: 0;">角色</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="roleApi.list"
      :delete-fn="roleApi.delete"
      @create="openDialog()"
      @edit="(r) => openDialog(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="roleName" label="角色名称" min-width="140" sortable />
        <el-table-column label="电子邮箱" min-width="200">
          <template #default="{ row }">{{ (row.emailAddress || []).join('、') }}</template>
        </el-table-column>
        <el-table-column label="电话" min-width="160">
          <template #default="{ row }">{{ (row.phoneNumber || []).join('、') }}</template>
        </el-table-column>
        <el-table-column prop="mailAddress" label="邮政地址" min-width="180" show-overflow-tooltip />
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="角色"
      :submit-fn="submitFn"
      :rules="formRules"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="角色名称" prop="roleName" required>
          <el-input v-model="form.roleName" placeholder="如：注册管理员、数据管理员" />
        </el-form-item>
        <el-form-item label="电子邮箱" prop="emailAddress">
          <el-select
            v-model="form.emailAddress"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入邮箱后回车，可添加多个"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="电话" prop="phoneNumber">
          <el-select
            v-model="form.phoneNumber"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入电话后回车，可添加多个"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="邮政地址" prop="mailAddress">
          <el-input v-model="form.mailAddress" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { FormRules } from 'element-plus';
import { roleApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

const formRules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
};

function openDialog(row?: Record<string, unknown>) {
  dialog.value?.open(
    row
      ? {
          ...row,
          emailAddress: [...(Array.isArray(row.emailAddress) ? row.emailAddress : [])],
          phoneNumber: [...(Array.isArray(row.phoneNumber) ? row.phoneNumber : [])],
        }
      : { roleName: '', emailAddress: [], phoneNumber: [], mailAddress: '' },
  );
}

function submitFn(data: Record<string, unknown>) {
  if (data.id) return roleApi.update(data.id as string, data);
  return roleApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await roleApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
