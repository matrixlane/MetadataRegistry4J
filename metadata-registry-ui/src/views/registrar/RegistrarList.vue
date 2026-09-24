<template>
  <div>
    <h2 style="margin-top: 0;">注册人员</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="registrarApi.list"
      :delete-fn="registrarApi.delete"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="identifier" label="标识符" width="160" sortable />
        <el-table-column label="名称">
          <template #default="{ row }">
            {{ row.individualName || row.organizationName || row.identifier || '-' }}
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="注册人员"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="标识符" prop="identifier" required>
          <el-input v-model="form.identifier" />
        </el-form-item>
        <el-form-item label="中文名称">
          <el-input v-model="form.titleZh" />
        </el-form-item>
        <el-form-item label="英文名称">
          <el-input v-model="form.titleEn" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { registrarApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return registrarApi.update(data.id as string, data);
  return registrarApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await registrarApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
