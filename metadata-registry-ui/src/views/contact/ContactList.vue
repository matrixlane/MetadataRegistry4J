<template>
  <div>
    <h2 style="margin-top: 0;">联系人</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="contactApi.list"
      :delete-fn="contactApi.delete"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="individualName" label="个人" min-width="160" />
        <el-table-column prop="roleName" label="角色" min-width="140" />
        <el-table-column prop="organizationName" label="组织" min-width="180" />
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="联系人"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="组织">
          <SmartItemPicker
            v-model="form.organizationId"
            :item-types="['Organization']"
            :scope="'all'"
            :show-advanced-filter="false"
            placeholder="搜索组织（输入名称或标识符）"
          />
        </el-form-item>
        <el-form-item label="个人">
          <SmartItemPicker
            v-model="form.individualId"
            :item-types="['Individual']"
            :scope="'all'"
            :show-advanced-filter="false"
            placeholder="搜索个人（输入名称或标识符）"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="form.roleId"
            clearable
            filterable
            placeholder="选择角色"
            style="width: 100%;"
          >
            <template #label="{ label, value }">
              <span v-if="isPendingOption(value, roleOptions, rolesLoaded)">…</span>
              <span v-else-if="isUnresolvedOption(value, roleOptions)">未解析项目</span>
              <span v-else>{{ label }}</span>
            </template>
            <el-option
              v-for="opt in roleOptions"
              :key="opt.id"
              :label="opt.roleName"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { contactApi, roleApi } from '@/api/resources';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';
import SmartItemPicker from '@/components/SmartItemPicker.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

const roleOptions = ref<{ id: string; roleName: string }[]>([]);
const rolesLoaded = ref(false);

/** 选项加载完成前，未命中的选中值显示占位而非原始 id */
function isPendingOption(value: unknown, options: { id: string }[], done: boolean): boolean {
  if (!value || typeof value !== 'string') return false;
  if (done) return false;
  return !options.some((o) => o.id === value);
}

/** 加载完成后仍不在选项中（如角色已删除）：永不回显原始 id */
function isUnresolvedOption(value: unknown, options: { id: string }[]): boolean {
  if (!value || typeof value !== 'string') return false;
  return !options.some((o) => o.id === value);
}

onMounted(async () => {
  try {
    roleOptions.value = await roleApi.options();
  } catch {
    roleOptions.value = [];
  } finally {
    rolesLoaded.value = true;
  }
});

function submitFn(data: Record<string, unknown>) {
  if (data.id) return contactApi.update(data.id as string, data);
  return contactApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  try {
    await contactApi.delete(row.id as string);
    tableRef.value?.fetchData();
  } catch {
    // 失败提示已由 axios 拦截器统一弹出；此处吞掉避免未处理拒绝冒泡到错误边界导致整页崩溃
  }
}
</script>
