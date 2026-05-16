<template>
  <div>
    <h2 style="margin-top: 0;">上下文</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="contextApi.list"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column label="标识符" width="160" sortable>
          <template #default="{ row }">
            {{ row.meta?.identifier || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="名称">
          <template #default="{ row }">
            {{ row.designations?.find((d: any) => d.languageCode === 'zh-CN')?.value || row.designations?.[0]?.value || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.meta?.registrationStatus" size="small" :type="statusType(row.meta?.registrationStatus)">
              {{ $t('registrationStatus.' + row.meta?.registrationStatus) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ItemFormDialog
      ref="dialog"
      title="上下文"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <!-- Context 没有额外字段，属性标签页保持为空 -->
      </template>
    </ItemFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { contextApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ItemFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return contextApi.update(data.id as string, data);
  return contextApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await contextApi.delete(row.id as string);
  tableRef.value?.fetchData();
}

function statusType(status?: string) {
  switch (status) {
    case 'STANDARD': return 'success';
    case 'PREFERRED': return 'success';
    case 'INCOMPLETE': return 'info';
    case 'SUBMITTED': return undefined as any;
    case 'RETIRED': return 'danger';
    default: return 'warning';
  }
}
</script>