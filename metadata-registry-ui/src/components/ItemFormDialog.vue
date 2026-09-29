<template>
  <el-dialog
    v-model="visible"
    :title="resolvedTitle"
    width="760px"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="reset"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="auto"
      @validate="handleFieldValidate"
    >
      <el-tabs v-model="activeTab" type="border-card">
        <!-- Registration Tab -->
        <el-tab-pane v-if="showRegistration" name="registration">
          <template #label>
            <span class="tab-label-container">
            <span>注册标识</span>
            <el-icon v-if="hasTabError('registration')" class="tab-error-icon"><Warning /></el-icon>
          </span>
          </template>
          <ItemRegistrationSection
            ref="registrationSectionRef"
            v-model="form.registration"
            :is-editing="isEditing"
            :registration-authority-name="editingRaName"
            :identifier-creation-mode="editingIdentifierCreationMode"
            :version-creation-mode="editingVersionCreationMode"
            @mode-change="handleRegistrationModeChange"
          />
        </el-tab-pane>

        <!-- 指称与定义（一级 Tab）：指称 / 定义 / 指称-定义配对 三个二级 Tab -->
        <el-tab-pane name="designationDefinition">
          <template #label>
            <span class="tab-label-container">
              <span>指称与定义</span>
              <el-icon v-if="hasNamingTabError()" class="tab-error-icon"><Warning /></el-icon>
            </span>
          </template>
          <el-tabs v-model="activeSubTab" class="naming-sub-tabs">
            <!-- Designations Sub-Tab -->
            <el-tab-pane name="designations">
              <template #label>
                <span class="tab-label-container">
                  <span>指称</span>
                  <el-icon v-if="hasTabError('designations')" class="tab-error-icon"><Warning /></el-icon>
                </span>
              </template>
              <el-form-item label="指称" prop="designations">
                <MultilingualTextEditor
                  ref="designationEditorRef"
                  v-model="form.designations"
                  label="指称"
                  :naming-convention-options="designationNamingConventionOptions"
                  :require-naming-conventions="namespaceMandatoryNC"
                  :pairings="form.pairings"
                  :default-language-id="defaultLanguageId || undefined"
                  :self-context-option="selfContextOption"
                  :ai-generate="canAiGenerate"
                  @conflicts="designationConflicts = $event"
                  @ai-generate="(i) => onAiGenerate('designation', i)"
                  @ai-generate-hover="(h, i) => onAiGenerateHover('designation', i, h)"
                />
              </el-form-item>
            </el-tab-pane>

            <!-- Definitions Sub-Tab -->
            <el-tab-pane name="definitions">
              <template #label>
                <span class="tab-label-container">
                  <span>定义</span>
                  <el-icon v-if="hasTabError('definitions')" class="tab-error-icon"><Warning /></el-icon>
                </span>
              </template>
              <el-form-item label="定义">
                <MultilingualTextEditor
                  ref="definitionEditorRef"
                  v-model="form.definitions"
                  label="定义"
                  multiline
                  :pairings="form.pairings"
                  :default-language-id="defaultLanguageId || undefined"
                  :self-context-option="selfContextOption"
                  :ai-generate="canAiGenerate"
                  @conflicts="definitionConflicts = $event"
                  @ai-generate="(i) => onAiGenerate('definition', i)"
                  @ai-generate-hover="(h, i) => onAiGenerateHover('definition', i, h)"
                />
              </el-form-item>
            </el-tab-pane>

            <!-- Pairings Sub-Tab（新建时也可配对：未保存行以 clientKey 临时引用，保存后映射为真实 id；
                 亦可直接输入新指称/定义文本随配对一并创建，见 createDesignationLine/createDefinitionLine） -->
            <el-tab-pane label="指称-定义配对" name="pairings">
              <DesignationDefinitionPairingEditor
                ref="pairingEditorRef"
                v-model="form.pairings"
                :designations="designationOptions"
                :definitions="definitionOptions"
                :self-context-option="selfContextOption"
                :create-designation="createDesignationLine"
                :create-definition="createDefinitionLine"
                :unlink-designation="unlinkDesignationLine"
                :unlink-definition="unlinkDefinitionLine"
                @delete-saved-row="onDeleteSavedPairing"
              />
            </el-tab-pane>
          </el-tabs>
        </el-tab-pane>

        <!-- Resource-specific fields Tab -->
        <el-tab-pane v-if="$slots.fields" name="fields">
          <template #label>
            <span class="tab-label-container">
              <span>属性</span>
              <el-icon v-if="hasTabError('fields')" class="tab-error-icon"><Warning /></el-icon>
            </span>
          </template>
          <slot name="fields" :form="form" />
        </el-tab-pane>

        <!-- Associations Tab：四实体（数据元/数据元概念/概念域/值域）标准双向关联 -->
        <el-tab-pane v-if="$slots.associations" name="associations">
          <template #label>
            <span class="tab-label-container">
              <span>关联</span>
              <el-icon v-if="hasTabError('associations')" class="tab-error-icon"><Warning /></el-icon>
            </span>
          </template>
          <slot name="associations" :form="form" />
        </el-tab-pane>

        <!-- Registration_State Tab：11179-3:2023 表 22 注册状态属性 -->
        <el-tab-pane v-if="showRegistration" name="registrationState">
          <template #label>
            <span class="tab-label-container">
              <span>注册状态</span>
              <el-icon v-if="hasTabError('registrationState')" class="tab-error-icon"><Warning /></el-icon>
            </span>
          </template>
          <ItemRegistrationStateSection v-model="form.registration" :is-editing="isEditing" />
        </el-tab-pane>

        <!-- Registration Records Tab：提交记录 + 主管记录 -->
        <el-tab-pane v-if="showRegistration" name="registrationRecords">
          <template #label>
            <span class="tab-label-container">
              <span>{{ $t('itemForm.registrationRecordsTab') }}</span>
              <el-icon v-if="hasTabError('registrationRecords')" class="tab-error-icon"><Warning /></el-icon>
            </span>
          </template>
          <el-collapse v-if="contacts.length > 0" v-model="registrationRecordsCollapse" style="margin-top: 16px;">
            <el-collapse-item :title="$t('itemForm.stewardshipInfo.title')" name="stewardship">
              <el-form-item :label="$t('itemForm.submissionInfo.contact')">
                <el-select
                  v-model="form.registration.stewardship.contactId"
                  :placeholder="$t('itemForm.submissionInfo.contactPlaceholder')"
                  clearable
                  style="width: 100%;"
                  data-testid="stewardship-contact-select"
                  @change="onStewardshipContactChange"
                >
                  <el-option
                    v-for="c in contacts"
                    :key="c.id"
                    :label="contactLabel(c)"
                    :value="c.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('itemForm.submissionInfo.organization')">
                <el-select
                  v-model="form.registration.stewardship.organizationId"
                  :placeholder="$t('itemForm.submissionInfo.organizationPlaceholder')"
                  clearable
                  filterable
                  style="width: 100%;"
                  data-testid="stewardship-organization-select"
                >
                  <el-option
                    v-for="org in organizationOptions"
                    :key="org.id"
                    :label="org.label"
                    :value="org.id"
                  />
                </el-select>
              </el-form-item>
            </el-collapse-item>
            <el-collapse-item :title="$t('itemForm.submissionRecords.title')" name="submissionRecords">
              <el-empty
                v-if="!form.registration.submissionRecordSummaries || form.registration.submissionRecordSummaries.length === 0"
                :description="$t('itemForm.submissionRecords.empty')"
                :image-size="60"
                data-testid="submission-records-empty"
              />
              <div v-else class="submission-records-list" data-testid="submission-records-list">
                <div
                  v-for="sr in form.registration.submissionRecordSummaries"
                  :key="sr.id"
                  class="submission-record-row"
                  data-testid="submission-record-row"
                >
                  <div class="submission-record-source">
                    <el-tag
                      :type="sr.source === 'USER_SUBMISSION' ? 'success' : 'info'"
                      size="small"
                      data-testid="submission-record-source-tag"
                    >
                      {{ $t('itemForm.submissionRecords.source.' + sr.source) }}
                    </el-tag>
                  </div>
                  <el-descriptions :column="1" size="small" border>
                    <el-descriptions-item :label="$t('itemForm.submissionRecords.contact')">
                      {{ sr.contactLabel || '—' }}
                    </el-descriptions-item>
                    <el-descriptions-item :label="$t('itemForm.submissionRecords.organization')">
                      {{ sr.organizationLabel || '—' }}
                    </el-descriptions-item>
                    <el-descriptions-item :label="$t('itemForm.submissionRecords.itemCount')">
                      {{ sr.registeredItemCount }}
                    </el-descriptions-item>
                  </el-descriptions>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
          <el-alert
            v-else-if="contactsLoaded"
            :title="$t('itemForm.stewardshipInfo.noContact')"
            type="info"
            :closable="false"
            style="margin-top: 12px;"
            data-testid="stewardship-no-contact-alert"
          />
        </el-tab-pane>
      </el-tabs>
    </el-form>

    <el-alert
      v-if="allConflicts.length"
      :title="`存在 ${allConflicts.length} 项冲突/警告`"
      type="warning"
      :closable="false"
      style="margin-top: 12px;"
    >
      <div class="conflict-list">
        <div v-for="(c, idx) in allConflicts.slice(0, 5)" :key="idx" class="conflict-item">
          <el-tag :type="c.type === 'PREFERRED' ? 'danger' : 'warning'" size="small">
            {{ c.type === 'PREFERRED' ? 'PREFERRED 冲突' : '孤立行' }}
          </el-tag>
          <span>{{ c.label }} 第 {{ c.index + 1 }} 行：{{ c.message }}</span>
        </div>
        <div v-if="allConflicts.length > 5">…等 {{ allConflicts.length }} 项</div>
      </div>
    </el-alert>

    <el-dialog
      v-model="pairingDeleteVisible"
      title="删除配对"
      width="520px"
      :close-on-click-modal="false"
      append-to-body
    >
      <p>确定删除该配对？请选择删除方式：</p>
      <el-radio-group v-model="pairingDeleteMode" class="pairing-delete-modes">
        <el-radio value="association">仅删除关联关系（保留指称和定义）</el-radio>
        <el-radio value="lines">连同配对的指称和定义一并删除</el-radio>
      </el-radio-group>
      <div class="pairing-delete-hints">
        <div v-if="pairingDeleteMode === 'association'">
          删除后指称/定义仍保留在条目中，且必须仍关联至少一个上下文，否则保存时将被拒绝。
        </div>
        <div v-else>
          若配对的指称或定义仍被其它配对引用，将拒绝删除；此时可改选"仅删除关联关系"。
        </div>
      </div>
      <template #footer>
        <el-button @click="pairingDeleteVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmDeleteSavedPairing">删除</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="isolatedConfirmVisible"
      title="发现孤立行"
      width="520px"
      :close-on-click-modal="false"
      append-to-body
    >
      <p>以下 {{ pendingIsolatedRows.length }} 行既没有上下文也未标记为例外，是否继续保存？</p>
      <ul class="isolated-list">
        <li v-for="(r, idx) in pendingIsolatedRows" :key="idx">
          {{ r.label }} 第 {{ r.index + 1 }} 行：{{ r.value }}
        </li>
      </ul>
      <template #footer>
        <el-button @click="isolatedConfirmVisible = false">取消</el-button>
        <el-button type="warning" @click="markAllIsolatedExceptions">全部标记为例外并保存</el-button>
        <el-button type="primary" @click="confirmSubmitAnyway">仍然保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="emptyConfirmVisible"
      :title="t('itemForm.emptyConfirmTitle')"
      width="520px"
      :close-on-click-modal="false"
      append-to-body
    >
      <p>{{ t('itemForm.emptyConfirmMessage', { itemType: resolvedTitle }) }}</p>
      <template #footer>
        <el-button @click="emptyConfirmVisible = false">{{ t('itemForm.emptyConfirmCancel') }}</el-button>
        <el-button type="primary" @click="confirmSubmitDespiteEmpty">{{ t('itemForm.emptyConfirmSubmitAnyway') }}</el-button>
      </template>
    </el-dialog>

    <AiCandidatePopover
      :visible="!!aiGen"
      :anchor="aiGen?.anchor ?? null"
      :field="aiGen?.field ?? 'designation'"
      :item-id="form.id"
      :cache-key="aiGen?.key ?? ''"
      :auto-generate="aiGen?.mode === 'generate'"
      :cached="aiGen ? (aiCandidateCache[aiGen.key] ?? null) : null"
      :last-error="aiGen ? (aiError[aiGen.key] ?? null) : null"
      :draft="aiGen?.draft ?? null"
      :button-hover="aiGenButtonHover"
      @apply="onAiCandidateApply"
      @close="aiGen = null"
      @generated="onAiGenerated"
      @inflight-change="onAiInflightChange"
      @failed="onAiFailed"
    />

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="gateBlocksSave" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, computed, nextTick } from 'vue';
import { useI18n } from 'vue-i18n';
const { t } = useI18n();
import { ElMessage } from 'element-plus';
import { watch } from 'vue';
import { Warning } from '@element-plus/icons-vue';
import type { FormInstance, FormRules } from 'element-plus';
import MultilingualTextEditor from './MultilingualTextEditor.vue';
import DesignationDefinitionPairingEditor from './DesignationDefinitionPairingEditor.vue';
import ItemRegistrationSection from './ItemRegistrationSection.vue';
import ItemRegistrationStateSection from './ItemRegistrationStateSection.vue';
import AiCandidatePopover from './assistant/AiCandidatePopover.vue';
import type { FieldCandidate, AiDraft } from './assistant/AiCandidatePopover.vue';
import { namingConventionApi, namespaceApi, pairingApi, myContactApi, organizationApi } from '@/api/resources';
import { languageApi } from '@/api/resources';
import { useRegistryDefaultLanguage } from '@/composables/useRegistryDefaultLanguage';
import { useAuthStore } from '@/stores/auth';
import type { TextLineForm, PairingForm, NamespaceDetailResponse, MyContactResponse, SubmissionRecordSummary } from '@/types/common';
import { SELF_CONTEXT_REFERENCE } from '@/types/common';

