<template>
  <div>
    <h2 style="margin-top: 0;">注册项生命周期</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="administeredItemApi.list"
      :can-create="false"
      :can-edit="false"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="identifier" label="标识符" width="160" sortable />
        <el-table-column label="名称">
          <template #default="{ row }">
            {{ row.designations?.find((d: any) => d.languageCode === 'zh-CN')?.value || row.designations?.[0]?.value || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="registrationStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.registrationStatus)">
              {{ $t('registrationStatus.' + row.registrationStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="effectiveDate" label="生效日期" width="160" />
      </template>
      <template #actions="{ row }">
        <el-button size="small" text type="primary" @click="openTransition(row)">
          状态迁移
        </el-button>
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="状态迁移"
      :submit-fn="submitTransition"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="目标状态" required>
          <el-select v-model="form.to" placeholder="选择目标状态">
            <el-option
              v-for="s in allowedStatuses"
              :key="s"
              :label="$t('registrationStatus.' + s)"
              :value="s"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" type="textarea" :rows="3" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { administeredItemApi } from '@/api/resources';
import request from '@/utils/request';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();
const currentRow = ref<Record<string, unknown> | null>(null);

const allowedStatuses = computed(() => [
  'INCOMPLETE', 'CANDIDATE', 'RECORDED', 'QUALIFIED',
  'STANDARD', 'PREFERRED_STANDARD', 'SUPERSEDED', 'RETIRED', 'HISTORICAL'
]);

function statusType(status?: string) {
  switch (status) {
    case 'STANDARD': return 'success';
    case 'PREFERRED_STANDARD': return 'success';
    case 'INCOMPLETE': return 'info';
    case 'CANDIDATE': return '';
    case 'RETIRED': return 'danger';
    default: return 'warning';
  }
}

function openTransition(row: Record<string, unknown>) {
  currentRow.value = row;
  dialog.value?.open({ to: undefined, note: '' });
}

async function submitTransition(data: Record<string, unknown>) {
  if (!currentRow.value) return Promise.reject();
  return request.post(`/administered-items/${currentRow.value.id}/transition`, data);
}

async function handleDelete(row: Record<string, unknown>) {
  await administeredItemApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
