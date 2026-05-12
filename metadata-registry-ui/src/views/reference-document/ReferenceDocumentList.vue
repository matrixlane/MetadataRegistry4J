<template>
  <div>
    <h2 style="margin-top: 0;">参考文献</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="referenceDocumentApi.list"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="identifier" label="标识符" width="160" sortable />
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="uri" label="URI" />
        <el-table-column prop="typeDescriptionName" label="文档类型" width="140" />
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="参考文献"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="标识符" prop="identifier" required>
          <el-input v-model="form.identifier" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="URI">
          <el-input v-model="form.uri" />
        </el-form-item>
        <el-form-item label="文档类型" required>
          <el-input v-model="form.typeDescriptionId" placeholder="UUID" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { referenceDocumentApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return referenceDocumentApi.update(data.id as string, data);
  return referenceDocumentApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await referenceDocumentApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
