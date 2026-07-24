<template>
  <div>
    <h2 style="margin-top: 0;">用户管理</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="appUserApi.list"
      :show-action="false"
      @create="dialog?.open({ enabled: true })"
      @edit="(r) => dialog?.open(r)"
      @delete="handleDelete"
    >
      <template #columns>
        <el-table-column prop="username" label="用户名" min-width="150" sortable />
        <el-table-column prop="sysRole" label="系统角色" min-width="150">
          <template #default="{ row }">
            <el-tag size="small" :type="row.sysRole === 'SUPER_ADMIN' ? 'danger' : 'info'">
              {{ row.sysRole === 'SUPER_ADMIN' ? '超级管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="启用状态" min-width="150">
          <template #default="{ row }">
            <el-tag size="small" :type="row.enabled ? 'success' : 'warning'">
              {{ row.enabled ? '已启用' : '待审核' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180">
          <template #default="{ row }">
            {{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="上次登录时间" min-width="180">
          <template #default="{ row }">
            {{ row.lastLoginAt ? new Date(row.lastLoginAt).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="260">
          <template #default="{ row }">
            <el-button
              v-if="!row.enabled"
              type="success"
              link
              :disabled="row.id === auth.user?.id"
              @click="handleAudit(row, true)"
            >
              审核通过
            </el-button>
            <el-button
              v-if="row.enabled"
              type="danger"
              link
              :disabled="row.id === auth.user?.id"
              @click="handleAudit(row, false)"
            >
              禁用
            </el-button>
            <el-button type="primary" link @click="dialog?.open(row)">
              编辑
            </el-button>
            <el-button
              v-if="row.sysRole !== 'SUPER_ADMIN'"
              type="warning"
              link
              @click="handleResetPassword(row)"
            >
              重置密码
            </el-button>
            <el-button
              type="danger"
              link
              :disabled="row.id === auth.user?.id"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      title="用户"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-form-item label="用户名" prop="username" required v-if="!form.id">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="系统角色">
          <el-select v-model="form.sysRole" placeholder="普通用户" clearable style="width: 100%;" :disabled="form.username === 'admin'">
            <el-option label="超级管理员" value="SUPER_ADMIN" disabled />
            <el-option label="普通用户" :value="null" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item :label="form.id ? '新密码' : '密码'" :required="!form.id">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { appUserApi } from '@/api/resources';
import { useAuthStore } from '@/stores/auth';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';

const auth = useAuthStore();

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();

function submitFn(data: Record<string, unknown>) {
  if (data.id) {
    return appUserApi.update(data.id as string, {
      sysRole: data.sysRole || null,
      enabled: data.enabled !== false,
      newPassword: data.password || undefined
    });
  }
  return appUserApi.create({
    username: data.username,
    password: data.password,
    sysRole: data.sysRole || null,
    enabled: data.enabled !== false
  });
}

async function handleAudit(row: Record<string, unknown>, enabled: boolean) {
  try {
    await appUserApi.audit(row.id as string, enabled);
    ElMessage.success(enabled ? '已审核通过该用户并启用账户' : '已禁用该用户账户');
    tableRef.value?.fetchData();
  } catch (err) {
    // Error details are displayed by interceptor
  }
}

async function handleDelete(row: Record<string, unknown>) {
  await appUserApi.delete(row.id as string);
  tableRef.value?.fetchData();
}

async function handleResetPassword(row: Record<string, unknown>) {
  try {
    await ElMessageBox.confirm(
      `确定要重置用户 "${row.username}" 的密码吗？重置后将生成一个临时密码，且该用户在首次登录时会被强制要求修改密码。`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    );
    const tempPassword = await appUserApi.resetPassword(row.id as string);
    await ElMessageBox.alert(
      `<div style="font-size: 14px; line-height: 1.8;">
        密码重置成功！该用户的临时密码为：<br/>
        <strong style="font-size: 18px; color: var(--el-color-primary); background: var(--el-fill-color-light); padding: 4px 8px; border-radius: 4px; display: inline-block; margin: 8px 0; font-family: monospace; user-select: all;">${tempPassword}</strong><br/>
        请复制并妥善保管此密码。<strong>此密码仅能成功登录一次，且登录后必须立即修改密码。</strong>
       </div>`,
      '重置成功',
      {
        dangerouslyUseHTMLString: true,
        confirmButtonText: '复制并关闭',
        callback: action => {
          if (action === 'confirm') {
            navigator.clipboard.writeText(tempPassword);
            ElMessage.success('密码已复制到剪贴板');
          }
        }
      }
    );
  } catch (err) {
    if (err !== 'cancel') {
      // Handled by request interceptor
    }
  }
}
</script>
