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

        <!-- Designations Tab -->
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
              @conflicts="designationConflicts = $event"
            />
          </el-form-item>
        </el-tab-pane>

        <!-- Definitions Tab -->
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
              @conflicts="definitionConflicts = $event"
            />
          </el-form-item>
        </el-tab-pane>

        <!-- Pairings Tab（新建时也可配对：未保存行以 clientKey 临时引用，保存后映射为真实 id） -->
        <el-tab-pane label="指称-定义配对" name="pairings">
        <DesignationDefinitionPairingEditor
          ref="pairingEditorRef"
          v-model="form.pairings"
          :designations="designationOptions"
          :definitions="definitionOptions"
        />
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

        <!-- Registration_State Tab：11179-3:2023 表 22 注册状态属性，置于末位 -->
        <el-tab-pane v-if="showRegistration" name="registrationState">
          <template #label>
            <span class="tab-label-container">
              <span>注册状态</span>
              <el-icon v-if="hasTabError('registrationState')" class="tab-error-icon"><Warning /></el-icon>
            </span>
          </template>
          <ItemRegistrationStateSection v-model="form.registration" />
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

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="gateBlocksSave" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed, nextTick } from 'vue';
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
import { namingConventionApi, namespaceApi, pairingApi } from '@/api/resources';
import { languageApi } from '@/api/resources';
import { useRegistryDefaultLanguage } from '@/composables/useRegistryDefaultLanguage';
import type { TextLineForm, PairingForm, NamespaceDetailResponse } from '@/types/common';

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
const { defaultLanguageId } = useRegistryDefaultLanguage();
const formRef = ref<FormInstance>();
const registrationSectionRef = ref<InstanceType<typeof ItemRegistrationSection>>();
const pairingEditorRef = ref<InstanceType<typeof DesignationDefinitionPairingEditor>>();
const activeTab = ref('designations');
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

function handleFieldValidate(prop: any, isValid: boolean) {
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

const form = reactive<ItemForm>({
  designations: [],
  definitions: [],
  pairings: [],
  registration: {},
});

/** 编辑既有条目时其后端归属注册机构名 */
const editingRaName = ref<string | null>(null);
/** 编辑既有条目时持久化的标识符创建方式（AUTO / MANUAL / null） */
const editingIdentifierCreationMode = ref<'AUTO' | 'MANUAL' | null>(null);
/** 编辑既有条目时持久化的版本创建方式（AUTO / MANUAL / null） */
const editingVersionCreationMode = ref<'AUTO' | 'MANUAL' | null>(null);
const languages = ref<{ id: string; languageIdentifier?: string; languageCode?: string }[]>([]);
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
      const ncMap = new Map(((ncRes.content || []) as any[]).map((n) => [n.id, n]));
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
    languages.value = (langRes.content || []) as any;
    loaded.value = true;
  } catch {
    // ignore
  }
}

async function open(initial?: Record<string, unknown>) {
  reset();
  await loadOptions();
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
  activeTab.value = props.showRegistration ? 'registration' : 'designations';
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
  form.registration = {};
  invalidFields.value.clear();
  editingIdentifierCreationMode.value = null;
  editingVersionCreationMode.value = null;
  registrationSectionRef.value?.reset();
  formRef.value?.resetFields();
}

/** 与 MultilingualTextEditor 中 newClientKey 同规则：为未持久化行生成前端临时键。 */
function newTextLineClientKey(): string {
  return typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `ck-${Date.now()}-${Math.random().toString(36).slice(2)}`;
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
  const reg = (raw || {}) as RegistrationForm;
  return {
    namespaceId: reg.namespaceId || (meta?.namespaceId as string) || undefined,
    identifier: reg.identifier || (meta?.identifier as string) || undefined,
    version: reg.version || (meta?.version as string) || undefined,
    // Registration_State 回显（11179-3:2023 表 22）：编辑态从 meta 回填，创建态留空走默认
    registrationStatus: reg.registrationStatus || (meta?.registrationStatus as string) || undefined,
    administrativeStatus: reg.administrativeStatus || (meta?.administrativeStatus as string) || undefined,
    effectiveDate: reg.effectiveDate || (meta?.stateEffectiveDate as string) || undefined,
    untilDate: reg.untilDate || (meta?.stateUntilDate as string) || undefined,
    administrativeNote: reg.administrativeNote || (meta?.administrativeNote as string) || undefined,
    unresolvedIssue: reg.unresolvedIssue || (meta?.unresolvedIssue as string) || undefined,
  };
}

async function handleSubmit() {
  if (!formRef.value) return;
  const pairingsValid = pairingEditorRef.value?.validate() ?? true;
  if (!pairingsValid) {
    activeTab.value = 'pairings';
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
        activeTab.value = getTabForField(firstField);

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
      // Normalize designations/definitions for API
      payload.designations = form.designations
        .filter((d) => d.value?.trim())
        .map((d) => ({
          languageId: d.languageId || defaultLanguageId || null,
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
          languageId: d.languageId || defaultLanguageId || null,
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
    const existing = await pairingApi.list(itemId) as any[];
    // Delete all existing pairings
    for (const p of existing) {
      if (p.id) await pairingApi.delete(itemId, p.id);
    }
    // Recreate all current pairings
    for (const p of form.pairings) {
      const designationId = resolveLineId(p.designationId);
      const definitionId = resolveLineId(p.definitionId);
      if (designationId && definitionId) {
        await pairingApi.create(itemId, {
          designationId,
          definitionId,
          contextId: p.contextId || null,
          acceptability: p.acceptability,
        });
      }
    }
  } catch {
    // ignore pairing sync errors for now
  }
}

defineExpose({ open });
</script>

<style scoped>
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
</style>