const auth = useAuthStore();

interface RegistrationForm {
  namespaceId?: string;
  identifier?: string;
  version?: string;
  /** Registration_State 属性（ISO/IEC 11179-3:2023 表 22） */
  registrationStatus?: string;
  administrativeStatus?: string;
  effectiveDate?: string;
  untilDate?: string;
  administrativeNote?: string;
  unresolvedIssue?: string;
  /** 条目解释性注释（explanatory comment）；null/undefined = 不变更，空串 = 清空 */
  explanatoryComment?: string;
  /** 主管信息（联系人 + 所属组织），编辑表单可维护。 */
  stewardship: { contactId: string | null; organizationId: string | null };
  /** 关联 SubmissionRecord 列表（align-submission-record-to-iso 只读展示用）。
   *  编辑表单展示历史提交记录；后端 ItemMetaResponse.submissionRecordSummaries 填充。 */
  submissionRecordSummaries?: SubmissionRecordSummary[];
}

interface ItemForm {
  id?: string;
  designations: TextLineForm[];
  definitions: TextLineForm[];
  pairings: PairingForm[];
  registration: RegistrationForm;
  [key: string]: any;
}

interface Props {
  /**
   * 元数据对象类型标识符（如 'dataElement'、'valueDomain'）。
   * 显示名称由 i18n 资源 meta.itemType.<itemType> 在启动时加载到内存，
   * 此处仅按 key 索引即可。
   */
  itemType: string;
  submitFn: (data: Record<string, unknown>) => Promise<unknown>;
  showRegistration?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  showRegistration: true,
});
const emit = defineEmits<{ success: [] }>();

