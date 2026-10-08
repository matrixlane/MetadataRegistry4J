<template>
  <div>
    <h2 style="margin-top: 0;">{{ $t('namingConvention.title') }}</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="(params) => namingConventionApi.list(params as any)"
      :delete-fn="namingConventionApi.delete"
      @create="handleCreate"
      @edit="(r) => handleEdit(r as any)"
      @delete="(r) => handleDelete(r as any)"
    >
      <template #columns>
        <el-table-column :label="$t('common.name')" min-width="220">
          <template #default="{ row }">
            {{ row.label || truncate(row.scopeRule, 40) || '-' }}
            <code v-if="row.authorityRule" class="authority-hint" :title="row.authorityRule">{{ truncate(row.authorityRule, 40) }}</code>
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
        <el-table-column :label="$t('namingConvention.semanticRule')" min-width="180">
          <template #default="{ row }">
            <code v-if="row.semanticRule" style="font-size: 12px;">{{ truncate(row.semanticRule, 50) }}</code>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('namingConvention.syntacticRule')" min-width="180">
          <template #default="{ row }">
            <code v-if="row.syntacticRule" style="font-size: 12px;">{{ truncate(row.syntacticRule, 50) }}</code>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('namingConvention.lexicalRule')" min-width="180">
          <template #default="{ row }">
            <code v-if="row.lexicalRule" style="font-size: 12px;">{{ truncate(row.lexicalRule, 50) }}</code>
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
        <el-tabs v-model="activeTab" type="border-card">
          <!-- 注册标识 -->
          <el-tab-pane label="注册标识" name="registration">
            <ItemRegistrationSection
              ref="regSection"
              v-model="form.registration"
              :is-editing="!!editingId"
              :registration-authority-name="editingRaName"
            />
          </el-tab-pane>

          <!-- 指称（Naming_Convention 为 Item 子类，可被命名，0..* 非必填） -->
          <el-tab-pane label="指称" name="designations">
            <el-form-item label="指称">
              <MultilingualTextEditor
                v-model="form.designations"
                label="指称"
                :default-language-id="defaultLanguageId || undefined"
              />
            </el-form-item>
          </el-tab-pane>

          <!-- 定义（Item 子类可被定义，0..* 非必填） -->
          <el-tab-pane label="定义" name="definitions">
            <el-form-item label="定义">
              <MultilingualTextEditor
                v-model="form.definitions"
                label="定义"
                multiline
                :default-language-id="defaultLanguageId || undefined"
              />
            </el-form-item>
          </el-tab-pane>

          <!-- 属性 -->
          <el-tab-pane label="属性" name="attributes">
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
              <template #label>
                <span>{{ $t('namingConvention.semanticRule') }}</span>
                <el-tooltip :content="$t('namingConvention.semanticRuleTip')" placement="top">
                  <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input
                v-model="form.semanticRule"
                :placeholder="$t('namingConvention.semanticRulePlaceholder')"
              />
            </el-form-item>
            <el-form-item>
              <template #label>
                <span>{{ $t('namingConvention.syntacticRule') }}</span>
                <el-tooltip :content="$t('namingConvention.syntacticRuleTip')" placement="top">
                  <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input
                v-model="form.syntacticRule"
                :placeholder="$t('namingConvention.syntacticRulePlaceholder')"
              />
            </el-form-item>
            <el-form-item>
              <template #label>
                <span>{{ $t('namingConvention.lexicalRule') }}</span>
                <el-tooltip :content="$t('namingConvention.lexicalRuleTip')" placement="top">
                  <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input
                v-model="form.lexicalRule"
                :placeholder="$t('namingConvention.lexicalRulePlaceholder')"
              />
            </el-form-item>
          </el-tab-pane>
        </el-tabs>
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
import MultilingualTextEditor from '@/components/MultilingualTextEditor.vue';
import ItemRegistrationSection from '@/components/ItemRegistrationSection.vue';
import { useRegistryDefaultLanguage } from '@/composables/useRegistryDefaultLanguage';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();
const activeTab = ref('registration');
const regSection = ref<InstanceType<typeof ItemRegistrationSection>>();
const { defaultLanguageId } = useRegistryDefaultLanguage();

const editingId = ref<string | null>(null);
const editingRaName = ref<string | null>(null);

function truncate(text: string | undefined, max: number): string {
  if (!text) return '';
  return text.length > max ? `${text.slice(0, max)}…` : text;
}

function handleCreate() {
  editingId.value = null;
  editingRaName.value = null;
  dialog.value?.open({
    scopeRule: '',
    authorityRule: '',
    semanticRule: '',
    syntacticRule: '',
    lexicalRule: '',
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
      semanticRule: detail.semanticRule || '',
      syntacticRule: detail.syntacticRule || '',
      lexicalRule: detail.lexicalRule || '',
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
  const varError = regSection.value?.validateTemplateVariables() ?? null;
  if (varError) {
    ElMessage.error(varError);
    throw new Error(varError);
  }
  const optional = (v: unknown) => ((v as string) || '').trim() || undefined;
  const payload: NamingConventionRequest = {
    scopeRule: (data.scopeRule as string).trim(),
    authorityRule: optional(data.authorityRule),
    semanticRule: optional(data.semanticRule),
    syntacticRule: optional(data.syntacticRule),
    lexicalRule: optional(data.lexicalRule),
    designations: (data.designations as TextLineForm[]) || [],
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
    const label = row.label || truncate(row.scopeRule, 40) || '—';
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

<style scoped>
.authority-hint {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
</style>
