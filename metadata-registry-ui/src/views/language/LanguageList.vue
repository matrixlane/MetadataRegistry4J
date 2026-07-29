<template>
  <div>
    <h2 style="margin-top: 0;">语言</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="languageApi.list"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="languageIdentifier" label="语言标识" width="140" sortable />
        <el-table-column prop="scriptIdentifier" label="文字" width="100" />
        <el-table-column prop="geopoliticalTerritoryIdentifier" label="地区" width="100" />
        <el-table-column prop="languageName" label="语言名称" width="120" />
        <el-table-column prop="geopoliticalTerritoryName" label="地区名称" width="120" />
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="语言"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="语言标识" required>
          <el-input v-model="form.languageIdentifier" />
        </el-form-item>
        <el-form-item label="文字">
          <el-input v-model="form.scriptIdentifier" />
        </el-form-item>
        <el-form-item label="地区">
          <el-input v-model="form.geopoliticalTerritoryIdentifier" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { languageApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) return languageApi.update(data.id as string, data);
  return languageApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await languageApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