const visible = ref(false);
const saving = ref(false);

/** 从 i18n 资源中按 itemType 索引该元数据对象类型的本地化显示名。 */
const resolvedTitle = computed(() => t(`meta.itemType.${props.itemType}`, props.itemType));

/**
 * 上下文条目的自引用选项：允许指称/定义/配对关联到条目自身
 * （否则无法创建第一个根上下文）。新建时条目尚无 id，用哨兵 NIL UUID，
 * 后端按 ItemAdministrationService.SELF_CONTEXT_REFERENCE 解析为条目自身；
 * 配对在保存后同步，届时把哨兵替换为真实 id。
 * 标签用条目自身名称 +「（当前）」，避免与远程搜索结果里的同一条上下文重复出现。
 */
const selfContextOption = computed(() => {
  if (props.itemType !== 'context') return undefined;
  const name = (form.designations || []).find((d) => d.value?.trim())?.value?.trim();
  return {
    id: form.id || SELF_CONTEXT_REFERENCE,
    label: `${name || '当前上下文'}（当前）`,
  };
});
const { defaultLanguageId } = useRegistryDefaultLanguage();
const formRef = ref<FormInstance>();
const registrationSectionRef = ref<InstanceType<typeof ItemRegistrationSection>>();
const pairingEditorRef = ref<InstanceType<typeof DesignationDefinitionPairingEditor>>();
const activeTab = ref('designationDefinition');
/** "指称与定义"一级 Tab 内的二级 Tab：designations / definitions / pairings */
const activeSubTab = ref('designations');
const designationEditorRef = ref<InstanceType<typeof MultilingualTextEditor>>();
const definitionEditorRef = ref<InstanceType<typeof MultilingualTextEditor>>();

/** 注册区段生成方式切换后的处理：切回自动时清除旧字段校验错误，避免残留红色警示框。 */
async function handleRegistrationModeChange(payload: { field: 'identifier' | 'version'; to: 'auto' | 'manual' }) {
  if (payload.to === 'auto') {
    await nextTick();
    formRef.value?.clearValidate([`registration.${payload.field}`]);
  }
}

const invalidFields = ref<Set<string>>(new Set());

/**
 * 已保存配对待执行的删除（保存时先于条目提交执行，服务端按选项校验约束）：
 * deleteLines=false 仅删关联；deleteLines=true 连同指称/定义一并删除。
 */
const pendingPairingDeletions = ref<{ pairingId: string; deleteLines: boolean }[]>([]);
const pairingDeleteVisible = ref(false);
const pairingDeleteTarget = ref<PairingForm | null>(null);
const pairingDeleteMode = ref<'association' | 'lines'>('association');

function onDeleteSavedPairing(row: PairingForm) {
  pairingDeleteTarget.value = row;
  pairingDeleteMode.value = 'association';
  pairingDeleteVisible.value = true;
}

/** 确认删除已保存配对：按所选方式从表单移除行/行内实例，并登记待执行删除。 */
function confirmDeleteSavedPairing() {
  const target = pairingDeleteTarget.value;
  if (!target) {
    pairingDeleteVisible.value = false;
    return;
  }
  const deleteLines = pairingDeleteMode.value === 'lines';
  if (deleteLines) {
    // 连同指称/定义一并删除：从条目表单中移除对应实例行（服务端还会校验"被其它配对引用"）
    if (target.designationId) {
      const idx = form.designations.findIndex((d) => d.id === target.designationId);
      if (idx >= 0) form.designations.splice(idx, 1);
    }
    if (target.definitionId) {
      const idx = form.definitions.findIndex((d) => d.id === target.definitionId);
      if (idx >= 0) form.definitions.splice(idx, 1);
    }
  }
  form.pairings = form.pairings.filter((p) => p.id !== target.id);
  pendingPairingDeletions.value.push({ pairingId: target.id as string, deleteLines });
  pairingDeleteVisible.value = false;
  pairingDeleteTarget.value = null;
}

function handleFieldValidate(prop: unknown, isValid: boolean) {
  if (typeof prop === 'string') {
    if (isValid) {
      invalidFields.value.delete(prop);
    } else {
      invalidFields.value.add(prop);
    }
  }
}

function getTabForField(field: string): string {
  if (field.startsWith('registration.')) {
    return 'registration';
  } else if (field.startsWith('designations')) {
    return 'designations';
  } else if (field.startsWith('definitions')) {
    return 'definitions';
  } else {
    return 'fields';
  }
}

function hasTabError(tabName: string): boolean {
  for (const field of invalidFields.value) {
    if (getTabForField(field) === tabName) {
      return true;
    }
  }
  return false;
}

