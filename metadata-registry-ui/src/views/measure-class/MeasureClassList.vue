<template>
  <div>
    <h2 style="margin-top: 0;">计量类</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="measureClassApi.list"
      :delete-fn="measureClassApi.delete"
      @create="handleCreate"
      @edit="handleEdit"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="meta.identifier" label="标识符" width="160" sortable />
        <el-table-column label="名称">
          <template #default="{ row }">
            {{ row.designations?.find((d: any) => d.languageCode === 'zh-CN')?.value || row.designations?.[0]?.value || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="维度数" width="100">
          <template #default="{ row }">
            {{ row.dimensionalities?.length ?? 0 }}
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ItemFormDialog
      ref="dialog"
      item-type="measureClass"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <!-- dimensionality_measure_class(0..*) -->
        <el-form-item label="维度">
          <SmartItemPicker
            v-model="form.dimensionalityIds"
            :item-types="['Dimensionality']"
            :show-advanced-filter="false"
            allow-multiple
            placeholder="搜索维度（可多选）"
          />
        </el-form-item>
      </template>
    </ItemFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { measureClassApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';
import SmartItemPicker from '@/components/SmartItemPicker.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ItemFormDialog>>();

async function handleCreate() {
  await dialog.value?.open();
  const form = (dialog.value as any)?.form as Record<string, any> | undefined;
  if (form) {
    form.dimensionalityIds = [];
  }
}

async function handleEdit(row: Record<string, any>) {
  await dialog.value?.open(row);
  const form = (dialog.value as any)?.form as Record<string, any> | undefined;
  if (form) {
    // 响应携带 dimensionalities[{id,label}],picker 只需 id 数组
    form.dimensionalityIds = (form.dimensionalities || []).map((d: any) => d.id);
  }
}

function submitFn(data: Record<string, unknown>) {
  if (data.id) return measureClassApi.update(data.id as string, data);
  return measureClassApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await measureClassApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
