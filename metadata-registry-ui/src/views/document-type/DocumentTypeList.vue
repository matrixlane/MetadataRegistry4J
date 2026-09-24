<template>
  <div>
    <h2 style="margin-top: 0;">文档类型</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="documentTypeApi.list"
      :delete-fn="documentTypeApi.delete"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="identifier" label="标识符" width="160" sortable />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="schemeReference" label="方案引用" />
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="文档类型"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="标识符" prop="identifier" required>
          <el-input v-model="form.identifier" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" />
        </el-form-item>
        <el-form-item label="方案引用">
          <el-input v-model="form.schemeReference" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { documentTypeApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return documentTypeApi.update(data.id as string, data);
  return documentTypeApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await documentTypeApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
