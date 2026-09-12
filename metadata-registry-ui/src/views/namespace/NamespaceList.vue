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
            <el-form-item :label="$t('common.name') + ' / ' + $t('common.description')">
              <DesignationDefinitionSection
                :designations="form.designations"
                :definitions="form.definitions"
                :show-pairings="false"
                :default-language-id="defaultLanguageId || undefined"
                @update:designations="form.designations = $event"
                @update:definitions="form.definitions = $event"
              />
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
                <template #label="{ label, value }">
                  <span v-if="isPendingOption(value, organizationOptions, orgsLoaded)">…</span>
                  <span v-else>{{ label }}</span>
                </template>
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
                <template #label="{ label, value }">
                  <span v-if="isPendingOption(value, namingConventionOptions, namingConventionsLoaded)">…</span>
                  <span v-else>{{ label }}</span>
                </template>
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
            <!-- 命名空间性质：创建时确定，只读展示，无变更入口 -->
            <el-form-item v-if="editingId" :label="$t('namespace.kind')">
              <el-tag :type="formKind === 'OWN' ? 'success' : 'info'" effect="dark">
                {{ formKind === 'OWN' ? $t('namespace.kindOwn') : $t('namespace.kindExternal') }}
              </el-tag>
              <span style="margin-left: 8px; font-size: 12px; color: var(--el-text-color-secondary);">
                {{ $t('namespace.kindImmutableTip') }}
              </span>
            </el-form-item>
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

            <!-- 外部命名空间：仅手动录入说明 + 可选格式校验（含样例即时测试） -->
            <template v-if="editingId && formKind === 'EXTERNAL'">
              <el-alert
                :title="$t('namespace.externalManualHint')"
                type="info"
                :closable="false"
                show-icon
                style="margin-bottom: 12px;"
              />
              <el-form-item :label="$t('namespace.externalPatternTest')">
                <div style="display: flex; gap: 8px; width: 100%;">
                  <el-input v-model="patternTestSample" placeholder="DE.00001" clearable />
                  <el-button @click="testExternalPattern">{{ $t('common.test') }}</el-button>
                </div>
              </el-form-item>
              <el-alert
                v-if="patternTestResult"
                :title="patternTestResult"
                :type="patternTestOk ? 'success' : 'error'"
                :closable="false"
                show-icon
                style="margin-bottom: 12px;"
              />
            </template>

            <!-- 自有命名空间：段模板策略（设计器入口 + 版本信息 + 序号回填） -->
            <template v-else-if="editingId && formKind === 'OWN'">
              <el-divider content-position="left">{{ $t('namespace.groupSegmentStrategy') }}</el-divider>
              <el-form-item :label="$t('namespace.publishedTemplate')">
                <div v-if="strategyView?.published" style="width: 100%;">
                  <el-tag size="small" type="success">v{{ strategyView.published.version }} PUBLISHED</el-tag>
                  <code style="margin-left: 8px; font-size: 12px;">{{ summarizeTemplate(strategyView.published.configJson) }}</code>
                </div>
                <span v-else style="font-size: 12px; color: var(--el-text-color-secondary);">
                  {{ $t('namespace.noPublishedTemplate') }}
                </span>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" plain @click="openDesigner">
                  {{ $t('namespace.openDesigner') }}
                </el-button>
                <el-button v-if="strategyView?.published" @click="handleBackfill" :loading="backfilling">
                  {{ $t('namespace.backfill') }}
                </el-button>
                <el-popconfirm
                  v-if="strategyView?.published"
                  :title="$t('namespace.disableConfirm')"
                  @confirm="handleDisableStrategy"
                >
                  <template #reference>
                    <el-button type="danger" plain>{{ $t('namespace.disableStrategy') }}</el-button>
                  </template>
                </el-popconfirm>
              </el-form-item>
            </template>

            <!-- 新建命名空间：本界面创建的是外部命名空间，提示性质 -->
            <el-alert
              v-else-if="!editingId"
              :title="$t('namespace.createIsExternalHint')"
              type="info"
              :closable="false"
              show-icon
              style="margin-bottom: 12px;"
            />

            <!-- 旧版独立策略（UUID/URN/SEQUENCE）已收敛为段模板；仅编辑中且已有旧配置时显示 -->
            <template v-if="false">
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

    <!-- 段规则设计器（自有命名空间） -->
    <el-dialog
      v-model="designerVisible"
      :title="$t('namespace.designerTitle')"
      width="860px"
      destroy-on-close
    >
      <SegmentTemplateDesigner
        v-if="designerVisible && editingId"
        v-model="designerJson"
        :namespace-id="editingId"
      />
      <template #footer>
        <el-button @click="designerVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button :loading="strategySaving" @click="saveStrategyDraft">
          {{ $t('namespace.saveDraft') }}
        </el-button>
        <el-button type="primary" :loading="strategySaving" @click="publishStrategy">
          {{ $t('namespace.publishStrategy') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { QuestionFilled } from '@element-plus/icons-vue';
import { namespaceApi, organizationApi, namingConventionApi } from '@/api/resources';
import { identifierStrategyApi, type NamespaceStrategyView } from '@/api/identifierStrategy';
import type {
  NamespaceRequest,
  NamespaceResponse,
  NamespaceDetailResponse,
  TextLineForm,
} from '@/types/common';
import DataTablePage from '@/components/DataTablePage.vue';
import ResourceFormDialog from '@/components/ResourceFormDialog.vue';
import DesignationDefinitionSection from '@/components/DesignationDefinitionSection.vue';
import { useRegistryDefaultLanguage } from '@/composables/useRegistryDefaultLanguage';
import SegmentTemplateDesigner from '@/components/identifier/SegmentTemplateDesigner.vue';

const tableRef = ref<InstanceType<typeof DataTablePage>>();
const dialog = ref<InstanceType<typeof ResourceFormDialog>>();
const activeTab = ref('basic');

const { defaultLanguageId } = useRegistryDefaultLanguage();

const editingId = ref<string | null>(null);

const formKind = ref<'OWN' | 'EXTERNAL' | ''>('');
const strategyView = ref<NamespaceStrategyView | null>(null);
const designerVisible = ref(false);
const designerJson = ref('');
const strategySaving = ref(false);
const backfilling = ref(false);
const patternTestSample = ref('');
const patternTestResult = ref('');
const patternTestOk = ref(false);

const organizationOptions = ref<{ id: string; name: string; identifier?: string }[]>([]);
const namingConventionOptions = ref<{ id: string; label?: string; scopeRule?: string }[]>([]);
const orgsLoaded = ref(false);
const namingConventionsLoaded = ref(false);

/** 选项列表加载完成前，未命中的选中值显示占位而非原始 id */
function isPendingOption(value: unknown, options: { id: string }[], done: boolean): boolean {
  if (!value || typeof value !== 'string') return false;
  if (done) return false;
  return !options.some((o) => o.id === value);
}

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
  } finally {
    orgsLoaded.value = true;
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
  } finally {
    namingConventionsLoaded.value = true;
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
  formKind.value = '';
  strategyView.value = null;
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
    formKind.value = (detail as any).namespaceKind === 'OWN' ? 'OWN' : 'EXTERNAL';
    patternTestSample.value = '';
    patternTestResult.value = '';
    dialog.value?.open(buildFormFromDetail(detail));
    if (formKind.value === 'OWN') {
      await loadStrategyView();
    }
  } catch {
    ElMessage.error('加载命名空间详情失败');
  }
}

