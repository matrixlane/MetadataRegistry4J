<template>
  <div>
    <h2 style="margin-top: 0;">概念域</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="conceptualDomainApi.list"
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
        <el-table-column prop="type" label="类型" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ row.type === 'LOCAL_ENUMERATED' ? '局部枚举' : '描述型' }}</el-tag>
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
          <el-select v-model="form.type" placeholder="选择类型" style="width: 100%;">
            <el-option label="描述型" value="DESCRIBED" />
            <el-option label="局部枚举" value="LOCAL_ENUMERATED" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源 URI">
          <el-input v-model="form.sourceUri" placeholder="可选" />
        </el-form-item>
        <!-- ValueMeanings editor for LOCAL_ENUMERATED -->
        <el-card v-if="form.type === 'LOCAL_ENUMERATED'" shadow="never" style="margin-top: 8px;">
          <template #header>
            <span>值含义</span>
          </template>
          <div
            v-for="(vm, idx) in form.valueMeanings || []"
            :key="idx"
            style="margin-bottom: 16px; border-bottom: 1px solid #eee; padding-bottom: 8px;"
          >
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
              <span>值含义 #{{ idx + 1 }}</span>
              <el-button type="danger" size="small" text circle @click="removeValueMeaning(idx)">
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

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ItemFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return conceptualDomainApi.update(data.id as string, data);
  return conceptualDomainApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await conceptualDomainApi.delete(row.id as string);
  tableRef.value?.fetchData();
}

function addValueMeaning(form: Record<string, unknown>) {
  const list = (form.valueMeanings || []) as any[];
  list.push({ designations: [], definitions: [], validPeriodStart: null, validPeriodEnd: null });
  form.valueMeanings = list;
}

function removeValueMeaning(idx: number) {
  const dialogRef = dialog.value as any;
  if (!dialogRef) return;
  const form = dialogRef.form as Record<string, unknown>;
  const list = (form.valueMeanings || []) as any[];
  list.splice(idx, 1);
  form.valueMeanings = list;
}
</script>
