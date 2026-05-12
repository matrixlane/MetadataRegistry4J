<template>
  <div>
    <h2 style="margin-top: 0;">联系人</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="contactApi.list"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="identifier" label="标识符" width="160" sortable />
        <el-table-column prop="organizationName" label="组织" />
        <el-table-column prop="individualName" label="个人" />
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="联系人"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="标识符" prop="identifier" required>
          <el-input v-model="form.identifier" />
        </el-form-item>
        <el-form-item label="组织ID">
          <el-input v-model="form.organizationId" placeholder="UUID" />
        </el-form-item>
        <el-form-item label="个人ID">
          <el-input v-model="form.individualId" placeholder="UUID" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { contactApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return contactApi.update(data.id as string, data);
  return contactApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await contactApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
