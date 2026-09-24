<template>
  <div>
    <h2 style="margin-top: 0;">维度</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="dimensionalityApi.list"
      :delete-fn="dimensionalityApi.delete"
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
        <el-table-column label="坐标指示" width="110">
          <template #default="{ row }">
            {{ coordinateLabel(row.coordinateIndicator) }}
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
      item-type="dimensionality"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <!-- 坐标指示 coordinate_indicator(0..1) 三态:
             TRUE=坐标测量、FALSE=区间测量、''(不适用)=非物理单位,提交时归一为 null -->
        <el-form-item label="坐标指示">
          <el-select v-model="form.coordinateIndicator" placeholder="不适用（非物理单位）" style="width: 100%;">
            <el-option label="是（坐标测量）" :value="true" />
            <el-option label="否（区间测量）" :value="false" />
            <el-option label="不适用" value="" />
          </el-select>
        </el-form-item>
        <!-- 计量类 dimensionality_measure_class(0..*) -->
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
import { dimensionalityApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';
import SmartItemPicker from '@/components/SmartItemPicker.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ItemFormDialog>>();

function coordinateLabel(value?: boolean | null): string {
  if (value === true) return '是（坐标）';
  if (value === false) return '否（区间）';
  return '不适用';
}

async function handleCreate() {
  await dialog.value?.open();
  const form = (dialog.value as any)?.form as Record<string, any> | undefined;
  if (form) {
    form.coordinateIndicator = '';
    form.measureClassIds = [];
  }
}

async function handleEdit(row: Record<string, any>) {
  await dialog.value?.open(row);
  const form = (dialog.value as any)?.form as Record<string, any> | undefined;
  if (form) {
    // 响应携带 measureClasses[{id,label}],picker 只需 id 数组;null(不适用)归一为哨兵 ''
    form.measureClassIds = (form.measureClasses || []).map((m: any) => m.id);
    form.coordinateIndicator = row.coordinateIndicator === true
      ? true
      : row.coordinateIndicator === false ? false : '';
  }
}

function submitFn(data: Record<string, unknown>) {
  const payload = { ...data };
  // '' = 不适用,归一化为后端三态 null
  if (payload.coordinateIndicator === '') payload.coordinateIndicator = null;
  if (payload.id) return dimensionalityApi.update(payload.id as string, payload);
  return dimensionalityApi.create(payload);
}

async function handleDelete(row: Record<string, unknown>) {
  await dimensionalityApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
