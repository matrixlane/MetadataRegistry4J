<!--
  ValueDomainList —— ISO/IEC 11179-31 §7.4.2 三层 UI（与概念域同构）：
  - 一级 `kind`（DESCRIBED / ENUMERATED）；二级 `enumKind`（仅 ENUMERATED：LOCAL / REFERENCE）
  - 三分支互斥卡片：DESCRIBED（概念域选择器 + 公共字段）/ LOCAL_ENUMERATED（许可值）/ REFERENCE_ENUMERATED（外部源 + 引用 CD）
  - 与 conceptual-domain 三层 UI 同构
-->
<template>
  <div>
    <h2 style="margin-top: 0;">值域</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="valueDomainApi.list"
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
        <el-table-column prop="dataTypeLabel" label="数据类型" width="140" />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTagType(row.type)">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="meta.registrationStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ $t('registrationStatus.' + (row.meta?.registrationStatus || '')) }}</el-tag>
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ItemFormDialog
      ref="dialog"
      item-type="valueDomain"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="类型" prop="type" required>
          <div style="display: flex; gap: 8px; align-items: center;">
            <el-select
              v-model="form.kind"
              placeholder="一级"
              style="width: 160px;"
              @change="() => onKindChange(form)"
            >
              <el-option label="描述型" value="DESCRIBED" />
              <el-option label="枚举型" value="ENUMERATED" />
            </el-select>
            <el-select
              v-if="form.kind === 'ENUMERATED'"
              v-model="form.enumKind"
              placeholder="二级"
              style="width: 160px;"
              @change="() => onEnumKindChange(form)"
            >
              <el-option label="本地枚举型" value="LOCAL" />
              <el-option label="引用枚举型" value="REFERENCE" />
            </el-select>
            <span style="color: var(--el-text-color-secondary); font-size: 12px;">
              提交 type: <code>{{ form.type || '(未设置)' }}</code>
            </span>
          </div>
        </el-form-item>

        <el-form-item label="数据类型">
          <el-select
            v-model="form.dataTypeId"
            placeholder="选择数据类型"
            filterable
            clearable
            style="width: 100%;"
          >
            <el-option v-for="dt in dataTypeOptions" :key="dt.id" :label="dt.label" :value="dt.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="计量单位">
          <el-select
            v-model="form.unitOfMeasureId"
            placeholder="选择计量单位"
            filterable
            clearable
            style="width: 100%;"
          >
            <el-option v-for="uom in uomOptions" :key="uom.id" :label="uom.label" :value="uom.id" />
          </el-select>
        </el-form-item>

        <!-- DESCRIBED：仅最大字符数 / 格式 -->
        <template v-if="form.kind === 'DESCRIBED'">
          <el-form-item label="最大字符数">
            <el-input-number v-model="form.maximumCharacterQuantity" :min="0" style="width: 100%;" />
          </el-form-item>
          <el-form-item label="格式">
            <el-input v-model="form.format" placeholder="如 yyyy-MM-dd" />
          </el-form-item>
        </template>

        <!-- DESCRIBED：概念域选择器 -->
        <el-form-item v-if="form.kind === 'DESCRIBED'" label="概念域">
          <el-select
            v-model="form.describedConceptualDomainId"
            placeholder="选择描述型概念域"
            filterable
            clearable
            style="width: 100%;"
          >
            <el-option v-for="cd in cdOptions" :key="cd.id" :label="cd.label" :value="cd.id" />
          </el-select>
        </el-form-item>

        <!-- LOCAL_ENUMERATED：许可值编辑卡片 -->
        <el-card
          v-if="form.kind === 'ENUMERATED' && form.enumKind === 'LOCAL'"
          shadow="never"
          style="margin-top: 8px;"
        >
          <template #header><span>许可值</span></template>
          <div
            v-for="(pv, idx) in form.permissibleValues || []"
            :key="idx"
            style="margin-bottom: 16px; border-bottom: 1px solid #eee; padding-bottom: 8px;"
          >
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
              <span>许可值 #{{ idx + 1 }}</span>
              <el-button type="danger" size="small" text circle @click="removePermissibleValue(form, idx)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <DesignationDefinitionSection
              :designations="pv.designations"
              :definitions="pv.definitions"
              :show-pairings="false"
              :default-language-id="defaultLanguageId || undefined"
              @update:designations="pv.designations = $event"
              @update:definitions="pv.definitions = $event"
            />
            <div style="display: flex; gap: 8px; margin-top: 8px;">
              <el-date-picker
                v-model="pv.validPeriodStart"
                type="datetime"
                placeholder="有效开始时间"
                size="small"
                style="flex: 1;"
                value-format="YYYY-MM-DDTHH:mm:ss"
              />
              <el-date-picker
                v-model="pv.validPeriodEnd"
                type="datetime"
                placeholder="有效结束时间"
                size="small"
                style="flex: 1;"
                value-format="YYYY-MM-DDTHH:mm:ss"
              />
            </div>
          </div>
          <el-button type="primary" size="small" text @click="addPermissibleValue(form)">
            <el-icon><Plus /></el-icon>
            添加许可值
          </el-button>
        </el-card>

        <!-- REFERENCE_ENUMERATED：外部源卡片 -->
        <el-card
          v-if="form.kind === 'ENUMERATED' && form.enumKind === 'REFERENCE'"
          shadow="never"
          style="margin-top: 8px;"
        >
          <template #header><span>外部源</span></template>
          <el-form-item label="外部源字段名" required>
            <el-input v-model="form.permittedValuesFieldname" placeholder="如 code / alpha2" />
          </el-form-item>
          <el-form-item label="引用概念域">
            <el-select
              v-model="form.referencedConceptualDomainId"
              placeholder="选择引用枚举概念域（可空）"
              filterable
              clearable
              style="width: 100%;"
            >
              <el-option
                v-for="cd in refCdOptions"
                :key="cd.id"
                :label="cd.label"
                :value="cd.id"
              />
            </el-select>
          </el-form-item>
        </el-card>
      </template>
    </ItemFormDialog>
  </div>
