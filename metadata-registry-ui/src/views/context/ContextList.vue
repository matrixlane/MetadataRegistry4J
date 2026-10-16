<template>
  <div>
    <h2 style="margin-top: 0;">上下文</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="contextApi.list"
      :delete-fn="contextApi.delete"
      :action-width="250"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column label="标识符" width="160" sortable prop="meta.identifier">
          <template #default="{ row }">
            {{ row.meta?.identifier || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="名称" sortable prop="meta.label">
          <template #default="{ row }">
            {{ row.designations?.find((d: any) => d.languageCode === 'zh-CN')?.value || row.designations?.[0]?.value || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.meta?.registrationStatus" size="small" :type="statusType(row.meta?.registrationStatus)">
              {{ (row.meta?.registrationStatus ? $t('registrationStatus.' + row.meta.registrationStatus) : '-') }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </template>
      <template #actions="{ row }">
        <el-button
          size="small"
          type="info"
          text
          :icon="Link"
          @click="openUsages(row)"
        >
          查看引用
        </el-button>
      </template>
    </DataTablePage>

    <ItemFormDialog
      ref="dialog"
      item-type="context"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <!-- Context 没有额外字段，属性标签页保持为空 -->
      </template>
    </ItemFormDialog>

    <ContextUsageDialog ref="usageDialog" :context-label="usageContextLabel" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { Link } from '@element-plus/icons-vue';
import { contextApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';
import ContextUsageDialog from '@/components/context/ContextUsageDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ItemFormDialog>>();
const usageDialog = ref<InstanceType<typeof ContextUsageDialog>>();
const usageContextLabel = ref<string>('');

function submitFn(data: Record<string, unknown>) {
  if (data.id) return contextApi.update(data.id as string, data);
  return contextApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  try {
    await contextApi.delete(row.id as string);
    tableRef.value?.fetchData();
  } catch {
    // 失败提示已由 axios 拦截器统一弹出；此处吞掉避免未处理拒绝冒泡到错误边界导致整页崩溃
  }
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

function openUsages(row: any) {
  usageContextLabel.value = row.designations?.find((d: any) => d.languageCode === 'zh-CN')?.value
    || row.designations?.[0]?.value
    || row.meta?.identifier
    || '—';
  usageDialog.value?.open(row.id, usageContextLabel.value);
}
</script>
