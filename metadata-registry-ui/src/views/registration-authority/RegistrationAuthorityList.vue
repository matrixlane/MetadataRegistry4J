<template>
  <div>
    <h2 style="margin-top: 0;">注册机构</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="registrationAuthorityApi.list"
      :delete-fn="registrationAuthorityApi.delete"
      :can-create="canCreate"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="organizationIdentifier" label="标识符" width="160" sortable />
        <el-table-column label="名称">
          <template #default="{ row }">
            {{ row.name || '-' }}
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="注册机构"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="标识符" prop="organizationIdentifier" required>
          <el-input v-model="form.organizationIdentifier" />
        </el-form-item>
        <el-form-item label="中文名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="英文名称">
          <el-input v-model="form.englishName" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { registrationAuthorityApi } from '@/api/resources';
import request from '@/utils/request';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();
const canCreate = ref(true);

// 新建入口按部署版本与治理策略门控：
//   个人版 — 永远隐藏（个人版只允许一个注册机构）；
//   团队版 + SINGLE 策略且已有注册机构 — 隐藏（与后端校验一致）。
onMounted(async () => {
  try {
    const status = await request.get<{ edition?: string | null; runningMode?: string }>(
      '/init-wizard/status'
    );
    if (status.edition === 'PERSONAL') {
      canCreate.value = false;
      return;
    }
    if ((status.runningMode ?? 'SINGLE') === 'SINGLE') {
      const page = await registrationAuthorityApi.list({ page: 1, size: 1 });
      if (page.totalElements >= 1) {
        canCreate.value = false;
      }
    }
  } catch {
    // 状态获取失败时保持默认（后端仍会做最终校验）
  }
});

function submitFn(data: Record<string, unknown>) {
  if (data.id) return registrationAuthorityApi.update(data.id as string, data);
  return registrationAuthorityApi.create(data);
}

async function handleDelete(row: Record<string, unknown>) {
  await registrationAuthorityApi.delete(row.id as string);
  tableRef.value?.fetchData();
}
</script>
