<template>
  <div>
    <h2 style="margin-top: 0;">数据类型</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="dataTypeApi.list"
      :delete-fn="dataTypeApi.delete"
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
          <SmartItemPicker
            v-model="form.dataTypeSchemeId"
            :item-types="['DataTypeScheme']"
            :show-advanced-filter="false"
            :browse-on-open="true"
            placeholder="搜索数据类型方案（输入名称或标识符）"
          />
        </el-form-item>
        <el-form-item label="注记">
          <el-input
            v-model="form.annotation"
            type="textarea"
            :rows="2"
            placeholder="可选，用于进一步定义该数据类型的信息"
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
import SmartItemPicker from '@/components/SmartItemPicker.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ItemFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return dataTypeApi.update(data.id as string, data);
  return dataTypeApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  try {
    await dataTypeApi.delete(row.id as string);
    tableRef.value?.fetchData();
  } catch {
    // 失败提示已由 axios 拦截器统一弹出；此处吞掉避免未处理拒绝冒泡到错误边界导致整页崩溃
  }
}
</script>
