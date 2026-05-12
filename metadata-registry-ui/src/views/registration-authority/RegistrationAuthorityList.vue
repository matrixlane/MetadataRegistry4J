<template>
  <div>
    <h2 style="margin-top: 0;">注册机构</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="registrationAuthorityApi.list"
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
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="注册机构"
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
import { registrationAuthorityApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return registrationAuthorityApi.update(data.id as string, data);
  return registrationAuthorityApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await registrationAuthorityApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
