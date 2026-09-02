<!--
  ConceptualDomainList —— ISO/IEC 11179-31 §7.4.2 三层 UI。
  - 一级 kind（DESCRIBED / ENUMERATED）；二级 enumKind（仅 ENUMERATED：LOCAL / REFERENCE）
  - 三分支互斥卡片：描述型（来源 URI + 概念域 picker）/ 本地枚举型（值含义）/ 引用枚举型（无专属字段，§7.4.2.6.4）
  - 四实体双向关联由「关联」tab 的 ItemAssociationsPanel 管理（data_element_concept_domain / value_domain_meaning 反向）
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
            <el-tag size="small">{{ (row.meta?.registrationStatus ? $t('registrationStatus.' + row.meta.registrationStatus) : '-') }}</el-tag>
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
              style="width: 160px;"
              @change="() => onKindChange(form)"
            >
              <el-option label="描述型" value="DESCRIBED" />
              <el-option label="枚举型" value="ENUMERATED" />
            </el-select>
            <el-select
              v-if="form.kind === 'ENUMERATED'"
              v-model="form.enumKind"
              style="width: 160px;"
              @change="() => onEnumKindChange(form)"
            >
              <el-option label="本地枚举型" value="LOCAL" />
              <el-option label="引用枚举型" value="REFERENCE" />
            </el-select>
          </div>
        </el-form-item>

        <!-- DESCRIBED 描述型：来源 URI -->
        <template v-if="form.kind === 'DESCRIBED'">
          <el-form-item label="来源 URI">
            <el-input v-model="form.sourceUri" placeholder="可选" />
          </el-form-item>
        </template>

        <!-- 概念域有效期(ISO/IEC 11179-31 表 2 valid_period,必备;不填默认自创建起生效) -->
        <el-form-item label="有效期">
          <div style="display: flex; gap: 8px; width: 100%;">
            <el-date-picker
              v-model="form.validPeriodStart"
              type="datetime"
              placeholder="有效开始时间(默认自创建起)"
              size="small"
              style="flex: 1;"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
            <el-date-picker
              v-model="form.validPeriodEnd"
              type="datetime"
              placeholder="有效结束时间(可选)"
              size="small"
              style="flex: 1;"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </div>
        </el-form-item>

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

      </template>

      <!-- 关联 tab：data_element_concept_domain（§7.2.3.4，↔ DEC 0..*）+ value_domain_meaning（§7.4.4.1） -->
      <template #associations="{ form }">
        <ItemAssociationsPanel
          :item-id="form.id || null"
          :sections="panelSections"
          @changed="tableRef?.fetchData()"
        />
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
import DesignationDefinitionSection from '@/components/DesignationDefinitionSection.vue';
import ItemAssociationsPanel, { type AssociationPanelSection } from '@/components/ItemAssociationsPanel.vue';

/**
 * 概念域视角的双向关联：
 * - data_element_concept_domain（§7.2.3.4）：本概念域关联的数据元概念（0..*，多对多，ConceptualDomain 为拥有方）；
 * - value_domain_meaning（§7.4.4.1）：以本概念域为 meaning 的值域（0..*，§7.4.5.2.1 要求子类型一致）。
 */
const panelSections: AssociationPanelSection[] = [
  {
    relationType: 'DATA_ELEMENT_CONCEPT_DOMAIN',
    pathType: 'data-element-concept-domain',
    direction: 'OUTGOING',
    focusRole: 'target',
    title: '数据元概念',
    targetItemTypes: ['DataElementConcept'],
    addPlaceholder: '搜索要关联的数据元概念（输入业务标识符或名称）',
  },
  {
    relationType: 'VALUE_DOMAIN_MEANING',
    pathType: 'value-domain-meaning',
    direction: 'INCOMING',
    focusRole: 'target',
    title: '值域',
    targetItemTypes: ['ValueDomain'],
    addPlaceholder: '搜索要关联的值域（子类型须与本概念域一致）',
  },
];

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
  } else if (form.kind === 'ENUMERATED') {
    if (!form.enumKind) form.enumKind = 'LOCAL';
    form.sourceUri = '';
  }
  form.type = computeType(form.kind, form.enumKind);
}

function onEnumKindChange(form: Record<string, any>) {
  if (form.enumKind === 'REFERENCE') {
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