/** "指称与定义"一级 Tab：任一二级（指称/定义/配对）有校验错误即显示角标。 */
function hasNamingTabError(): boolean {
  return hasTabError('designations') || hasTabError('definitions') || hasTabError('pairings');
}

/** 切换到"指称与定义"一级 Tab 并定位到指定二级 Tab。 */
function activateNamingTab(sub: 'designations' | 'definitions' | 'pairings') {
  activeTab.value = 'designationDefinition';
  activeSubTab.value = sub;
}

const form = reactive<ItemForm>({
  designations: [],
  definitions: [],
  pairings: [],
  registration: {
    stewardship: { contactId: null, organizationId: null },
    submissionRecordSummaries: [],
  },
});

/** 编辑既有条目时其后端归属注册机构名 */
const editingRaName = ref<string | null>(null);
/** 编辑既有条目时持久化的标识符创建方式（AUTO / MANUAL / null） */
const editingIdentifierCreationMode = ref<'AUTO' | 'MANUAL' | null>(null);
/** 编辑既有条目时持久化的版本创建方式（AUTO / MANUAL / null） */
const editingVersionCreationMode = ref<'AUTO' | 'MANUAL' | null>(null);
const languages = ref<{ id: string; languageIdentifier?: string; languageCode?: string; languageName?: string }[]>([]);
const namespaceNamingConventions = ref<{ id: string; label?: string; scopeRule?: string }[]>([]);
const namespaceMandatoryNC = ref(false);
const designationConflicts = ref<{ type: 'PREFERRED' | 'ISOLATED'; message: string; index: number }[]>([]);
const definitionConflicts = ref<{ type: 'PREFERRED' | 'ISOLATED'; message: string; index: number }[]>([]);
const isolatedConfirmVisible = ref(false);
const emptyConfirmVisible = ref(false);

const allConflicts = computed(() => {
  const list = [
    ...designationConflicts.value.map((c) => ({ ...c, label: '指称' })),
    ...definitionConflicts.value.map((c) => ({ ...c, label: '定义' })),
  ];
  return list;
});

const pendingIsolatedRows = computed(() => {
  const rows: { label: string; index: number; value: string }[] = [];
  designationConflicts.value
    .filter((c) => c.type === 'ISOLATED')
    .forEach((c) => {
      const row = form.designations[c.index];
      if (row) rows.push({ label: '指称', index: c.index, value: row.value || row.id || '' });
    });
  definitionConflicts.value
    .filter((c) => c.type === 'ISOLATED')
    .forEach((c) => {
      const row = form.definitions[c.index];
      if (row) rows.push({ label: '定义', index: c.index, value: row.value || row.id || '' });
    });
  return rows;
});

let submitAfterConfirm: (() => void) | null = null;

function markAllIsolatedExceptions() {
  designationConflicts.value
    .filter((c) => c.type === 'ISOLATED')
    .forEach((c) => {
      form.designations[c.index] = { ...form.designations[c.index], isolatedException: true };
    });
  definitionConflicts.value
    .filter((c) => c.type === 'ISOLATED')
    .forEach((c) => {
      form.definitions[c.index] = { ...form.definitions[c.index], isolatedException: true };
    });
  isolatedConfirmVisible.value = false;
  designationEditorRef.value?.updateConflicts?.();
  definitionEditorRef.value?.updateConflicts?.();
  if (submitAfterConfirm) submitAfterConfirm();
}

function confirmSubmitAnyway() {
  isolatedConfirmVisible.value = false;
  if (submitAfterConfirm) submitAfterConfirm();
}

function confirmSubmitDespiteEmpty() {
  emptyConfirmVisible.value = false;
  if (submitAfterConfirm) submitAfterConfirm();
}

function requestIsolatedConfirmation(onConfirm: () => void) {
  submitAfterConfirm = onConfirm;
  isolatedConfirmVisible.value = true;
}

const designationNamingConventionOptions = computed(() =>
  props.showRegistration ? namespaceNamingConventions.value : []
);

const isEditing = computed(() => !!form.id);

// 命名空间前置门槛（system-default 且无 RA 命名空间时禁保存）由注册区段组件判定
const gateBlocksSave = computed(() => registrationSectionRef.value?.gateBlocked ?? false);

watch(() => form.registration.namespaceId, (namespaceId) => {
  loadNamespaceDetail(namespaceId);
});

const designationOptions = computed(() =>
  form.designations
    .filter((d) => d.value?.trim())
    .map((d) => ({
      id: (d.id || d.clientKey)!,
      value: d.value,
      persisted: !!d.id,
      languageCode: languageCodeById(d.languageId),
      contexts: (d.contexts || []).map((c) => ({ contextId: c.contextId, acceptability: c.acceptability })),
    })),
);

const definitionOptions = computed(() =>
  form.definitions
    .filter((d) => d.value?.trim())
    .map((d) => ({
      id: (d.id || d.clientKey)!,
      value: d.value,
      persisted: !!d.id,
      languageCode: languageCodeById(d.languageId),
      contexts: (d.contexts || []).map((c) => ({ contextId: c.contextId, acceptability: c.acceptability })),
    })),
);

function languageCodeById(languageId?: string): string | undefined {
  if (!languageId) return undefined;
  const lang = languages.value.find((l) => l.id === languageId);
  return lang?.languageCode || lang?.languageIdentifier || languageId.slice(0, 4);
}

const rules = computed<FormRules>(() => ({
  designations: [
    {
      validator: (_rule: any, value: TextLineForm[], callback: any) => {
        // ISO/IEC 11179-3:2023 §6.4：Item 不强制要求 designation/definition；空集合法。
        // 仅当用户实际添加了指称行时，校验每行内容与命名规范约束。
        if (!value || value.length === 0) {
          callback();
          return;
        }
        const nonEmpty = value.filter((v) => v.value?.trim());
        if (nonEmpty.length === 0) {
          // 仅添加了空行，不视为有效条目，直接放行（提交时会被 designations 过滤逻辑丢弃）
          callback();
          return;
        }
        if (namespaceMandatoryNC.value) {
          const missing = value.filter(
            (v) => v.value?.trim() && !(v.namingConventionIds || []).length
          );
          if (missing.length > 0) {
            callback(new Error('该命名空间要求强制命名规范，请为每条指称至少选择一个命名规范'));
            return;
          }
        }
        callback();
      },
      trigger: 'change',
    },
  ],
}));

const loaded = ref(false);

// ── 提交信息（submission-record）──────────────────────────────────────
const contacts = ref<MyContactResponse[]>([]);
const contactsLoaded = ref(false);
const organizationOptions = ref<{ id: string; label: string }[]>([]);
const submissionCollapse = ref<string[]>(['submission']);
const registrationRecordsCollapse = ref<string[]>(['stewardship']);

