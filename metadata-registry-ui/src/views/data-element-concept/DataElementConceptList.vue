<template>
  <div>
    <h2 style="margin-top: 0;">数据元概念</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="dataElementConceptApi.list"
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
        <el-table-column prop="objectClassLabel" label="对象类" />
        <el-table-column prop="propertyLabel" label="特性" />
        <el-table-column prop="meta.registrationStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ $t('registrationStatus.' + (row.meta?.registrationStatus || '')) }}</el-tag>
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="数据元概念"
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
        <el-form-item label="对象类" required>
          <el-input v-model="form.objectClassId" placeholder="UUID" />
        </el-form-item>
        <el-form-item label="特性" required>
          <el-input v-model="form.propertyId" placeholder="UUID" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { dataElementConceptApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return dataElementConceptApi.update(data.id as string, data);
  return dataElementConceptApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await dataElementConceptApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
