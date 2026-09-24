<template>
  <div>
    <h2 style="margin-top: 0;">组织</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="organizationApi.list"
      :delete-fn="organizationApi.delete"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="identifier" label="标识符" width="160" sortable />
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
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="标识符" prop="identifier" required>
          <el-input v-model="form.identifier" />
        </el-form-item>
        <el-form-item label="中文名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="英文名称">
          <el-input v-model="form.englishName" />
        </el-form-item>
        <el-form-item label="网站">
          <el-input v-model="form.webAddress" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { organizationApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return organizationApi.update(data.id as string, data);
  return organizationApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await organizationApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