function contactLabel(contact: MyContactResponse): string {
  const parts = [
    contact.individualName || contact.organizationName,
    contact.organizationName && contact.individualName ? `(${contact.organizationName})` : undefined,
    contact.roleName,
  ].filter(Boolean);
  const label = parts.join(' · ') || contact.id;
  return contact.isDefault ? `${label} (${t('contactIdentity.default')})` : label;
}

async function loadContacts() {
  if (contactsLoaded.value) return;
  try {
    contacts.value = await myContactApi.list();
  } catch {
    contacts.value = [];
  } finally {
    contactsLoaded.value = true;
  }
}

async function loadOrganizations() {
  try {
    const res = await organizationApi.list({ size: 200 });
    organizationOptions.value = (res.content || []).map((o: { id: string; name?: string; englishName?: string }) => ({
      id: o.id,
      label: o.name || o.englishName || '—',
    }));
  } catch {
    organizationOptions.value = [];
  }
}

function onStewardshipContactChange(contactId: string) {
  const contact = contacts.value.find((c) => c.id === contactId);
  form.registration.stewardship.organizationId = contact?.organizationId || null;
}

async function loadNamespaceDetail(namespaceId: string | undefined) {
  if (!namespaceId) {
    namespaceNamingConventions.value = [];
    namespaceMandatoryNC.value = false;
    return;
  }
  try {
    const detail = await namespaceApi.detail(namespaceId) as NamespaceDetailResponse;
    namespaceNamingConventions.value = (detail.namingConventionIds || []).map((id) => ({
      id,
      label: detail.label,
      scopeRule: undefined,
    }));
    namespaceMandatoryNC.value = !!detail.mandatoryNamingConventionIndicator;
    // Try to fetch full naming convention labels
    try {
      const ncRes = await namingConventionApi.list({ size: 200 });
      const ncMap = new Map(((ncRes.content || []) as { id: string; label?: string; scopeRule?: string }[]).map((n) => [n.id, n]));
      namespaceNamingConventions.value = namespaceNamingConventions.value.map((nc) => {
        const found = ncMap.get(nc.id);
        return found
          ? { id: nc.id, label: found.label, scopeRule: found.scopeRule }
          : nc;
      });
    } catch {
      // ignore label enrichment failures
    }
  } catch {
    namespaceNamingConventions.value = [];
    namespaceMandatoryNC.value = false;
  }
}

async function loadOptions() {
  if (loaded.value) return;
  try {
    const langRes = await languageApi.list({ size: 100 });
    languages.value = (langRes.content || []) as { id: string; languageIdentifier?: string; languageCode?: string; languageName?: string }[];
    loaded.value = true;
  } catch {
    // ignore
  }
  await loadOrganizations();
}

async function open(initial?: Record<string, unknown>) {
  reset();
  await loadOptions();
  await loadContacts();
  editingRaName.value = (initial?.meta as any)?.registrationAuthorityName || null;
  editingIdentifierCreationMode.value = (initial?.meta as any)?.identifierCreationMode ?? null;
  editingVersionCreationMode.value = (initial?.meta as any)?.versionCreationMode ?? null;
  if (initial) {
    Object.assign(form, {
      ...initial,
      designations: normalizeTextLines(initial.designations as any),
      definitions: normalizeTextLines(initial.definitions as any),
      pairings: normalizePairings(initial.pairings as any),
      registration: normalizeRegistration(initial.registration as any, initial.meta as any),
    });
    initialExplanatoryComment.value = form.registration.explanatoryComment;
    await loadNamespaceDetail(form.registration.namespaceId);
    if (initial.id) {
      try {
        const res = await pairingApi.list(initial.id as string);
        form.pairings = normalizePairings(res as any);
      } catch {
        // ignore
      }
    }
  }
  // align-submission-record-to-iso：编辑保存不再预填 submission；仅 work space 提交才需要。
  activeTab.value = props.showRegistration ? 'registration' : 'designationDefinition';
  activeSubTab.value = 'designations';
  visible.value = true;
}

function reset() {
  Object.keys(form).forEach((k) => {
    if (k !== 'designations' && k !== 'definitions' && k !== 'pairings' && k !== 'registration') {
      delete (form as any)[k];
    }
  });
  form.designations = [];
  form.definitions = [];
  form.pairings = [];
  form.registration = {
    stewardship: { contactId: null, organizationId: null },
    submissionRecordSummaries: [],
  };
  initialExplanatoryComment.value = undefined;
  invalidFields.value.clear();
  pendingPairingDeletions.value = [];
  pairingDeleteVisible.value = false;
  pairingDeleteTarget.value = null;
  editingIdentifierCreationMode.value = null;
  editingVersionCreationMode.value = null;
  contacts.value = [];
  contactsLoaded.value = false;
  submissionCollapse.value = ['submission'];
  registrationRecordsCollapse.value = ['stewardship'];
  registrationSectionRef.value?.reset();
  formRef.value?.resetFields();
  // 对话框关闭/重开：行级候选缓存、进行中/失败态与会话内悬停态一并失效
  aiCandidateCache.value = {};
  aiInflight.value = {};
  aiError.value = {};
  aiGenButtonHover.value = false;
  aiGen.value = null;
}