</template>

<script setup lang="ts">
import { useRegistryDefaultLanguage } from '@/composables/useRegistryDefaultLanguage';

const { defaultLanguageId } = useRegistryDefaultLanguage();

import { ref } from 'vue';
import { Plus, Delete } from '@element-plus/icons-vue';
import { valueDomainApi, dataTypeApi, unitOfMeasureApi, conceptualDomainApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';
import DesignationDefinitionSection from '@/components/DesignationDefinitionSection.vue';

type ConceptKind = 'DESCRIBED' | 'ENUMERATED';
type EnumKind = 'LOCAL' | 'REFERENCE';

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

const dataTypeOptions = ref<{ id: string; label: string }[]>([]);
const uomOptions = ref<{ id: string; label: string }[]>([]);
const cdOptions = ref<{ id: string; label: string }[]>([]);
const refCdOptions = ref<{ id: string; label: string }[]>([]);
const optionsLoaded = ref(false);

async function loadFormOptions() {
  if (optionsLoaded.value) return;
  try {
    const [dtRes, uomRes, cdRes] = await Promise.all([
      dataTypeApi.list({ size: 1000 }),
      unitOfMeasureApi.list({ size: 1000 }),
      conceptualDomainApi.list({ size: 1000 }),
    ]);
    dataTypeOptions.value = (dtRes.content || []).map((r: any) => ({
      id: r.id, label: r.label || r.identifier || r.id,
    }));
    uomOptions.value = (uomRes.content || []).map((r: any) => ({
      id: r.id, label: r.label || r.identifier || r.id,
    }));
    cdOptions.value = (cdRes.content || []).map((r: any) => ({
      id: r.id, label: r.label || r.identifier || r.id,
    }));
    optionsLoaded.value = true;
  } catch {
    // ignore
  }
}

async function loadReferenceConceptualDomains() {
  try {
    const res = await conceptualDomainApi.list({ size: 1000, type: 'REFERENCE_ENUMERATED' } as any);
    refCdOptions.value = (res.content || []).map((r: any) => ({
      id: r.id, label: r.label || r.identifier || r.id,
    }));
  } catch {
    refCdOptions.value = [];
  }
}

function kindFromType(t?: string): ConceptKind {
  if (t === 'LOCAL_ENUMERATED' || t === 'REFERENCE_ENUMERATED') return 'ENUMERATED';
  return 'DESCRIBED';
}
function enumKindFromType(t?: string): EnumKind {
  if (t === 'REFERENCE_ENUMERATED') return 'REFERENCE';
  return 'LOCAL';
}
function computeType(kind?: ConceptKind, enumKind?: EnumKind): string {
  if (kind === 'DESCRIBED') return 'DESCRIBED';
  if (kind === 'ENUMERATED') {
    return enumKind === 'REFERENCE' ? 'REFERENCE_ENUMERATED' : 'LOCAL_ENUMERATED';
  }
  return 'DESCRIBED';
}

function onKindChange(form: Record<string, any>) {
  if (form.kind === 'DESCRIBED') {
    form.permissibleValues = [];
    form.permittedValuesFieldname = null;
    form.referencedConceptualDomainId = null;
  } else if (form.kind === 'ENUMERATED') {
    if (!form.enumKind) form.enumKind = 'LOCAL';
    form.describedConceptualDomainId = null;
  }
  form.type = computeType(form.kind, form.enumKind);
}

function onEnumKindChange(form: Record<string, any>) {
  if (form.enumKind === 'LOCAL') {
    form.permittedValuesFieldname = null;
    form.referencedConceptualDomainId = null;
  } else if (form.enumKind === 'REFERENCE') {
    form.permissibleValues = [];
  }
  form.type = computeType(form.kind, form.enumKind);
}

function addPermissibleValue(form: Record<string, unknown>) {
  const list = (form.permissibleValues || []) as any[];
  list.push({ designations: [], definitions: [], validPeriodStart: null, validPeriodEnd: null });
  form.permissibleValues = list;
}

function removePermissibleValue(form: Record<string, unknown>, idx: number) {
  const list = (form.permissibleValues || []) as any[];
  list.splice(idx, 1);
  form.permissibleValues = list;
}

async function handleCreate() {
  await loadFormOptions();
  await loadReferenceConceptualDomains();
  await dialog.value?.open();
  const form = (dialog.value as any)?.form as Record<string, any> | undefined;
  if (form) {
    form.kind = 'DESCRIBED';
    form.enumKind = undefined;
    form.type = 'DESCRIBED';
  }
}

async function handleEdit(row: Record<string, any>) {
  await loadFormOptions();
  await loadReferenceConceptualDomains();
  await dialog.value?.open(row);
  const form = (dialog.value as any)?.form as Record<string, any> | undefined;
  if (form) {
    form.kind = kindFromType(form.type);
    form.enumKind = form.kind === 'ENUMERATED' ? enumKindFromType(form.type) : undefined;
  }
}

function submitFn(data: Record<string, unknown>) {
  // 提交前清理不适用的字段
  if (data.kind === 'DESCRIBED') {
    data.permissibleValues = [];
    data.permittedValuesFieldname = null;
    data.referencedConceptualDomainId = null;
  } else if (data.kind === 'ENUMERATED' && data.enumKind === 'LOCAL') {
    data.permittedValuesFieldname = null;
    data.referencedConceptualDomainId = null;
  } else if (data.kind === 'ENUMERATED' && data.enumKind === 'REFERENCE') {
    data.permissibleValues = [];
  }
  // 引用枚举不应提交 maximumCharacterQuantity / format（后端会自动置空）
  if (data.enumKind === 'REFERENCE') {
    data.maximumCharacterQuantity = null;
    data.format = null;
  }
  delete data.kind;
  delete data.enumKind;
  if (data.id) return valueDomainApi.update(data.id as string, data);
  return valueDomainApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await valueDomainApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>