async function loadStrategyView() {
  if (!editingId.value) return;
  try {
    strategyView.value = await identifierStrategyApi.view(editingId.value);
  } catch {
    strategyView.value = null;
  }
}

function summarizeTemplate(configJson: string): string {
  try {
    const parsed = JSON.parse(configJson);
    return (parsed.segments || [])
      .map((s: any) => s.name || s.params?.value || s.params?.char || s.type)
      .join(' → ');
  } catch {
    return configJson;
  }
}

async function openDesigner() {
  if (!editingId.value) return;
  await loadStrategyView();
  designerJson.value = strategyView.value?.published?.configJson
    || strategyView.value?.drafts?.[0]?.configJson
    || '{"segments":[]}';
  designerVisible.value = true;
}

async function saveStrategyDraft() {
  if (!editingId.value) return;
  strategySaving.value = true;
  try {
    await identifierStrategyApi.saveDraft(editingId.value, designerJson.value);
    ElMessage.success('草稿已保存');
    await loadStrategyView();
  } catch (e: any) {
    ElMessage.error(e?.message || '保存草稿失败');
  } finally {
    strategySaving.value = false;
  }
}

async function publishStrategy() {
  if (!editingId.value) return;
  strategySaving.value = true;
  try {
    await identifierStrategyApi.publish(editingId.value, designerJson.value);
    ElMessage.success('策略已发布并立即生效（历史标识不受影响）');
    designerVisible.value = false;
    await loadStrategyView();
  } catch (e: any) {
    ElMessage.error(e?.message || '发布失败');
  } finally {
    strategySaving.value = false;
  }
}

async function handleDisableStrategy() {
  if (!editingId.value) return;
  try {
    await identifierStrategyApi.disable(editingId.value);
    ElMessage.success('策略已停用');
    await loadStrategyView();
  } catch (e: any) {
    ElMessage.error(e?.message || '停用失败');
  }
}

async function handleBackfill() {
  if (!editingId.value) return;
  backfilling.value = true;
  try {
    const res = await identifierStrategyApi.backfill(editingId.value);
    const scopes = Object.entries(res.scopeMaxValues)
      .map(([k, v]) => `${k}: ${v}`)
      .join('，');
    ElMessage.success(`序号回填完成：解析 ${res.parsed} 个、跳过 ${res.skipped} 个${scopes ? `；${scopes}` : ''}`);
  } catch (e: any) {
    ElMessage.error(e?.message || '回填失败');
  } finally {
    backfilling.value = false;
  }
}

function testExternalPattern() {
  patternTestResult.value = '';
  const dialogForm = (dialog.value as any)?.form || {};
  const pattern = (dialogForm.manualIdentifierPattern as string) || '';
  if (!pattern) {
    patternTestOk.value = false;
    patternTestResult.value = '请先配置校验正则';
    return;
  }
  try {
    const re = new RegExp(`^(?:${pattern})$`);
    patternTestOk.value = re.test(patternTestSample.value);
    patternTestResult.value = patternTestOk.value ? '样例符合校验规则' : '样例不符合校验规则';
  } catch {
    patternTestOk.value = false;
    patternTestResult.value = '正则本身无法编译（保存时将跳过该校验）';
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
