<!--
  DataElementConceptList —— ISO/IEC 11179-31 §7.3.2。
  关联 picker（对象类 / 特性 / 概念域）统一替换为 SmartItemPicker
  （smart-item-picker），支持 intellisense 搜索 + 业务标识符显示 + 类型过滤。
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
            <el-tag size="small">{{ (row.meta?.registrationStatus ? $t('registrationStatus.' + row.meta.registrationStatus) : '-') }}</el-tag>
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
          <SmartItemPicker
            v-model="form.objectClassId"
            :item-types="['ObjectClass']"
            :show-advanced-filter="false"
            placeholder="搜索对象类（输入业务标识符或名称）"
          />
          <el-input v-model="form.objectClassId" placeholder="UUID" style="display: none;" />
        </el-form-item>
        <el-form-item label="特性">
          <SmartItemPicker
            v-model="form.propertyId"
            :item-types="['Property']"
            :show-advanced-filter="false"
            placeholder="搜索特性（输入业务标识符或名称）"
          />
        </el-form-item>
      </template>

      <!-- 关联 tab：data_element_concept_domain（§7.2.3.4，↔ CD 0..*）+ data_element_meaning（§7.6.3.2） -->
      <template #associations="{ form }">
        <el-form-item label="概念域">
          <SmartItemPicker
            v-model="form.conceptualDomainIds"
            :item-types="['ConceptualDomain']"
            :show-advanced-filter="false"
            :allow-multiple="true"
            placeholder="搜索概念域（可多选）"
          />
        </el-form-item>
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
import { ref } from 'vue';
import { dataElementConceptApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';
import SmartItemPicker from '@/components/SmartItemPicker.vue';
import ItemAssociationsPanel, { type AssociationPanelSection } from '@/components/ItemAssociationsPanel.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();

/** data_element_meaning（§7.6.3.2）：关联到本数据元概念的数据元（0..*）。 */
const panelSections: AssociationPanelSection[] = [
  {
    relationType: 'DATA_ELEMENT_MEANING',
    pathType: 'data-element-meaning',
    direction: 'INCOMING',
    focusRole: 'target',
    title: '数据元',
    targetItemTypes: ['DataElement'],
    addPlaceholder: '搜索要关联的数据元（输入业务标识符或名称）',
  },
];
const dialog = ref<InstanceType<typeof ItemFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return dataElementConceptApi.update(data.id as string, data);
  return dataElementConceptApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await dataElementConceptApi.delete(row.id as string);
  tableRef.value?.fetchData();
}

// 编辑态：open 后清理未被 SmartItemPicker 控制的 fields（向后兼容）。
// SmartItemPicker 直接写到 form.*Id；此处不再需要 loadFormOptions / loadReference 等。
async function handleCreate() {
  await dialog.value?.open();
}
async function handleEdit(row: Record<string, unknown>) {
  await dialog.value?.open(row);
}
</script>