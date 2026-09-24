<!--
  DataElementList —— ISO/IEC 11179-31 §7.6。
  "数据元概念" 与 "值域" 关联 picker 统一替换为 SmartItemPicker
  （smart-item-picker），支持 intellisense 搜索 + 业务标识符显示 + 类型过滤。
-->
<template>
  <div>
    <h2 style="margin-top: 0;">数据元</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="dataElementApi.list"
      :delete-fn="dataElementApi.delete"
      @create="handleCreate"
      @edit="handleEdit"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="meta.identifier" label="标识符" width="160" sortable />
        <!-- meta.label 为虚拟排序键:后端 ItemSortPageableResolver 改写为 designations.sign.value -->
        <el-table-column prop="meta.label" label="名称" sortable>
          <template #default="{ row }">
            {{ row.designations?.find((d: any) => d.languageCode === 'zh-CN')?.value || row.designations?.[0]?.value || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="dataElementConceptLabel" label="数据元概念" />
        <el-table-column prop="valueDomainLabel" label="值域" />
        <el-table-column prop="meta.registrationStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.meta?.registrationStatus)">
              {{ (row.meta?.registrationStatus ? $t('registrationStatus.' + row.meta.registrationStatus) : '-') }}
            </el-tag>
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ItemFormDialog
      ref="dialog"
      item-type="dataElement"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <!-- 属性 tab：precision（ISO/IEC 11179-31:2023 §7.6.2.1.4 Table 23，0..1 Integer） -->
      <template #fields="{ form }">
        <el-form-item label="精度">
          <el-input-number
            v-model="form.precision"
            :min="0"
            style="width: 100%;"
            placeholder="关联数据元值允许的小数位数"
          />
        </el-form-item>
      </template>

      <!-- 关联 tab：data_element_meaning（§7.6.3.2，→ DEC 0..1）+ data_element_domain（§7.6.3.1，→ VD 0..1） -->
      <template #associations="{ form }">
        <el-form-item label="数据元概念" prop="dataElementConceptId">
          <SmartItemPicker
            v-model="form.dataElementConceptId"
            :item-types="['DataElementConcept']"
            :show-advanced-filter="false"
            placeholder="搜索数据元概念（输入业务标识符或名称）"
          />
        </el-form-item>
        <el-form-item label="值域" prop="valueDomainId">
          <SmartItemPicker
            v-model="form.valueDomainId"
            :item-types="['ValueDomain']"
            :show-advanced-filter="false"
            placeholder="搜索值域（输入业务标识符或名称）"
          />
        </el-form-item>
      </template>
    </ItemFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { dataElementApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';
import SmartItemPicker from '@/components/SmartItemPicker.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ItemFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return dataElementApi.update(data.id as string, data);
  return dataElementApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await dataElementApi.delete(row.id as string);
  tableRef.value?.fetchData();
}

async function handleCreate() {
  await dialog.value?.open();
}
async function handleEdit(row: Record<string, unknown>) {
  await dialog.value?.open(row);
}

function statusType(status?: string) {
  switch (status) {
    case 'STANDARD': return 'success';
    case 'PREFERRED_STANDARD': return 'success';
    case 'INCOMPLETE': return 'info';
    case 'CANDIDATE': return undefined as any;
    case 'RETIRED': return 'danger';
    default: return 'warning';
  }
}
</script>