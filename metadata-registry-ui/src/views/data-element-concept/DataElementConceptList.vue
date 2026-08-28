<!--
  DataElementConceptList —— ISO/IEC 11179-31 §7.3.2：
  - "对象类" / "特性" 改用 picker（fallback UUID 输入框）
  - "概念域" 多选 picker（kind 标签化 + kind 过滤；与值域同构）
  - 编辑态从 row.conceptualDomainIds 回填
-->
<template>
  <div>
    <h2 style="margin-top: 0;">数据元概念</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="dataElementConceptApi.list"
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
        <el-table-column prop="objectClassLabel" label="对象类" />
        <el-table-column prop="propertyLabel" label="特性" />
        <el-table-column prop="meta.registrationStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ $t('registrationStatus.' + (row.meta?.registrationStatus || '')) }}</el-tag>
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ItemFormDialog
      ref="dialog"
      item-type="dataElementConcept"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="对象类">
          <el-select
            v-if="pickersReady"
            v-model="form.objectClassId"
            placeholder="选择对象类"
            filterable
            clearable
            style="width: 100%;"
          >
            <el-option
              v-for="oc in ocOptions"
              :key="oc.id"
              :label="oc.label"
              :value="oc.id"
            />
          </el-select>
          <el-input v-else v-model="form.objectClassId" placeholder="UUID" />
        </el-form-item>
        <el-form-item label="特性">
          <el-select
            v-if="pickersReady"
            v-model="form.propertyId"
            placeholder="选择特性"
            filterable
            clearable
            style="width: 100%;"
          >
            <el-option
              v-for="p in propOptions"
              :key="p.id"
              :label="p.label"
              :value="p.id"
            />
          </el-select>
          <el-input v-else v-model="form.propertyId" placeholder="UUID" />
        </el-form-item>
        <el-form-item label="概念域">
          <el-select
            v-model="form.conceptualDomainIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择概念域（可多选）"
            filterable
            clearable
            style="width: 100%;"
            @visible-change="(v: boolean) => { if (v) loadFormOptions() }"
          >
            <template #header>
              <div style="padding: 4px 8px;">
                <span style="font-size: 12px; color: var(--el-text-color-secondary);">按 kind 过滤：</span>
                <el-checkbox-group v-model="cdKindFilter" size="small">
                  <el-checkbox-button value="DESCRIBED">描述型</el-checkbox-button>
                  <el-checkbox-button value="LOCAL_ENUMERATED">本地枚举型</el-checkbox-button>
                  <el-checkbox-button value="REFERENCE_ENUMERATED">引用枚举型</el-checkbox-button>
                </el-checkbox-group>
              </div>
            </template>
            <el-option
              v-for="cd in filteredCdOptions"
              :key="cd.id"
              :label="cd.label"
              :value="cd.id"
            >
              <div style="display: flex; align-items: center; gap: 6px;">
                <span>{{ cd.label }}</span>
                <el-tag size="small" :type="typeTagType(cd.type)">{{ typeLabel(cd.type) }}</el-tag>
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
import { dataElementConceptApi, objectClassApi, propertyApi, conceptualDomainApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';

type ConceptDomainKind = 'DESCRIBED' | 'LOCAL_ENUMERATED' | 'REFERENCE_ENUMERATED';

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

const ocOptions = ref<{ id: string; label: string }[]>([]);
const propOptions = ref<{ id: string; label: string }[]>([]);
const cdOptions = ref<{ id: string; label: string; type?: string }[]>([]);
const cdKindFilter = ref<ConceptDomainKind[]>([]);
const pickersReady = ref(false);

const filteredCdOptions = computed(() => {
  if (!cdKindFilter.value || cdKindFilter.value.length === 0) return cdOptions.value;
  return cdOptions.value.filter((cd) => cd.type && cdKindFilter.value.includes(cd.type as ConceptDomainKind));
});

async function loadFormOptions() {
  if (pickersReady.value) return;
  try {
    const [ocRes, pRes, cdRes] = await Promise.all([
      objectClassApi.list({ size: 1000 } as any),
      propertyApi.list({ size: 1000 } as any),
      conceptualDomainApi.list({ size: 1000 }),
    ]);
    ocOptions.value = (ocRes.content || []).map((r: any) => ({
      id: r.id, label: r.label || r.identifier || r.id,
    }));
    propOptions.value = (pRes.content || []).map((r: any) => ({
      id: r.id, label: r.label || r.identifier || r.id,
    }));
    cdOptions.value = (cdRes.content || []).map((r: any) => ({
      id: r.id, label: r.label || r.identifier || r.id, type: r.type,
    }));
    pickersReady.value = true;
  } catch {
    // pickersReady stays false → 输入框 fallback 仍生效
  }
}

async function handleCreate() {
  await loadFormOptions();
  cdKindFilter.value = [];
  await dialog.value?.open();
  const form = (dialog.value as any)?.form as Record<string, any> | undefined;
  if (form) {
    // 多选 picker 默认空数组，避免 undefined 与 v-model 多选不兼容
    if (form.conceptualDomainIds === undefined) form.conceptualDomainIds = [];
  }
}

async function handleEdit(row: Record<string, any>) {
  await loadFormOptions();
  cdKindFilter.value = [];
  await dialog.value?.open(row);
  const form = (dialog.value as any)?.form as Record<string, any> | undefined;
  if (form) {
    // 后端 response 用 conceptualDomainIds 字段回填（已在 DEC service 中映射）
    if (form.conceptualDomainIds === undefined) form.conceptualDomainIds = [];
  }
}

function submitFn(data: Record<string, unknown>) {
  if (data.id) return dataElementConceptApi.update(data.id as string, data);
  return dataElementConceptApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await dataElementConceptApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>