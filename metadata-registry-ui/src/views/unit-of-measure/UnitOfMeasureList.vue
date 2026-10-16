<template>
  <div>
    <h2 style="margin-top: 0;">计量单位</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="unitOfMeasureApi.list"
      :delete-fn="unitOfMeasureApi.delete"
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
        <el-table-column label="计量类数" width="100">
          <template #default="{ row }">
            {{ row.measureClasses?.length ?? 0 }}
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ItemFormDialog
      ref="dialog"
      item-type="unitOfMeasure"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <!-- 计量类 unit_of_measure_class(1..*) -->
        <el-form-item label="计量类">
          <SmartItemPicker
            v-model="form.measureClassIds"
            :item-types="['MeasureClass']"
            :show-advanced-filter="false"
            allow-multiple
            placeholder="搜索计量类（可多选）"
          />
        </el-form-item>
      </template>
    </ItemFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { unitOfMeasureApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';
import SmartItemPicker from '@/components/SmartItemPicker.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ItemFormDialog>>();

async function handleCreate() {
  await dialog.value?.open();
  const form = (dialog.value as any)?.form as Record<string, any> | undefined;
  if (form) {
    form.measureClassIds = [];
  }
}

async function handleEdit(row: Record<string, any>) {
  await dialog.value?.open(row);
  const form = (dialog.value as any)?.form as Record<string, any> | undefined;
  if (form) {
    // 响应携带 measureClasses[{id,label}],picker 只需 id 数组
    form.measureClassIds = (form.measureClasses || []).map((m: any) => m.id);
  }
}

function submitFn(data: Record<string, unknown>) {
  if (data.id) return unitOfMeasureApi.update(data.id as string, data);
  return unitOfMeasureApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  try {
    await unitOfMeasureApi.delete(row.id as string);
    tableRef.value?.fetchData();
  } catch {
    // 失败提示已由 axios 拦截器统一弹出；此处吞掉避免未处理拒绝冒泡到错误边界导致整页崩溃
  }
}
</script>
