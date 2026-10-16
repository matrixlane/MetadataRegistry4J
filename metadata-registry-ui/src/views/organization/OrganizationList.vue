<template>
  <div>
    <h2 style="margin-top: 0;">组织</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="organizationApi.list"
      :delete-fn="organizationApi.delete"
      @create="openDialog()"
      @edit="(r) => openDialog(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column label="名称">
          <template #default="{ row }">
            {{ row.name || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="webAddress" label="网站" />
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="组织"
      :submit-fn="submitFn"
      :rules="formRules"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="中文名称" prop="name" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="英文名称" prop="englishName">
          <el-input v-model="form.englishName" />
        </el-form-item>
        <el-form-item label="邮政地址" prop="mailAddress">
          <el-input v-model="form.mailAddress" placeholder="该组织的邮政地址" />
        </el-form-item>
        <el-form-item label="电子邮箱" prop="emailAddress">
          <el-select
            v-model="form.emailAddress"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="该组织的联系电子邮件地址，不与特定个人或角色关联"
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
            placeholder="该组织的电话号码"
            style="width: 100%;"
          />
        </el-form-item>
        <el-form-item label="网站" prop="webAddress">
          <el-input v-model="form.webAddress" placeholder="该组织网站的 URL" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { FormRules } from 'element-plus';
import { organizationApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

// ISO/IEC 11179-3:2023 表 2：name 多重性 1..*，必填
const formRules: FormRules = {
  name: [{ required: true, message: '请输入中文名称', trigger: 'blur' }],
};

function openDialog(row?: Record<string, unknown>) {
  dialog.value?.open(
    row
      ? {
          ...row,
          emailAddress: [...(Array.isArray(row.emailAddress) ? row.emailAddress : [])],
          phoneNumber: [...(Array.isArray(row.phoneNumber) ? row.phoneNumber : [])],
        }
      : { name: '', englishName: '', mailAddress: '', emailAddress: [], phoneNumber: [], webAddress: '' },
  );
}

function submitFn(data: Record<string, unknown>) {
  if (data.id) return organizationApi.update(data.id as string, data);
  return organizationApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  try {
    await organizationApi.delete(row.id as string);
    tableRef.value?.fetchData();
  } catch {
    // 失败提示已由 axios 拦截器统一弹出；此处吞掉避免未处理拒绝冒泡到错误边界导致整页崩溃
  }
}
</script>
