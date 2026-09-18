<template>
  <div>
    <h2 style="margin-top: 0;">个人</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="individualApi.list"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="identifier" label="标识符" width="160" sortable />
        <el-table-column label="姓名">
          <template #default="{ row }">
            {{ row.name || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="title" label="职位" />
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="个人"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="标识符" prop="identifier">
          <el-input v-model="form.identifier" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="职位">
          <el-input v-model="form.title" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { individualApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return individualApi.update(data.id as string, data);
  return individualApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await individualApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
