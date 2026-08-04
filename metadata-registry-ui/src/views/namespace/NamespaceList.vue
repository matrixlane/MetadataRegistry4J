<template>
  <div>
    <h2 style="margin-top: 0;">{{ $t('namespace.title') }}</h2>
    <DataTablePage
      ref="tableRef"
      :fetch-fn="(params) => namespaceApi.list(params as any)"
      @create="handleCreate"
      @edit="(r) => handleEdit(r as any)"
      @delete="(r) => handleDelete(r as any)"
    >
      <template #columns>
        <el-table-column :label="$t('common.name')" min-width="200">
          <template #default="{ row }">
            {{ row.label || preferredDesignation(row) || '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('namespace.shorthandPrefix')" width="160">
          <template #default="{ row }">
            <code v-if="row.shorthandPrefix" style="font-size: 12px;">{{ row.shorthandPrefix }}</code>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.status')" width="160">
          <template #default="{ row }">
            <div style="display: flex; gap: 4px; flex-wrap: wrap;">
              <el-tag
                size="small"
                :type="row.mandatoryNamingConventionIndicator ? 'primary' : 'info'"
                :effect="row.mandatoryNamingConventionIndicator ? 'dark' : 'plain'"
              >
                {{ $t('namespace.indicatorMandatory') }}
              </el-tag>
              <el-tag
                size="small"
                :type="row.oneNamePerItemIndicator ? 'primary' : 'info'"
                :effect="row.oneNamePerItemIndicator ? 'dark' : 'plain'"
              >
                {{ $t('namespace.indicatorOneName') }}
              </el-tag>
              <el-tag
                size="small"
                :type="row.oneItemPerNameIndicator ? 'primary' : 'info'"
                :effect="row.oneItemPerNameIndicator ? 'dark' : 'plain'"
              >
                {{ $t('namespace.indicatorOneItem') }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
      </template>
    </DataTablePage>

    <ResourceFormDialog
      ref="dialog"
      :title="editingId ? $t('namespace.edit') : $t('namespace.create')"
      width="720px"
      :submit-fn="submitFn"
      @success="tableRef?.fetchData()"
    >
      <template #fields="{ form }">
        <el-tabs v-model="activeTab" type="border-card">
          <!-- 基础信息 -->
          <el-tab-pane :label="$t('namespace.sectionBasic')" name="basic">
            <el-form-item :label="$t('common.name')" required>
              <DesignationEditor v-model="form.designations" />
            </el-form-item>
            <el-form-item :label="$t('common.description')">
              <DefinitionEditor v-model="form.definitions" />
            </el-form-item>
          </el-tab-pane>

          <!-- ISO 治理属性 -->
          <el-tab-pane :label="$t('namespace.sectionGovernance')" name="governance">
            <el-form-item :label="$t('namespace.shorthandPrefix')">
              <template #label>
                <span>{{ $t('namespace.shorthandPrefix') }}</span>
                <el-tooltip :content="$t('namespace.shorthandPrefixTip')" placement="top">
                  <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input v-model="form.shorthandPrefix" :placeholder="`sys:`" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <span>{{ $t('namespace.schemeReference') }}</span>
                <el-tooltip :content="$t('namespace.schemeReferenceTip')" placement="top">
                  <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input v-model="form.schemeReference" placeholder="urn:metadata-registry:my-namespace" />
            </el-form-item>
            <el-divider style="margin: 12px 0;" />
            <el-form-item>
              <template #label>
                <span>{{ $t('namespace.mandatoryNamingConventionIndicator') }}</span>
                <el-tooltip :content="$t('namespace.mandatoryNamingConventionIndicatorTip')" placement="top">
                  <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-switch
                v-model="form.mandatoryNamingConventionIndicator"
                @change="onMandatoryNCChange"
              />
            </el-form-item>
            <el-alert
              v-if="form.mandatoryNamingConventionIndicator && (!form.namingConventionIds || form.namingConventionIds.length === 0)"
              :title="$t('namespace.mandatoryNCWarn')"
              type="warning"
              :closable="false"
              show-icon
              style="margin-bottom: 12px;"
            />
            <el-form-item>
              <template #label>
                <span>{{ $t('namespace.oneNamePerItemIndicator') }}</span>
                <el-tooltip :content="$t('namespace.oneNamePerItemIndicatorTip')" placement="top">
                  <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-switch v-model="form.oneNamePerItemIndicator" />
            </el-form-item>
            <el-form-item>
              <template #label>
                <span>{{ $t('namespace.oneItemPerNameIndicator') }}</span>
                <el-tooltip :content="$t('namespace.oneItemPerNameIndicatorTip')" placement="top">
                  <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-switch v-model="form.oneItemPerNameIndicator" />
            </el-form-item>
          </el-tab-pane>

          <!-- 关联引用 -->
          <el-tab-pane :label="$t('namespace.sectionReferences')" name="references">
            <el-form-item :label="$t('namespace.namingAuthority')">
              <el-select
                v-model="form.namingAuthorityId"
                :placeholder="$t('namespace.namingAuthorityPlaceholder')"
                clearable
                filterable
                style="width: 100%;"
              >
                <el-option
                  v-for="org in organizationOptions"
                  :key="org.id"
                  :label="`${org.name} (${org.identifier || ''})`"
                  :value="org.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('namespace.namingConventions')">
              <el-select
                v-model="form.namingConventionIds"
                :placeholder="$t('namespace.namingConventionsPlaceholder')"
                multiple
                collapse-tags
                collapse-tags-tooltip
                filterable
                style="width: 100%;"
              >
                <el-option
                  v-for="nc in namingConventionOptions"
                  :key="nc.id"
                  :label="nc.label || nc.scopeRule || nc.id.slice(0, 8)"
                  :value="nc.id"
                />
              </el-select>
            </el-form-item>
          </el-tab-pane>

          <!-- 标识生成策略 -->
          <el-tab-pane :label="$t('namespace.sectionIdentifierPolicy')" name="identifierPolicy">
            <el-form-item>
              <template #label>
                <span>{{ $t('namespace.inputMode') }}</span>
                <el-tooltip :content="$t('namespace.identifierInputModeTip')" placement="top">
                  <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-radio-group v-model="form.identifierInputMode">
                <el-radio-button value="AUTO_ONLY">{{ $t('namespace.modeAutoOnly') }}</el-radio-button>
                <el-radio-button value="AUTO_WITH_OVERRIDE">{{ $t('namespace.modeAutoWithOverride') }}</el-radio-button>
                <el-radio-button value="MANUAL_ONLY">{{ $t('namespace.modeManualOnly') }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-alert
              v-if="form.identifierInputMode === 'MANUAL_ONLY'"
              :title="$t('namespace.modeManualOnlyHint')"
              type="info"
              :closable="false"
              show-icon
              style="margin-bottom: 12px;"
            />
            <el-form-item>
              <template #label>
                <span>{{ $t('namespace.manualIdentifierPattern') }}</span>
                <el-tooltip :content="$t('namespace.manualIdentifierPatternTip')" placement="top">
                  <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input
                v-model="form.manualIdentifierPattern"
                placeholder="^[A-Z0-9_-]+$"
                :disabled="form.identifierInputMode === 'AUTO_ONLY'"
                clearable
              />
            </el-form-item>

            <el-divider content-position="left">{{ $t('namespace.groupStrategy') }}</el-divider>
            <el-form-item>
              <template #label>
                <span>{{ $t('namespace.identifierStrategy') }}</span>
                <el-tooltip :content="$t('namespace.identifierStrategyTip')" placement="top">
                  <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-radio-group
                v-model="form.identifierStrategy"
                :disabled="form.identifierInputMode === 'MANUAL_ONLY'"
              >
                <el-radio-button value="UUID">{{ $t('namespace.strategyUuid') }}</el-radio-button>
                <el-radio-button value="SEQUENCE">{{ $t('namespace.strategySequence') }}</el-radio-button>
                <el-radio-button value="TEMPLATE">{{ $t('namespace.strategyTemplateType') }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <template v-if="form.identifierStrategy === 'SEQUENCE'">
              <el-form-item :label="$t('namespace.strategyPrefix')">
                <el-input
                  v-model="form.strategyPrefix"
                  placeholder="DE- 或 {ns}. 或 {type}-"
                  clearable
                />
              </el-form-item>
              <el-form-item :label="$t('namespace.strategySeqStart')">
                <el-input-number v-model="form.strategySeqStart" :min="0" style="width: 180px;" />
              </el-form-item>
              <el-form-item :label="$t('namespace.strategySeqPad')">
                <el-input-number v-model="form.strategySeqPad" :min="1" :max="18" style="width: 180px;" />
              </el-form-item>
            </template>
            <template v-if="form.identifierStrategy === 'TEMPLATE'">
              <el-form-item>
                <template #label>
                  <span>{{ $t('namespace.strategyTemplate') }}</span>
                  <el-tooltip :content="$t('namespace.strategyTemplateTip')" placement="top">
                    <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </template>
                <el-input
                  v-model="form.strategyTemplate"
                  placeholder="{ns}.{type}.{seq:4}"
                  clearable
                />
              </el-form-item>
              <el-form-item :label="$t('namespace.strategySeqPad')">
                <el-input-number v-model="form.strategySeqPad" :min="1" :max="18" style="width: 180px;" />
              </el-form-item>
            </template>
          </el-tab-pane>

          <!-- 版本生成策略 -->
          <el-tab-pane :label="$t('namespace.sectionVersionPolicy')" name="versionPolicy">
            <el-form-item>
              <template #label>
                <span>{{ $t('namespace.inputMode') }}</span>
                <el-tooltip :content="$t('namespace.versionInputModeTip')" placement="top">
                  <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-radio-group v-model="form.versionInputMode">
                <el-radio-button value="AUTO_ONLY">{{ $t('namespace.modeAutoOnly') }}</el-radio-button>
                <el-radio-button value="AUTO_WITH_OVERRIDE">{{ $t('namespace.modeAutoWithOverride') }}</el-radio-button>
                <el-radio-button value="MANUAL_ONLY">{{ $t('namespace.modeManualOnly') }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-alert
              v-if="form.versionInputMode === 'MANUAL_ONLY'"
              :title="$t('namespace.versionManualOnlyHint')"
              type="info"
              :closable="false"
              show-icon
              style="margin-bottom: 12px;"
            />
            <el-form-item>
              <template #label>
                <span>{{ $t('namespace.versioningPolicy') }}</span>
                <el-tooltip :content="$t('namespace.versioningPolicyTip')" placement="top">
                  <el-icon style="margin-left: 4px;"><QuestionFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-select
                v-model="form.versioningPolicy"
                style="width: 100%;"
                :disabled="form.versionInputMode === 'MANUAL_ONLY'"
              >
                <el-option label="语义化版本 (X.Y.Z)" value="SEMVER" />
                <el-option label="顺序整数 (1, 2, 3, ...)" value="SEQUENTIAL" />
                <el-option label="日期版本 (YYYY-MM-DD)" value="DATE" />
              </el-select>
            </el-form-item>
          </el-tab-pane>
        </el-tabs>
      </template>
    </ResourceFormDialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { QuestionFilled } from '@element-plus/icons-vue';
import { namespaceApi, organizationApi, namingConventionApi } from '@/api/resources';
import type {
  NamespaceRequest,
  NamespaceResponse,
  NamespaceDetailResponse,
  TextLineForm,
} from '@/types/common';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';
import DesignationEditor from '@/components/DesignationEditor.vue';
import DefinitionEditor from '@/components/DefinitionEditor.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();
const activeTab = ref('basic');

const editingId = ref<string | null>(null);

const organizationOptions = ref<{ id: string; name: string; identifier?: string }[]>([]);
const namingConventionOptions = ref<{ id: string; label?: string; scopeRule?: string }[]>([]);

onMounted(async () => {
  try {
    const orgRes = await organizationApi.list({ size: 200 });
    organizationOptions.value = (orgRes.content || []).map((o: any) => ({
      id: o.id,
      name: o.name || o.englishName || o.identifier || o.id,
      identifier: o.identifier,
    }));
  } catch {
    organizationOptions.value = [];
  }
  try {
    const ncRes = await namingConventionApi.list({ size: 200 });
    namingConventionOptions.value = (ncRes.content || []).map((n) => ({
      id: n.id,
      label: n.label,
      scopeRule: n.scopeRule,
    }));
  } catch {
    namingConventionOptions.value = [];
  }
});

function preferredDesignation(row: NamespaceResponse | NamespaceDetailResponse): string {
  if (row.label) return row.label;
  const list = row.designations || [];
  if (list.length === 0) return '';
  const zh = list.find((d) => d.languageCode === 'zh-CN');
  return zh?.value || list[0]?.value || '';
}

function onMandatoryNCChange(val: boolean | string | number) {
  if (val) {
    ElMessage.warning('已开启强制命名规范，请至少绑定一个命名规范');
  }
}

function handleCreate() {
  editingId.value = null;
  activeTab.value = 'basic';
  dialog.value?.open({
    identifierInputMode: 'AUTO_WITH_OVERRIDE',
    versionInputMode: 'AUTO_WITH_OVERRIDE',
    versioningPolicy: 'SEMVER',
    manualIdentifierPattern: '',
    identifierStrategy: 'UUID',
    strategyPrefix: '',
    strategyTemplate: '',
    strategySeqStart: 1,
    strategySeqPad: 4,
  });
}

async function handleEdit(row: NamespaceResponse) {
  activeTab.value = 'basic';
  try {
    const detail = await namespaceApi.detail(row.id);
    editingId.value = detail.id;
    dialog.value?.open(buildFormFromDetail(detail));
  } catch {
    ElMessage.error('加载命名空间详情失败');
  }
}

function buildFormFromDetail(detail: NamespaceDetailResponse): Record<string, unknown> {
  return {
    namingAuthorityId: detail.namingAuthorityId,
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
    shorthandPrefix: detail.shorthandPrefix,
    schemeReference: detail.schemeReference,
    mandatoryNamingConventionIndicator: !!detail.mandatoryNamingConventionIndicator,
    namingConventionIds: detail.namingConventionIds || [],
    oneNamePerItemIndicator: !!detail.oneNamePerItemIndicator,
    oneItemPerNameIndicator: !!detail.oneItemPerNameIndicator,
    identifierInputMode: detail.identifierInputMode || 'AUTO_WITH_OVERRIDE',
    versionInputMode: detail.versionInputMode || 'AUTO_WITH_OVERRIDE',
    versioningPolicy: detail.versioningPolicy || 'SEMVER',
    manualIdentifierPattern: detail.manualIdentifierPattern || '',
    identifierStrategy: detail.identifierStrategy || 'UUID',
    strategyPrefix: detail.strategyPrefix || '',
    strategyTemplate: detail.strategyTemplate || '',
    strategySeqStart: detail.strategySeqStart ?? 1,
    strategySeqPad: detail.strategySeqPad ?? 4,
  };
}

async function submitFn(data: Record<string, unknown>) {
  const payload: NamespaceRequest = buildPayload(data);
  if (editingId.value) {
    return namespaceApi.update(editingId.value, payload);
  }
  return namespaceApi.create(payload);
}

function buildPayload(data: Record<string, unknown>): NamespaceRequest {
  const designations = (data.designations as TextLineForm[]) || [];
  if (!designations || designations.length === 0) {
    ElMessage.error('请至少添加一条指称');
    throw new Error('designations 不能为空');
  }
  if (
    data.mandatoryNamingConventionIndicator &&
    (!data.namingConventionIds || (data.namingConventionIds as string[]).length === 0)
  ) {
    ElMessage.error('已开启强制命名规范，必须至少绑定一个命名规范');
    throw new Error('mandatory NC requires namingConventionIds');
  }
  return {
    namingAuthorityId: (data.namingAuthorityId as string) || undefined,
    designations,
    definitions: (data.definitions as TextLineForm[]) || [],
    shorthandPrefix: (data.shorthandPrefix as string) || undefined,
    schemeReference: (data.schemeReference as string) || undefined,
    mandatoryNamingConventionIndicator: !!data.mandatoryNamingConventionIndicator,
    namingConventionIds: (data.namingConventionIds as string[]) || [],
    oneNamePerItemIndicator: !!data.oneNamePerItemIndicator,
    oneItemPerNameIndicator: !!data.oneItemPerNameIndicator,
    identifierInputMode: (data.identifierInputMode as string) || undefined,
    versionInputMode: (data.versionInputMode as string) || undefined,
    versioningPolicy: (data.versioningPolicy as string) || undefined,
    manualIdentifierPattern: (data.manualIdentifierPattern as string) || undefined,
    identifierStrategy: (data.identifierStrategy as string) || undefined,
    strategyPrefix: (data.strategyPrefix as string) || undefined,
    strategyTemplate: (data.strategyTemplate as string) || undefined,
    strategySeqStart: (data.strategySeqStart as number) ?? undefined,
    strategySeqPad: (data.strategySeqPad as number) ?? undefined,
  };
}

async function handleDelete(row: NamespaceResponse) {
  try {
    const check = await namespaceApi.deletionCheck(row.id);
    const label = row.label || preferredDesignation(row) || row.id.slice(0, 8);
    const msg = check.hasIdentifiers
      ? `命名空间「${label}」下还有 ${check.count} 个标识符 / 子命名空间，确认一并删除？`
      : `确认删除命名空间「${label}」？`;
    await ElMessageBox.confirm(msg, '提示', { type: 'warning' });
    await namespaceApi.delete(row.id);
    ElMessage.success('删除成功');
    tableRef.value?.fetchData();
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) {
      ElMessage.error('删除失败');
    }
  }
}
</script>
