<!--
  DataElementList —— ISO/IEC 11179-31 §7.6：值域 picker 候选按 kind 标签化
  并支持按 kind 多选过滤；kind 含义来自后端响应的 row.type（描述型/本地枚举/引用枚举）
-->
<template>
  <div>
    <h2 style="margin-top: 0;">数据元</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="dataElementApi.list"
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
        <el-table-column prop="dataElementConceptLabel" label="数据元概念" />
        <el-table-column prop="valueDomainLabel" label="值域" />
        <el-table-column prop="meta.registrationStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.meta?.registrationStatus)">
              {{ $t('registrationStatus.' + (row.meta?.registrationStatus || '')) }}
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
      <template #fields="{ form }">
        <el-form-item label="数据元概念" prop="dataElementConceptId">
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
        <el-form-item label="值域" prop="valueDomainId">
          <el-select
            v-model="form.valueDomainId"
            placeholder="选择值域"
            filterable
            clearable
            style="width: 100%;"
            @visible-change="(v: boolean) => { if (v) loadFormOptions() }"
          >
            <template #header>
              <div style="padding: 4px 8px;">
                <span style="font-size: 12px; color: var(--el-text-color-secondary);">按 kind 过滤：</span>
                <el-checkbox-group v-model="vdKindFilter" size="small">
                  <el-checkbox-button value="DESCRIBED">描述型</el-checkbox-button>
                  <el-checkbox-button value="LOCAL_ENUMERATED">本地枚举型</el-checkbox-button>
                  <el-checkbox-button value="REFERENCE_ENUMERATED">引用枚举型</el-checkbox-button>
                </el-checkbox-group>
              </div>
            </template>
            <el-option
              v-for="vd in filteredVdOptions"
              :key="vd.id"
              :label="vd.label"
              :value="vd.id"
            >
              <div style="display: flex; align-items: center; gap: 6px;">
                <span>{{ vd.label }}</span>
                <el-tag size="small" :type="typeTagType(vd.type)">{{ typeLabel(vd.type) }}</el-tag>
              </div>
            </el-option>
          </el-select>
        </el-form-item>
      </template>
    </ItemFormDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { dataElementApi, dataElementConceptApi, valueDomainApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';

type ValueDomainKind = 'DESCRIBED' | 'LOCAL_ENUMERATED' | 'REFERENCE_ENUMERATED';

const TYPE_LABEL: Record<string, string> = {
  DESCRIBED: '描述型',
  LOCAL_ENUMERATED: '本地枚举型',
  REFERENCE_ENUMERATED: '引用枚举型',
};
const TYPE_TAG_TYPE: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = {
  DESCRIBED: 'info',
  LOCAL_ENUMERATED: 'success',
  REFERENCE_ENUMERATED: 'warning',
};
function typeLabel(t?: string): string {
  return (t && TYPE_LABEL[t]) || '描述型';
}
function typeTagType(t?: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  return (t && TYPE_TAG_TYPE[t]) || 'info';
}

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ItemFormDialog>>();

const decOptions = ref<{ id: string; label: string }[]>([]);
const vdOptions = ref<{ id: string; label: string; type?: string }[]>([]);
const vdKindFilter = ref<ValueDomainKind[]>([]);
const optionsLoaded = ref(false);

const filteredVdOptions = computed(() => {
  if (!vdKindFilter.value || vdKindFilter.value.length === 0) return vdOptions.value;
  return vdOptions.value.filter((v) => v.type && vdKindFilter.value.includes(v.type as ValueDomainKind));
});

async function loadFormOptions() {
  if (optionsLoaded.value) return;
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
      type: r.type,
    }));
    optionsLoaded.value = true;
  } catch {
    // ignore
  }
}

async function handleCreate() {
  await loadFormOptions();
  vdKindFilter.value = [];
  dialog.value?.open();
}

async function handleEdit(row: any) {
  await loadFormOptions();
  vdKindFilter.value = [];
  dialog.value?.open(row);
}

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
    case 'PREFERRED': return 'success';
    case 'INCOMPLETE': return 'info';
    case 'SUBMITTED': return undefined as any;
    case 'RETIRED': return 'danger';
    default: return 'warning';
  }
}
</script>