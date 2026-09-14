<template>
  <div>
    <h2 style="margin-top: 0;">{{ $t('namingConvention.title') }}</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="(params) => namingConventionApi.list(params as any)"
      @create="handleCreate"
      @edit="(r) => handleEdit(r as any)"
      @delete="(r) => handleDelete(r as any)"
    >
      <template #columns>
        <el-table-column :label="$t('common.identifier')" width="120">
          <template #default="{ row }">
            <code style="font-size: 12px;">{{ row.id?.slice(0, 8) || '-' }}</code>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.name')" min-width="180">
          <template #default="{ row }">
            {{ row.label || preferredDesignation(row) || '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('namingConvention.scopeRule')" min-width="180">
          <template #default="{ row }">
            <code v-if="row.scopeRule" style="font-size: 12px;">{{ truncate(row.scopeRule, 50) }}</code>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('namingConvention.authorityRule')" min-width="180">
          <template #default="{ row }">
            <code v-if="row.authorityRule" style="font-size: 12px;">{{ truncate(row.authorityRule, 50) }}</code>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.status')" width="140">
          <template #default="{ row }">
            <el-tag size="small">
              {{ (row.meta?.registrationStatus ? $t('registrationStatus.' + row.meta.registrationStatus) : '-') }}
            </el-tag>
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      :title="editingId ? $t('namingConvention.edit') : $t('namingConvention.create')"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <ItemRegistrationSection
          ref="regSection"
          v-model="form.registration"
          :is-editing="!!editingId"
          :registration-authority-name="editingRaName"
        />
        <el-divider content-position="left">{{ $t('namingConvention.scopeRule') }}</el-divider>
        <el-form-item :label="$t('namingConvention.scopeRule')" required>
          <template #label>
            <span>{{ $t('namingConvention.scopeRule') }}</span>
            <el-tooltip :content="$t('namingConvention.scopeRuleTip')" placement="top">
              <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <el-input
            v-model="form.scopeRule"
            :placeholder="$t('namingConvention.scopeRulePlaceholder')"
          />
        </el-form-item>
        <el-form-item>
          <template #label>
            <span>{{ $t('namingConvention.authorityRule') }}</span>
            <el-tooltip :content="$t('namingConvention.authorityRuleTip')" placement="top">
              <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <el-input
            v-model="form.authorityRule"
            :placeholder="$t('namingConvention.authorityRulePlaceholder')"
          />
        </el-form-item>
        <el-form-item>
          <DesignationDefinitionSection
            :designations="form.designations"
            :definitions="form.definitions"
            :show-pairings="false"
            :default-language-id="defaultLanguageId || undefined"
            @update:designations="form.designations = $event"
            @update:definitions="form.definitions = $event"
          />
        </el-form-item>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { QuestionFilled } from '@element-plus/icons-vue';
import { namingConventionApi, namespaceApi } from '@/api/resources';
import type {
  NamingConventionRequest,
  NamingConventionResponse,
  NamespaceResponse,
  TextLineForm,
} from '@/types/common';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';
import DesignationDefinitionSection from '@/components/DesignationDefinitionSection.vue';
import ItemRegistrationSection from '@/components/ItemRegistrationSection.vue';
import { useRegistryDefaultLanguage } from '@/composables/useRegistryDefaultLanguage';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();
const regSection = ref<InstanceType<typeof ItemRegistrationSection>>();
const { defaultLanguageId } = useRegistryDefaultLanguage();

const editingId = ref<string | null>(null);
const editingRaName = ref<string | null>(null);

function truncate(text: string, max: number): string {
  if (!text) return '';
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

function preferredDesignation(row: NamingConventionResponse): string {
  if (row.label) return row.label;
  const list = row.designations || [];
  if (list.length === 0) return '';
  const zh = list.find((d) => d.languageCode === 'zh-CN');
  return zh?.value || list[0]?.value || '';
}

function handleCreate() {
  editingId.value = null;
  editingRaName.value = null;
  dialog.value?.open({
    scopeRule: '',
    authorityRule: '',
    designations: [],
    definitions: [],
    registration: {},
  });
}

async function handleEdit(row: NamingConventionResponse) {
  try {
    const detail = await namingConventionApi.get(row.id);
    editingId.value = detail.id;
    editingRaName.value = (detail.meta as any)?.registrationAuthorityName || null;
    dialog.value?.open({
      scopeRule: detail.scopeRule || '',
      authorityRule: detail.authorityRule || '',
      designations: (detail.designations || []).map((d) => ({
        languageId: d.languageId,
        value: d.value,
        contexts: d.contexts,
      })) as TextLineForm[],
      definitions: (detail.definitions || []).map((d) => ({
        languageId: d.languageId,
        value: d.value,
        contexts: d.contexts,
      })) as TextLineForm[],
      registration: {
        namespaceId: (detail.meta as any)?.namespaceId || undefined,
        identifier: detail.meta?.identifier || undefined,
        version: (detail.meta as any)?.version || undefined,
      },
    });
  } catch {
    ElMessage.error('加载命名规范详情失败');
  }
}

async function submitFn(data: Record<string, unknown>) {
  if (!data.scopeRule || (data.scopeRule as string).trim() === '') {
    ElMessage.error('scopeRule 不能为空');
    throw new Error('scopeRule required');
  }
  const designations = (data.designations as TextLineForm[]) || [];
  if (designations.length === 0) {
    ElMessage.error('designations 不能为空');
    throw new Error('designations required');
  }
  const varError = regSection.value?.validateTemplateVariables() ?? null;
  if (varError) {
    ElMessage.error(varError);
    throw new Error(varError);
  }
  const payload: NamingConventionRequest = {
    scopeRule: (data.scopeRule as string).trim(),
    authorityRule: (data.authorityRule as string) || undefined,
    designations,
    definitions: (data.definitions as TextLineForm[]) || [],
    registration: regSection.value?.buildPayload() as any,
  };
  if (editingId.value) {
    return namingConventionApi.update(editingId.value, payload);
  }
  return namingConventionApi.create(payload);
}

async function handleDelete(row: NamingConventionResponse) {
  try {
    // Scan namespace list to compute reference count (max 200).
    let refCount = 0;
    let totalScanned = 0;
    try {
      const nsRes = await namespaceApi.list({ size: 200 });
      const all = (nsRes.content || []) as NamespaceResponse[];
      totalScanned = all.length;
      refCount = all.filter((n) =>
        (n.namingConventionIds || []).includes(row.id)
      ).length;
    } catch {
      refCount = 0;
    }
    const label = row.label || preferredDesignation(row) || row.id.slice(0, 8);
    let msg: string;
    if (refCount > 0) {
      msg = `命名规范「${label}」已被 ${refCount} 个命名空间引用，确认删除？`;
    } else if (totalScanned >= 200) {
      msg = `命名空间数量较多（>200），建议使用搜索确认关联后操作。确认仍要删除「${label}」？`;
    } else {
      msg = `确认删除命名规范「${label}」？此操作不可恢复。`;
    }
    await ElMessageBox.confirm(msg, '提示', { type: 'warning' });
    await namingConventionApi.delete(row.id);
    ElMessage.success('删除成功');
    tableRef.value?.fetchData();
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) {
      ElMessage.error('删除失败');
    }
  }
}
</script>
