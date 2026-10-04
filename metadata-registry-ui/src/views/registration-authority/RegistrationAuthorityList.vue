<template>
  <div>
    <h2 style="margin-top: 0;">{{ $t('registrationAuthority.title') }}</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="registrationAuthorityApi.list"
      :can-create="canCreate"
      :can-delete="false"
      @create="dialog?.open()"
      @edit="(r) => dialog?.open(r)"
    >
      <template #columns>
        <el-table-column prop="organizationIdentifier" :label="$t('registrationAuthority.columns.identifier')" width="160" sortable />
        <el-table-column :label="$t('registrationAuthority.columns.name')">
          <template #default="{ row }">
            {{ row.name || '-' }}
          </template>
        </el-table-column>
      </template>
      <template #actions="{ row }">
        <el-tooltip
          :disabled="row.deletable !== false"
          :content="$t('registrationAuthority.primaryNotDeletable')"
          placement="top"
        >
          <span>
            <el-button
              size="small"
              type="danger"
              text
              data-testid="row-delete"
              :disabled="row.deletable === false"
              @click="handleDelete(row)"
            >
              {{ $t('common.delete') }}
            </el-button>
          </span>
        </el-tooltip>
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      :title="$t('registrationAuthority.title')"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-alert
          v-if="edition === 'PERSONAL'"
          type="info"
          :closable="false"
          :title="$t('registrationAuthority.createAutoRegistrarHint')"
          data-testid="create-registrar-hint"
          style="margin-bottom: 12px;"
        />
        <el-form-item :label="$t('registrationAuthority.form.identifier')" prop="organizationIdentifier" required>
          <el-input v-model="form.organizationIdentifier" />
        </el-form-item>
        <el-form-item :label="$t('registrationAuthority.form.name')">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="$t('registrationAuthority.form.englishName')">
          <el-input v-model="form.englishName" />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { ElMessage, ElMessageBox } from 'element-plus';
import { registrationAuthorityApi } from '@/api/resources';
import request from '@/utils/request';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';
import type { RegistrationAuthorityDeletionPreview } from '@/types/common';

const { t } = useI18n();

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();
const canCreate = ref(true);
const edition = ref<string | null>(null);

// 新建入口按部署版本与治理策略门控（acg-submission）：
//   个人版 — 恢复新增入口（个人版创建后自动成为该机构注册人员，不设数量限制）；
//   团队版 + SINGLE 策略且已有注册机构 — 隐藏（与后端校验一致）。
onMounted(async () => {
  try {
    const status = await request.get<{ edition?: string | null; runningMode?: string }>(
      '/init-wizard/status'
    );
    edition.value = status.edition ?? null;
    if (status.edition === 'PERSONAL') {
      canCreate.value = true;
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

/**
 * 删除（acg-submission）：先调 deletion-preview 端点取级联删除计数，
 * 确认对话框展示计数与不可恢复警告后才执行删除。
 * 个人版主要注册机构（primary）行由后端 DTO 的 deletable=false 标记，
 * 前端禁用删除入口并提示；其余行正常放行。
 */
async function handleDelete(row: Record<string, unknown>) {
  const id = row.id as string;
  let preview: RegistrationAuthorityDeletionPreview;
  try {
    preview = await registrationAuthorityApi.deletionPreview(id);
  } catch {
    // request interceptor 已展示错误
    return;
  }
  const lines = [
    `${t('registrationAuthority.deletePreview.administeredItems')}: ${preview.administeredItemCount}`,
    `${t('registrationAuthority.deletePreview.submissionRecords')}: ${preview.submissionRecordCount}`,
    `${t('registrationAuthority.deletePreview.registrars')}: ${preview.registrarCount}`,
    `${t('registrationAuthority.deletePreview.namespaces')}: ${preview.namespaceCount}`,
    `${t('registrationAuthority.deletePreview.stewardshipRecords')}: ${preview.stewardshipRecordCount}`,
    `${t('registrationAuthority.deletePreview.transitionRules')}: ${preview.transitionRuleCount}`,
  ].join('\n');
  try {
    await ElMessageBox.confirm(
      `${lines}\n${t('registrationAuthority.deleteConfirmMessage')}`,
      t('registrationAuthority.deleteConfirmTitle'),
      { type: 'warning' }
    );
  } catch {
    return;
  }
  try {
    await registrationAuthorityApi.delete(id);
    ElMessage.success(t('registrationAuthority.deleteSuccess'));
    tableRef.value?.fetchData();
  } catch {
    // request interceptor 已展示错误（如个人版 primary 行后端 422 拒绝）
  }
}
</script>