/** 与 MultilingualTextEditor 中 newClientKey 同规则：为未持久化行生成前端临时键。 */
function newTextLineClientKey(): string {
  return typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `ck-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/**
 * 配对下拉里输入新文本时由配对编辑器回调：把新行挂到当前表单的指称/定义列表
 * （ISO/IEC 11179-3:2023 §8.4.1.4.1/§8.4.2.4.1 允许指称/定义仅经配对存在），
 * 随条目保存一并持久化，返回 clientKey 供配对行在保存前引用。
 */
function createDesignationLine(value: string): string {
  const key = newTextLineClientKey();
  form.designations.push({
    clientKey: key,
    languageId: defaultLanguageId.value || '',
    value,
  });
  return key;
}

function createDefinitionLine(value: string): string {
  const key = newTextLineClientKey();
  form.definitions.push({
    clientKey: key,
    languageId: defaultLanguageId.value || '',
    value,
  });
  return key;
}

/* ---------- 字段级 AI 生成（仅填入表单，不自动保存；落库走既有保存路径） ---------- */

/** 打开时既有的解释性注释值：用于判断本次是否变更（后端 null=不变更，空串=清空） */
const initialExplanatoryComment = ref<string | undefined>(undefined);

/** 行级「AI 生成」入口：仅 Registrar 业务角色（新建/编辑全模式覆盖，共用本对话框的全部注册对象均受益） */
const canAiGenerate = computed(() => !!auth.isRegistrarUser);

/** 行级候选缓存（对话框会话内有效，关闭/重置即失效）：key 为 `field:行id|clientKey|index` */
const aiCandidateCache = ref<Record<string, FieldCandidate[]>>({});
/** 行级「生成进行中」标记（popover 的 start/完成/取消/失败路径维护）：悬停判定据此走加载态分支 */
const aiInflight = ref<Record<string, boolean>>({});
/** 行级最近失败信息（popover failed 事件维护）：无缓存行悬停重开时展示错误态，不自动重发 */
const aiError = ref<Record<string, string>>({});
const aiGenButtonHover = ref(false);

const aiGen = ref<{
  field: 'designation' | 'definition';
  index: number;
  anchor: HTMLElement | null;
  /** generate=点击发起新请求；view=悬停回看缓存（不发请求） */
  mode: 'view' | 'generate';
  key: string;
  /** 新建模式的生成上下文快照（点击时收集）；编辑模式为 undefined（走 itemId） */
  draft?: AiDraft;
} | null>(null);

function aiGenKey(field: 'designation' | 'definition', index: number): string {
  const rows = field === 'designation' ? form.designations : form.definitions;
  const row = rows[index];
  return `${field}:${row?.id || row?.clientKey || index}`;
}

/**
 * 新建模式生成上下文：两 tab 全部已填非空行（含目标行，空行跳过），语言 id 解析为语言代码。
 * 快照在点击时采集——发起后用户对草稿的继续编辑不影响本次提示词。
 */
function buildAiDraft(): AiDraft {
  const filled = (r: { languageId?: string; value?: string }): r is { languageId?: string; value: string } =>
    typeof r.value === 'string' && r.value.trim() !== '';
  const toLines = (rows: Array<{ languageId?: string; value?: string }>) =>
    rows.filter(filled).map((r) => {
      const lang = r.languageId
        ? languages.value.find((l) => l.id === r.languageId)
        : undefined;
      const code = lang?.languageCode || lang?.languageIdentifier;
      return code ? { language: code, value: r.value } : { value: r.value };
    });
  return {
    itemType: props.itemType,
    designations: toLines(form.designations),
    definitions: toLines(form.definitions),
  };
}

function openAiGen(field: 'designation' | 'definition', index: number, mode: 'view' | 'generate') {
  const editor = field === 'designation' ? designationEditorRef.value : definitionEditorRef.value;
  const cards = editor?.$el?.querySelectorAll('.multilingual-card');
  const anchor = cards && cards.length > index ? (cards[index] as HTMLElement) : null;
  aiGen.value = {
    field, index, anchor, mode, key: aiGenKey(field, index),
    draft: mode === 'generate' && !form.id ? buildAiDraft() : undefined,
  };
}

/** 点击「✨ AI 生成」：发起新请求，成功后覆盖该行缓存 */
function onAiGenerate(field: 'designation' | 'definition', index: number) {
  openAiGen(field, index, 'generate');
}

/** 悬停按钮三分支：进行中→重开浮层显示加载态（不发请求）；有缓存→view 模式回看（不发请求）；
 *  无缓存但上次失败→重开浮层显示错误态与重试入口（不自动重发）；三者皆否→不打开 */
function onAiGenerateHover(field: 'designation' | 'definition', index: number, hovered: boolean) {
  aiGenButtonHover.value = hovered;
  if (!hovered) return;
  const key = aiGenKey(field, index);
  if (aiInflight.value[key]) {
    openAiGen(field, index, 'generate');
    return;
  }
  if (aiCandidateCache.value[key]?.length) {
    if (aiGen.value && aiGen.value.mode === 'generate') return;
    openAiGen(field, index, 'view');
    return;
  }
  if (aiError.value[key]) openAiGen(field, index, 'generate');
}

/** popover 上报行级进行中状态；新一轮开始即作废该行上次失败态 */
function onAiInflightChange(payload: { key: string; inflight: boolean }) {
  aiInflight.value = { ...aiInflight.value, [payload.key]: payload.inflight };
  if (payload.inflight && aiError.value[payload.key]) {
    const next = { ...aiError.value };
    delete next[payload.key];
    aiError.value = next;
  }
}

/** popover 上报行级失败：供无缓存行悬停重开时展示错误态 */
function onAiFailed(payload: { key: string; message: string }) {
  aiError.value = { ...aiError.value, [payload.key]: payload.message };
}

/** 生成成功：回写/覆盖行级缓存并清除该行失败态（失败不触发本事件，旧缓存保留） */
function onAiGenerated(payload: { key: string; candidates: FieldCandidate[] }) {
  aiCandidateCache.value = { ...aiCandidateCache.value, [payload.key]: payload.candidates };
  if (aiError.value[payload.key]) {
    const next = { ...aiError.value };
    delete next[payload.key];
    aiError.value = next;
  }
}

/** 候选语种（如 zh-CN）映射到注册语言实体 id；匹配不到回退默认语言。 */
function resolveLanguageId(languageCode?: string): string {
  if (languageCode) {
    const normalized = languageCode.trim().toLowerCase();
    const exact = languages.value.find(
      (l) =>
        (l.languageCode || '').toLowerCase() === normalized ||
        (l.languageIdentifier || '').toLowerCase() === normalized,
    );
    if (exact) return exact.id;
    const prefix = normalized.split('-')[0];
    const partial = languages.value.find(
      (l) =>
        (l.languageCode || '').toLowerCase().startsWith(prefix) ||
        (l.languageIdentifier || '').toLowerCase().startsWith(prefix),
    );
    if (partial) return partial.id;
  }
  return defaultLanguageId.value || '';
}

/** 字段级生成候选回填：写入选中行输入框（多语行按行语言回填；行无语言时按候选语种解析）。 */
function onAiCandidateApply(candidate: FieldCandidate) {
  const target = aiGen.value;
  if (!target) return;
  const rows = target.field === 'designation' ? form.designations : form.definitions;
  const row = rows[target.index];
  if (!row) return;
  row.value = candidate.text;
  if (!row.languageId) {
    row.languageId = resolveLanguageId(candidate.language);
  }
  const editor = target.field === 'designation' ? designationEditorRef.value : definitionEditorRef.value;
  void nextTick().then(() => editor?.updateConflicts?.());
  ElMessage.success(t('aiGenerate.applied'));
}

/** 配对改绑/清除/删除后清理孤立的内联新建行：仅移除未持久化且不再被任何配对引用的行。 */
function unlinkDesignationLine(id: string): void {
  if (form.pairings.some((p) => p.designationId === id)) return;
  const idx = form.designations.findIndex((d) => !d.id && d.clientKey === id);
  if (idx >= 0) form.designations.splice(idx, 1);
}

function unlinkDefinitionLine(id: string): void {
  if (form.pairings.some((p) => p.definitionId === id)) return;
  const idx = form.definitions.findIndex((d) => !d.id && d.clientKey === id);
  if (idx >= 0) form.definitions.splice(idx, 1);
}

function normalizeTextLines(raw: unknown): TextLineForm[] {
  if (!Array.isArray(raw)) return [];
  return raw.map((r: any) => ({
    id: r.id || undefined,
    clientKey: r.id ? undefined : (r.clientKey || newTextLineClientKey()),
    languageId: r.languageId || r.language_id || r.languageID || '',
    value: r.value || r.sign || r.text || '',
    sourceId: r.sourceId || r.source_id || r.sourceID || undefined,
    contexts: normalizeContexts(r.contexts),
    namingConventionIds: Array.isArray(r.namingConventionIds)
      ? r.namingConventionIds
      : (Array.isArray(r.namingConvention_ids) ? r.namingConvention_ids : undefined),
  }));
}

function normalizeContexts(raw: unknown): TextLineForm['contexts'] {
  if (!Array.isArray(raw)) return undefined;
  return raw.map((c: any) => ({
    contextId: c.contextId || c.context_id || c.contextID || '',
    acceptability: c.acceptability || 'ADMITTED',
  }));
}

function normalizePairings(raw: unknown): PairingForm[] {
  if (!Array.isArray(raw)) return [];
  return raw.map((p: any) => ({
    id: p.id || undefined,
    designationId: p.designationId || p.designation_id || p.designationID || '',
    definitionId: p.definitionId || p.definition_id || p.definitionID || '',
    contextId: p.contextId || p.context_id || p.contextID || undefined,
    acceptability: p.acceptability || 'ADMITTED',
  }));
}

function normalizeRegistration(
  raw: Record<string, unknown> | undefined,
  meta: Record<string, unknown> | undefined
): RegistrationForm {
  const reg = (raw || {}) as Partial<RegistrationForm>;
  const stewardship = reg.stewardship || {
    contactId: (meta?.stewardshipContactId as string) || null,
    organizationId: (meta?.stewardshipOrganizationId as string) || null,
  };
  // align-submission-record-to-iso：从 meta.submissionRecordSummaries 回填历史提交记录
  // （后端 ItemMetaResponse.submissionRecordSummaries），只读展示。
  const submissionRecordSummaries = (Array.isArray(meta?.submissionRecordSummaries)
      ? (meta?.submissionRecordSummaries as SubmissionRecordSummary[])
      : []) as SubmissionRecordSummary[];
  return {
    namespaceId: reg.namespaceId || (meta?.namespaceId as string) || undefined,
    identifier: reg.identifier || (meta?.identifier as string) || undefined,
    version: reg.version || (meta?.version as string) || undefined,
    registrationStatus: reg.registrationStatus || (meta?.registrationStatus as string) || undefined,
    administrativeStatus: reg.administrativeStatus || (meta?.administrativeStatus as string) || undefined,
    effectiveDate: reg.effectiveDate || (meta?.stateEffectiveDate as string) || undefined,
    untilDate: reg.untilDate || (meta?.stateUntilDate as string) || undefined,
    administrativeNote: reg.administrativeNote || (meta?.administrativeNote as string) || undefined,
    unresolvedIssue: reg.unresolvedIssue || (meta?.unresolvedIssue as string) || undefined,
    explanatoryComment: reg.explanatoryComment || (meta?.explanatoryComment as string) || undefined,
    stewardship,
    submissionRecordSummaries,
  };
}

async function handleSubmit() {
  if (!formRef.value) return;
  const pairingsValid = pairingEditorRef.value?.validate() ?? true;
  if (!pairingsValid) {
    activateNamingTab('pairings');
    ElMessage.warning('配对信息不完整或存在重复，请检查');
    return;
  }
  // Recompute conflicts from editors to ensure latest state
    designationEditorRef.value?.updateConflicts?.();
    definitionEditorRef.value?.updateConflicts?.();
  const hasPreferredConflict = allConflicts.value.some((c) => c.type === 'PREFERRED');
  if (hasPreferredConflict) {
    ElMessage.warning('存在 PREFERRED 冲突，请先修正');
    return;
  }
  // ISO/IEC 11179-3:2023 §6.4 允许 Item 不带指称/定义，但空集不利于后续检索与可读性。
  // 这里在两类皆为空时仅做一次确认放行，不阻塞保存。
  const hasAnyDesignation = form.designations.some((d) => d.value?.trim());
  const hasAnyDefinition = form.definitions.some((d) => d.value?.trim());
  if (!hasAnyDesignation && !hasAnyDefinition) {
    submitAfterConfirm = () => proceedToIsolatedOrSubmit();
    emptyConfirmVisible.value = true;
    return;
  }
  const hasIsolated = pendingIsolatedRows.value.length > 0;
  if (hasIsolated) {
    requestIsolatedConfirmation(() => handleSubmitInternal());
    return;
  }
  await handleSubmitInternal();
}

/** 跳过"指称/定义为空"确认后，复用孤立行判定与提交入口。 */
function proceedToIsolatedOrSubmit() {
  if (pendingIsolatedRows.value.length > 0) {
    requestIsolatedConfirmation(() => handleSubmitInternal());
    return;
  }
  handleSubmitInternal();
}

async function handleSubmitInternal() {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid, fields) => {
    if (!valid) {
      if (fields) {
        // Collect all invalid fields
        invalidFields.value.clear();
        Object.keys(fields).forEach((key) => {
          invalidFields.value.add(key);
        });

        const firstField = Object.keys(fields)[0];
        const targetTab = getTabForField(firstField);
        if (targetTab === 'designations' || targetTab === 'definitions' || targetTab === 'pairings') {
          activateNamingTab(targetTab);
        } else {
          activeTab.value = targetTab;
        }

        ElMessage.warning('表单验证失败，请检查所有标签页中的必填/非空项');
      }
      return;
    }
    saving.value = true;
    try {
      const payload: Record<string, unknown> = {};
      // Copy non-built-in fields
      Object.keys(form).forEach((k) => {
        if (k !== 'designations' && k !== 'definitions' && k !== 'pairings' && k !== 'registration') {
          payload[k] = (form as any)[k];
        }
      });
      // 被配对引用的行在前端口径下不算孤立行；但配对在条目保存之后才创建，
      // 后端 validateIsolatedLines 又只认上下文或例外标记，因此这里给被配对行
      // 置瞬态 isolatedException（该字段不持久化，仅用于本次提交放行）。
      const pairedLineKeys = new Set<string>();
      form.pairings.forEach((p) => {
        if (p.designationId) pairedLineKeys.add(p.designationId);
        if (p.definitionId) pairedLineKeys.add(p.definitionId);
      });
      // Normalize designations/definitions for API（携带已持久化行的 id，
      // 后端按 id 就地更新，避免重建行触发配对外键冲突）
      payload.designations = form.designations
        .filter((d) => d.value?.trim())
        .map((d) => ({
          id: d.id || null,
          languageId: d.languageId || defaultLanguageId.value || null,
          value: d.value.trim(),
          sourceId: d.sourceId || null,
          namingConventionIds: d.namingConventionIds || [],
          isolatedException: d.isolatedException || pairedLineKeys.has(d.id || d.clientKey || '') || null,
          contexts: (d.contexts || []).map((c) => ({
            contextId: c.contextId,
            acceptability: c.acceptability,
          })),
        }));
      payload.definitions = form.definitions
        .filter((d) => d.value?.trim())
        .map((d) => ({
          id: d.id || null,
          languageId: d.languageId || defaultLanguageId.value || null,
          value: d.value.trim(),
          sourceId: d.sourceId || null,
          namingConventionIds: d.namingConventionIds || [],
          isolatedException: d.isolatedException || pairedLineKeys.has(d.id || d.clientKey || '') || null,
          contexts: (d.contexts || []).map((c) => ({
            contextId: c.contextId,
            acceptability: c.acceptability,
          })),
        }));
      if (props.showRegistration) {
        const varError = registrationSectionRef.value?.validateTemplateVariables() ?? null;
        if (varError) {
          ElMessage.error(varError);
          throw new Error(varError);
        }
        payload.registration = registrationSectionRef.value?.buildPayload();
        // 解释性注释：仅在用户实际变更时携带（后端 null=不变更，空串=清空），
        // 避免未回显该字段的编辑场景把既有注释覆盖掉
        const comment = form.registration.explanatoryComment;
        if (comment !== undefined && comment !== initialExplanatoryComment.value) {
          payload.registration = {
            ...(payload.registration as Record<string, unknown>),
            explanatoryComment: comment,
          };
        }
        // 提交信息：align-submission-record-to-iso，编辑保存不再携带 submission 字段；
        // SubmissionRecord 仅在工作空间提交时创建。
        // 主管信息：仅当显式选择联系人时携带（organizationId 可空传 null）
        if (form.registration.stewardship?.contactId) {
          payload.registration = {
            ...(payload.registration as Record<string, unknown>),
            stewardship: {
              contactId: form.registration.stewardship.contactId || null,
              organizationId: form.registration.stewardship.organizationId || null,
            },
          };
        }
      }
      // 先执行已保存配对的待删除：服务端按 deleteLines 校验约束（留下的行须仍有
      // 上下文；一并删除的行须未被其它配对引用）。失败则中断保存，条目本身不变。
      if (form.id && pendingPairingDeletions.value.length) {
        for (const pd of pendingPairingDeletions.value) {
          await pairingApi.delete(form.id as string, pd.pairingId, { deleteLines: pd.deleteLines });
        }
        pendingPairingDeletions.value = [];
      }
      const result = await props.submitFn(payload);
      submitAfterConfirm = null;

      // 保存后同步配对（新建模式下用保存响应把 clientKey 映射为真实 id）
      const itemId = form.id || (result as any)?.id;
      if (itemId) {
        await syncPairings(itemId as string, result);
      }

      ElMessage.success('保存成功');
      visible.value = false;
      emit('success');
    } catch (err: any) {
      // submitFn implementations usually show their own ElMessage.error;
      // swallow the error here so it does not become an unhandled rejection.
      console.error('提交失败', err);
      // 请求未发出的本地错误（如 payload 含不可序列化对象导致 axios 序列化失败）
      // 没有响应拦截器兜底，必须显式提示，否则用户误以为已保存。
      if (!err?.response) {
        ElMessage.error(t('itemForm.saveFailed', { message: String(err?.message || err) }));
      }
    } finally {
      saving.value = false;
    }
  });
}

/** 后端按 payload 顺序重建指称/定义（见 ItemAdministrationService.applyDesignations），
 *  因此可按下标把表单行（含 clientKey 的新建行、id 已过期的编辑行）映射到保存响应中的真实 id。 */
function buildSavedLineIdMap(result: any): Map<string, string> {
  const map = new Map<string, string>();
  const fill = (rows: TextLineForm[], saved: any[] | undefined) => {
    if (!Array.isArray(saved)) return;
    const nonEmpty = rows.filter((r) => r.value?.trim());
    nonEmpty.forEach((r, i) => {
      const savedId = saved[i]?.id;
      if (!savedId) return;
      if (r.clientKey) map.set(r.clientKey, savedId);
      if (r.id) map.set(r.id, savedId);
    });
  };
  fill(form.designations, result?.designations);
  fill(form.definitions, result?.definitions);
  return map;
}

const UUID_LIKE = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

async function syncPairings(itemId: string, result?: unknown) {
  const idMap = buildSavedLineIdMap(result);
  const resolveLineId = (key: string | undefined): string => {
    if (!key) return '';
    const resolved = idMap.get(key) || key;
    // 行已被删除等场景下 clientKey 无法解析为真实 id，跳过该配对
    return UUID_LIKE.test(resolved) ? resolved : '';
  };
  try {
    // 已保存配对：指称/定义引用不可改，但上下文与可接受性可编辑，这里整体 PUT 持久化；
    // 配对本身只随"删除配对"流程移除（服务端按 deleteLines 校验），不做删后重建。
    for (const p of form.pairings) {
      const contextId = p.contextId === SELF_CONTEXT_REFERENCE ? itemId : (p.contextId || null);
      if (p.id) {
        await pairingApi.update(itemId, p.id as string, {
          designationId: resolveLineId(p.designationId),
          definitionId: resolveLineId(p.definitionId),
          contextId,
          acceptability: p.acceptability,
        });
        continue;
      }
      const designationId = resolveLineId(p.designationId);
      const definitionId = resolveLineId(p.definitionId);
      if (designationId && definitionId) {
        const created = await pairingApi.create(itemId, {
          designationId,
          definitionId,
          // 新建上下文时配对上下文的自引用哨兵，在条目保存后映射为真实 id
          contextId,
          acceptability: p.acceptability,
        });
        p.id = created?.id || p.id;
      }
    }
  } catch {
    // ignore pairing sync errors for now
  }
}

// form 一并暴露：视图在 open() 后需要回写类型/默认值（值域、概念域的 kind 预置与有效期预填）
defineExpose({ open, form });
</script>

<style scoped>
.submission-records-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.submission-record-row {
  padding: 8px 0;
}

.submission-record-source {
  margin-bottom: 6px;
}
</style>

<style scoped>
/* "指称与定义"内嵌二级 Tab：弱化边框，避免与外层 border-card 叠加过重 */
.naming-sub-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}
.naming-sub-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
}
.tab-label-container {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.tab-error-icon {
  color: var(--el-color-danger);
  font-size: 14px;
}
.conflict-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.conflict-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.isolated-list {
  max-height: 200px;
  overflow-y: auto;
  padding-left: 20px;
  margin: 8px 0;
}
.isolated-list li {
  margin-bottom: 4px;
  font-size: 13px;
}
.pairing-delete-modes {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  margin: 8px 0;
}
.pairing-delete-modes :deep(.el-radio) {
  margin-right: 0;
  height: auto;
}
.pairing-delete-hints {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-color-warning);
  line-height: 1.5;
}
</style>
