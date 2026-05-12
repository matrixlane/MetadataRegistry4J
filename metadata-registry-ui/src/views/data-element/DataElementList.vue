<template>
  <div>
    <h2 style="margin-top: 0;">数据元</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="dataElementApi.list"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="identifier" label="标识符" width="160" sortable />
        <el-table-column label="名称">
          <template #default="{ row }">
            {{ row.designations?.find((d: any) => d.languageCode === 'zh-CN')?.value || row.designations?.[0]?.value || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="dataElementConceptName" label="数据元概念" />
        <el-table-column prop="valueDomainName" label="值域" />
        <el-table-column prop="registrationStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.registrationStatus)">
              {{ $t('registrationStatus.' + row.registrationStatus) }}
            </el-tag>
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ItemFormDialog
      ref="dialog"
      title="数据元"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="数据元概念" prop="dataElementConceptId" required>
          <el-select
            v-model="form.dataElementConceptId"
            placeholder="选择数据元概念"
            filterable
            clearable
            style="width: 100%;"
          >
            <el-option
              v-for="dec in decOptions"
              :key="dec.id"
              :label="dec.label"
              :value="dec.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="值域" prop="valueDomainId" required>
          <el-select
            v-model="form.valueDomainId"
            placeholder="选择值域"
            filterable
            clearable
            style="width: 100%;"
          >
            <el-option
              v-for="vd in vdOptions"
              :key="vd.id"
              :label="vd.label"
              :value="vd.id"
            />
          </el-select>
        </el-form-item>
      </template>
    </ItemFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { dataElementApi, dataElementConceptApi, valueDomainApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ItemFormDialog>>();

const decOptions = ref<{ id: string; label: string }[]>([]);
const vdOptions = ref<{ id: string; label: string }[]>([]);

onMounted(async () => {
  try {
    const [decRes, vdRes] = await Promise.all([
      dataElementConceptApi.list({ size: 1000 }),
      valueDomainApi.list({ size: 1000 }),
    ]);
    decOptions.value = (decRes.content || []).map((r: any) => ({
      id: r.id,
      label: r.label || r.identifier || r.id,
    }));
    vdOptions.value = (vdRes.content || []).map((r: any) => ({
      id: r.id,
      label: r.label || r.identifier || r.id,
    }));
  } catch {
    // ignore
  }
});

function submitFn(data: Record<string, unknown>) {
  if (data.id) return dataElementApi.update(data.id as string, data);
  return dataElementApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await dataElementApi.delete(row.id as string);
  tableRef.value?.fetchData();
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
