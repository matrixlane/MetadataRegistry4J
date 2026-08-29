<!--
  ConceptualDomainList —— ISO/IEC 11179-31 §7.4.2 三层 UI。
  - 一级 kind（DESCRIBED / ENUMERATED）；二级 enumKind（仅 ENUMERATED：LOCAL / REFERENCE）
  - 三分支互斥卡片：描述型（来源 URI + 概念域 picker）/ 本地枚举型（值含义）/ 引用枚举型（外部源字段）
  - 概念域 picker 替换为 SmartItemPicker（smart-item-picker）
-->
<template>
  <div>
    <h2 style="margin-top: 0;">概念域</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="conceptualDomainApi.list"
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
        <el-table-column prop="type" label="类型" width="140">
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
      item-type="conceptualDomain"
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

        <!-- DESCRIBED 描述型：来源 URI + 概念域 picker -->
        <template v-if="form.kind === 'DESCRIBED'">
          <el-form-item label="来源 URI">
            <el-input v-model="form.sourceUri" placeholder="可选" />
          </el-form-item>
          <el-form-item label="概念域">
            <SmartItemPicker
              v-model="form.describedConceptualDomainId"
              :item-types="['ConceptualDomain']"
              :show-advanced-filter="false"
              placeholder="搜索描述型概念域"
            />
          </el-form-item>
        </template>

        <!-- LOCAL_ENUMERATED 本地枚举型：值含义编辑卡片 -->
        <el-card
          v-if="form.kind === 'ENUMERATED' && form.enumKind === 'LOCAL'"
          shadow="never"
          style="margin-top: 8px;"
        >
          <template #header><span>值含义</span></template>
          <div
            v-for="(vm, idx) in form.valueMeanings || []"
            :key="idx"
            style="margin-bottom: 16px; border-bottom: 1px solid #eee; padding-bottom: 8px;"
          >
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
              <span>值含义 #{{ idx + 1 }}</span>
              <el-button type="danger" size="small" text circle @click="removeValueMeaning(form, idx)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <DesignationDefinitionSection
              :designations="vm.designations"
              :definitions="vm.definitions"
              :show-pairings="false"
              :default-language-id="defaultLanguageId || undefined"
              @update:designations="vm.designations = $event"
              @update:definitions="vm.definitions = $event"
            />
            <div style="display: flex; gap: 8px; margin-top: 8px;">
              <el-date-picker
                v-model="vm.validPeriodStart"
                type="datetime"
                placeholder="有效开始时间"
                size="small"
                style="flex: 1;"
                value-format="YYYY-MM-DDTHH:mm:ss"
              />
              <el-date-picker
                v-model="vm.validPeriodEnd"
                type="datetime"
                placeholder="有效结束时间"
                size="small"
                style="flex: 1;"
                value-format="YYYY-MM-DDTHH:mm:ss"
              />
            </div>
          </div>
          <el-button type="primary" size="small" text @click="addValueMeaning(form)">
            <el-icon><Plus /></el-icon>
            添加值含义
          </el-button>
        </el-card>

        <!-- REFERENCE_ENUMERATED 引用枚举型：外部源卡片 -->
        <el-card
          v-if="form.kind === 'ENUMERATED' && form.enumKind === 'REFERENCE'"
          shadow="never"
          style="margin-top: 8px;"
        >
          <template #header><span>外部源</span></template>
          <el-form-item label="外部源字段名" required>
            <el-input v-model="form.permittedValuesFieldname" placeholder="如 code / alpha2" />
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
import { conceptualDomainApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';
import SmartItemPicker from '@/components/SmartItemPicker.vue';
import DesignationDefinitionSection from '@/components/DesignationDefinitionSection.vue';

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

function kindFromType(t?: string): 'DESCRIBED' | 'ENUMERATED' {
  if (t === 'LOCAL_ENUMERATED' || t === 'REFERENCE_ENUMERATED') return 'ENUMERATED';
  return 'DESCRIBED';
}
function enumKindFromType(t?: string): 'LOCAL' | 'REFERENCE' {
  if (t === 'REFERENCE_ENUMERATED') return 'REFERENCE';
  return 'LOCAL';
}
function computeType(kind?: string, enumKind?: string): string {
  if (kind === 'DESCRIBED') return 'DESCRIBED';
  if (kind === 'ENUMERATED') {
    return enumKind === 'REFERENCE' ? 'REFERENCE_ENUMERATED' : 'LOCAL_ENUMERATED';
  }
  return 'DESCRIBED';
}

function onKindChange(form: Record<string, any>) {
  if (form.kind === 'DESCRIBED') {
    form.valueMeanings = [];
    form.permittedValuesFieldname = null;
  } else if (form.kind === 'ENUMERATED') {
    if (!form.enumKind) form.enumKind = 'LOCAL';
    form.sourceUri = '';
  }
  form.type = computeType(form.kind, form.enumKind);
}

function onEnumKindChange(form: Record<string, any>) {
  if (form.enumKind === 'LOCAL') {
    form.permittedValuesFieldname = null;
  } else if (form.enumKind === 'REFERENCE') {
    form.valueMeanings = [];
  }
  form.type = computeType(form.kind, form.enumKind);
}

function addValueMeaning(form: Record<string, unknown>) {
  const list = (form.valueMeanings || []) as any[];
  list.push({ designations: [], definitions: [], validPeriodStart: null, validPeriodEnd: null });
  form.valueMeanings = list;
}

function removeValueMeaning(form: Record<string, unknown>, idx: number) {
  const list = (form.valueMeanings || []) as any[];
  list.splice(idx, 1);
  form.valueMeanings = list;
}

async function handleCreate() {
  await dialog.value?.open();
  const form = (dialog.value as any)?.form as Record<string, any> | undefined;
  if (form) {
    form.kind = 'DESCRIBED';
    form.enumKind = undefined;
    form.type = 'DESCRIBED';
  }
}

async function handleEdit(row: Record<string, any>) {
  await dialog.value?.open(row);
  const form = (dialog.value as any)?.form as Record<string, any> | undefined;
  if (form) {
    form.kind = kindFromType(form.type);
    form.enumKind = form.kind === 'ENUMERATED' ? enumKindFromType(form.type) : undefined;
  }
}

function submitFn(data: Record<string, unknown>) {
  if (data.kind === 'DESCRIBED') {
    data.valueMeanings = [];
    data.permittedValuesFieldname = null;
  } else if (data.kind === 'ENUMERATED' && data.enumKind === 'LOCAL') {
    data.permittedValuesFieldname = null;
  } else if (data.kind === 'ENUMERATED' && data.enumKind === 'REFERENCE') {
    data.valueMeanings = [];
  }
  delete data.kind;
  delete data.enumKind;
  if (data.id) return conceptualDomainApi.update(data.id as string, data);
  return conceptualDomainApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await conceptualDomainApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>