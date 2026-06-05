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
      title="值域"
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
        <el-form-item label="数据类型">
          <el-select
            v-model="form.dataTypeId"
            placeholder="选择数据类型"
            filterable
            clearable
            style="width: 100%;"
          >
            <el-option
              v-for="dt in dataTypeOptions"
              :key="dt.id"
              :label="dt.label"
              :value="dt.id"
            />
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
            <el-option
              v-for="uom in uomOptions"
              :key="uom.id"
              :label="uom.label"
              :value="uom.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="最大字符数">
          <el-input-number v-model="form.maximumCharacterQuantity" :min="0" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="格式">
          <el-input v-model="form.format" placeholder="如 yyyy-MM-dd" />
        </el-form-item>
        <el-form-item v-if="form.type === 'DESCRIBED'" label="概念域">
          <el-select
            v-model="form.describedConceptualDomainId"
            placeholder="选择描述型概念域"
            filterable
            clearable
            style="width: 100%;"
          >
            <el-option
              v-for="cd in cdOptions"
              :key="cd.id"
              :label="cd.label"
              :value="cd.id"
            />
          </el-select>
        </el-form-item>

        <!-- PermissibleValues editor for LOCAL_ENUMERATED -->
        <el-card v-if="form.type === 'LOCAL_ENUMERATED'" shadow="never" style="margin-top: 8px;">
          <template #header>
            <span>许可值</span>
          </template>
          <div
            v-for="(pv, idx) in form.permissibleValues || []"
            :key="idx"
            style="margin-bottom: 16px; border-bottom: 1px solid #eee; padding-bottom: 8px;"
          >
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
              <span>许可值 #{{ idx + 1 }}</span>
              <el-button type="danger" size="small" text circle @click="removePermissibleValue(idx)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <MultilingualTextEditor v-model="pv.designations" label="指称" />
            <div style="margin-top: 8px;">
              <MultilingualTextEditor v-model="pv.definitions" label="定义" />
            </div>
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
      </template>
    </ItemFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { Plus, Delete } from '@element-plus/icons-vue';
import { valueDomainApi, dataTypeApi, unitOfMeasureApi, conceptualDomainApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ItemFormDialog from '@/components/ItemFormDialog.vue';
import MultilingualTextEditor from '@/components/MultilingualTextEditor.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ItemFormDialog>>();

const dataTypeOptions = ref<{ id: string; label: string }[]>([]);
const uomOptions = ref<{ id: string; label: string }[]>([]);
const cdOptions = ref<{ id: string; label: string }[]>([]);
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
      id: r.id,
      label: r.label || r.identifier || r.id,
    }));
    uomOptions.value = (uomRes.content || []).map((r: any) => ({
      id: r.id,
      label: r.label || r.identifier || r.id,
    }));
    cdOptions.value = (cdRes.content || []).map((r: any) => ({
      id: r.id,
      label: r.label || r.identifier || r.id,
    }));
    optionsLoaded.value = true;
  } catch {
    // ignore
  }
}

async function handleCreate() {
  await loadFormOptions();
  dialog.value?.open();
}

async function handleEdit(row: any) {
  await loadFormOptions();
  dialog.value?.open(row);
}

function submitFn(data: Record<string, unknown>) {
  if (data.id) return valueDomainApi.update(data.id as string, data);
  return valueDomainApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await valueDomainApi.delete(row.id as string);
  tableRef.value?.fetchData();
}

function addPermissibleValue(form: Record<string, unknown>) {
  const list = (form.permissibleValues || []) as any[];
  list.push({ designations: [], definitions: [], validPeriodStart: null, validPeriodEnd: null });
  form.permissibleValues = list;
}

function removePermissibleValue(idx: number) {
  const dialogRef = dialog.value as any;
  if (!dialogRef) return;
  const form = dialogRef.form as Record<string, unknown>;
  const list = (form.permissibleValues || []) as any[];
  list.splice(idx, 1);
  form.permissibleValues = list;
}
</script>
