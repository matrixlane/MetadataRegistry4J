<template>
  <div>
    <h2 style="margin-top: 0;">对象类</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="objectClassApi.list"
      :delete-fn="objectClassApi.delete"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="meta.identifier" label="标识符" width="160" sortable />
        <el-table-column label="名称">
          <template #default="{ row }">
            {{ row.designations?.find((d: any) => d.languageCode === 'zh-CN')?.value || row.designations?.[0]?.value || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="meta.registrationStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ (row.meta?.registrationStatus ? $t('registrationStatus.' + row.meta.registrationStatus) : '-') }}</el-tag>
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ItemFormDialog
      ref="dialog"
      item-type="objectClass"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields />
    </ItemFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { objectClassApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ItemFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return objectClassApi.update(data.id as string, data);
  return objectClassApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await objectClassApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
