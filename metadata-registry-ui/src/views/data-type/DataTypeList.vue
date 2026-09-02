<template>
  <div>
    <h2 style="margin-top: 0;">数据类型</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="dataTypeApi.list"
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
        <el-table-column prop="dataTypeSchemeLabel" label="数据类型方案" />
        <el-table-column prop="meta.registrationStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ (row.meta?.registrationStatus ? $t('registrationStatus.' + row.meta.registrationStatus) : '-') }}</el-tag>
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ItemFormDialog
      ref="dialog"
      item-type="dataType"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="数据类型方案">
          <el-input v-model="form.dataTypeSchemeId" placeholder="UUID" />
        </el-form-item>
        <el-form-item label="注记">
          <el-input
            v-model="form.annotation"
            type="textarea"
            :rows="2"
            placeholder="可选（ISO/IEC 11179-31 表 8 annotation，进一步说明该数据类型）"
          />
        </el-form-item>
      </template>
    </ItemFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { dataTypeApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ItemFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return dataTypeApi.update(data.id as string, data);
  return dataTypeApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await dataTypeApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
