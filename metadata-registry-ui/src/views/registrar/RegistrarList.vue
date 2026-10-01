<template>
  <div>
    <h2 style="margin-top: 0;">注册人员</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="registrarApi.list"
      :delete-fn="registrarApi.delete"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="individualName" label="姓名" min-width="160" />
        <el-table-column prop="roleName" label="角色" min-width="140" />
        <el-table-column prop="organizationName" label="组织" min-width="180" />
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="注册人员"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="姓名" prop="individualName">
          <el-input v-model="form.individualName" placeholder="如：李雷" clearable />
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="form.roleId" clearable filterable placeholder="选择角色" style="width: 100%;">
            <el-option
              v-for="opt in roleOptions"
              :key="opt.id"
              :label="opt.roleName"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="组织" prop="organizationId" required>
          <el-select v-model="form.organizationId" filterable placeholder="选择组织" style="width: 100%;">
            <el-option
              v-for="opt in organizationOptions"
              :key="opt.id"
              :label="opt.name || '—'"
              :value="opt.id"
            />
          </el-select>
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { registrarApi, roleApi, organizationApi } from '@/api/resources';
import { withSilentErrors } from '@/utils/request';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

interface RoleOption { id: string; roleName: string }
interface OrganizationOption { id: string; name: string | null }

const roleOptions = ref<RoleOption[]>([]);
const organizationOptions = ref<OrganizationOption[]>([]);

async function loadOptions() {
  // add-registrar-three-elements: 静默加载选项；后端 /organizations/options 端点缺失时
  // 不应阻塞列表渲染与编辑保存（fallback：编辑时 el-select 没 option 但 v-model 用 row 原值即可）。
  try {
    roleOptions.value = await withSilentErrors(async () => (await roleApi.options?.()) ?? []);
  } catch {
    roleOptions.value = [];
  }
  try {
    organizationOptions.value = await withSilentErrors(async () => (await organizationApi.options?.()) ?? []);
  } catch {
    organizationOptions.value = [];
  }
}

onMounted(loadOptions);

// 编辑回填：dialog 打开后若有 row 选项回灌当前角色/组织的 id 进入表单
watch(() => dialog.value, () => {
  // ResourceFormDialog 内置 row → form 赋值；选项列表预加载即可。
});

function submitFn(data: Record<string, unknown>) {
  const payload = {
    registrationAuthorityId: data.registrationAuthorityId ?? null,
    userId: data.userId ?? null,
    organizationId: data.organizationId,
    individualId: data.individualId ?? null,
    // 个体姓名由后端在 Individual 三种情形下处理（add-registrar-three-elements）：
    //   1) individualId 非空 → 改现有 Individual.name
    //   2) individualId 为空 + individualName 非空 → 新建 Individual 并关联
    //   3) 全空 → 保留现有关联
    individualName: (data.individualName as string | undefined)?.trim() || null,
    roleId: data.roleId ?? null,
  };
  if (data.id) return registrarApi.update(data.id as string, payload);
  return registrarApi.create(payload);
}

async function handleDelete(row: Record<string, unknown>) {
  await registrarApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
