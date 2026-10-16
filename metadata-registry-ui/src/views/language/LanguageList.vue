<template>
  <div>
    <h2 style="margin-top: 0;">语言</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="languageApi.list"
      :delete-fn="languageApi.delete"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(toFormModel(r))"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="languageIdentifier" label="语言标识" width="140" sortable />
        <el-table-column prop="scriptIdentifier" label="文字标识" width="100" />
        <el-table-column prop="geopoliticalTerritoryIdentifier" label="地区标识" width="100" />
        <el-table-column prop="languageName" label="语言名称" width="120" />
        <el-table-column prop="geopoliticalTerritoryName" label="地区名称" width="120" />
        <el-table-column label="是否启用" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled !== false"
              :loading="togglingId === row.id"
              @change="(val: boolean | string | number) => handleToggleEnabled(row, val === true)"
            />
          </template>
        </el-table-column>
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
        <el-form-item label="文字标识">
          <el-input v-model="form.scriptIdentifier" />
        </el-form-item>
        <el-form-item label="地区标识">
          <el-input v-model="form.geopoliticalTerritoryIdentifier" />
        </el-form-item>
        <el-form-item label="语言名称">
          <el-input v-model="form.languageName" placeholder="留空则显示语言标识符" />
        </el-form-item>
        <el-form-item label="地区名称">
          <el-input v-model="form.territoryName" placeholder="留空则显示地区标识符" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { languageApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();
// 正在保存启用状态切换的行 id,用于开关 loading 防重复点击
const togglingId = ref<string | null>(null);

// 响应字段 geopoliticalTerritoryName 对应请求字段 territoryName,编辑时映射回填,避免误清空持久化值
function toFormModel(row: Record<string, unknown>) {
  return { ...row, territoryName: row.territoryName ?? row.geopoliticalTerritoryName };
}

function submitFn(data: Record<string, unknown>) {
  if (data.id) return languageApi.update(data.id as string, data);
  return languageApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  try {
    await languageApi.delete(row.id as string);
    tableRef.value?.fetchData();
  } catch {
    // 失败提示已由 axios 拦截器统一弹出；此处吞掉避免未处理拒绝冒泡到错误边界导致整页崩溃
  }
}

// 切换启用状态:开关用 model-value 单向绑定,仅在后端保存成功后刷新列表;失败时行数据不变,开关随 model-value 自动回滚
// 请求体沿用编辑表单的整行提交方式,并像 toFormModel 一样把响应字段 geopoliticalTerritoryName 映射回 territoryName,避免切换时误覆盖持久化名称
async function handleToggleEnabled(row: Record<string, unknown>, val: boolean) {
  togglingId.value = row.id as string;
  try {
    await languageApi.update(row.id as string, {
      ...row,
      territoryName: row.territoryName ?? row.geopoliticalTerritoryName,
      enabled: val
    });
    ElMessage.success(val ? '已启用该语言' : '已停用该语言');
    tableRef.value?.fetchData();
  } catch {
    // 失败提示由请求拦截器统一展示,这里不修改行数据,开关保持原状
  } finally {
    togglingId.value = null;
  }
}
</script>
