<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="760px"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="reset"
  >
    <el-tabs v-model="activeTab" type="border-card">
      <!-- Registration Tab -->
      <el-tab-pane v-if="showRegistration" label="注册标识" name="registration">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="100px"
        >
          <el-form-item label="命名空间" prop="registration.namespaceId">
            <el-select
              v-model="form.registration.namespaceId"
              placeholder="选择命名空间"
              style="width: 100%;"
              clearable
            >
              <el-option
                v-for="ns in namespaces"
                :key="ns.id"
                :label="ns.identifier || ns.name || ns.id"
                :value="ns.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="标识符" prop="registration.identifier">
            <el-input v-model="form.registration.identifier" placeholder="唯一标识符" />
          </el-form-item>
          <el-form-item label="版本" prop="registration.version">
            <el-input v-model="form.registration.version" placeholder="如 1.0" />
          </el-form-item>
          <el-form-item label="注册机构" prop="registration.registrationAuthorityId">
            <el-select
              v-model="form.registration.registrationAuthorityId"
              placeholder="选择注册机构"
              style="width: 100%;"
              clearable
            >
              <el-option
                v-for="ra in registrationAuthorities"
                :key="ra.id"
                :label="ra.name || ra.identifier || ra.id"
                :value="ra.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- Designations Tab -->
      <el-tab-pane label="指称" name="designations">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="80px"
        >
          <el-form-item label="指称" prop="designations">
            <MultilingualTextEditor
              v-model="form.designations"
              label="指称"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- Definitions Tab -->
      <el-tab-pane label="定义" name="definitions">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="80px"
        >
          <el-form-item label="定义">
            <MultilingualTextEditor
              v-model="form.definitions"
              label="定义"
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- Pairings Tab -->
      <el-tab-pane v-if="form.id" label="指称-定义配对" name="pairings">
        <DesignationDefinitionPairingEditor
          v-model="form.pairings"
          :designations="designationOptions"
          :definitions="definitionOptions"
        />
      </el-tab-pane>

      <!-- Resource-specific fields Tab -->
      <el-tab-pane label="属性" name="fields">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="120px"
        >
          <slot name="fields" :form="form" />
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import MultilingualTextEditor from './MultilingualTextEditor.vue';
import DesignationDefinitionPairingEditor from './DesignationDefinitionPairingEditor.vue';
import { namespaceApi, registrationAuthorityApi, pairingApi } from '@/api/resources';
import type { TextLineForm, PairingForm } from '@/types/common';

interface RegistrationForm {
  namespaceId?: string;
  identifier?: string;
  version?: string;
  registrationAuthorityId?: string;
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
  title: string;
  submitFn: (data: Record<string, unknown>) => Promise<unknown>;
  showRegistration?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  showRegistration: true,
});
const emit = defineEmits<{ success: [] }>();

const visible = ref(false);
const saving = ref(false);
const formRef = ref<FormInstance>();
const activeTab = ref('designations');

const form = reactive<ItemForm>({
  designations: [],
  definitions: [],
  pairings: [],
  registration: {},
});

const namespaces = ref<{ id: string; identifier?: string; name?: string }[]>([]);
const registrationAuthorities = ref<{ id: string; identifier?: string; name?: string }[]>([]);

const designationOptions = computed(() =>
  form.designations
    .filter((d) => d.id && d.value)
    .map((d) => ({ id: d.id!, value: d.value })),
);

const definitionOptions = computed(() =>
  form.definitions
    .filter((d) => d.id && d.value)
    .map((d) => ({ id: d.id!, value: d.value })),
);

const rules: FormRules = {
  'registration.namespaceId': [{ required: true, message: '请选择命名空间', trigger: 'change' }],
  'registration.identifier': [{ required: true, message: '请输入标识符', trigger: 'blur' }],
  designations: [
    {
      validator: (_rule: any, value: TextLineForm[], callback: any) => {
        if (!value || value.length === 0) {
          callback(new Error('至少添加一条指称'));
          return;
        }
        const valid = value.filter((v) => v.languageId && v.value?.trim()).length > 0;
        if (!valid) {
          callback(new Error('至少有一条完整的指称（语言+内容）'));
          return;
        }
        callback();
      },
      trigger: 'change',
    },
  ],
};

onMounted(async () => {
  try {
    const [nsRes, raRes] = await Promise.all([
      namespaceApi.list({ size: 100 }),
      registrationAuthorityApi.list({ size: 100 }),
    ]);
    namespaces.value = (nsRes.content || []) as any;
    registrationAuthorities.value = (raRes.content || []) as any;
  } catch {
    // ignore
  }
});

async function open(initial?: Record<string, unknown>) {
  reset();
  if (initial) {
    Object.assign(form, {
      ...initial,
      designations: normalizeTextLines(initial.designations as any),
      definitions: normalizeTextLines(initial.definitions as any),
      pairings: normalizePairings(initial.pairings as any),
      registration: normalizeRegistration(initial.registration as any, initial.meta as any),
    });
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
  formRef.value?.resetFields();
}

function normalizeTextLines(raw: unknown): TextLineForm[] {
  if (!Array.isArray(raw)) return [];
  return raw.map((r: any) => ({
    id: r.id || undefined,
    languageId: r.languageId || r.language_id || r.languageID || '',
    value: r.value || r.sign || r.text || '',
    sourceId: r.sourceId || r.source_id || r.sourceID || undefined,
    contexts: normalizeContexts(r.contexts),
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
    registrationAuthorityId: reg.registrationAuthorityId || undefined,
  };
}

async function handleSubmit() {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (!valid) return;
    saving.value = true;
    try {
      const payload: Record<string, unknown> = {};
      // Copy non-built-in fields
      Object.keys(form).forEach((k) => {
        if (k !== 'designations' && k !== 'definitions' && k !== 'pairings' && k !== 'registration') {
          payload[k] = (form as any)[k];
        }
      });
      // Normalize designations/definitions for API
      payload.designations = form.designations
        .filter((d) => d.languageId && d.value?.trim())
        .map((d) => ({
          languageId: d.languageId,
          value: d.value.trim(),
          sourceId: d.sourceId || null,
          contexts: (d.contexts || []).map((c) => ({
            contextId: c.contextId,
            acceptability: c.acceptability,
          })),
        }));
      payload.definitions = form.definitions
        .filter((d) => d.languageId && d.value?.trim())
        .map((d) => ({
          languageId: d.languageId,
          value: d.value.trim(),
          sourceId: d.sourceId || null,
          contexts: (d.contexts || []).map((c) => ({
            contextId: c.contextId,
            acceptability: c.acceptability,
          })),
        }));
      if (props.showRegistration) {
        payload.registration = {
          namespaceId: form.registration.namespaceId || null,
          identifier: form.registration.identifier || '',
          version: form.registration.version || '',
          registrationAuthorityId: form.registration.registrationAuthorityId || null,
        };
      }
      const result = await props.submitFn(payload);

      // Sync pairings if editing
      const itemId = form.id || (result as any)?.id;
      if (itemId) {
        await syncPairings(itemId as string);
      }

      ElMessage.success('保存成功');
      visible.value = false;
      emit('success');
    } finally {
      saving.value = false;
    }
  });
}

async function syncPairings(itemId: string) {
  try {
    const existing = await pairingApi.list(itemId) as any[];
    // Delete all existing pairings
    for (const p of existing) {
      if (p.id) await pairingApi.delete(itemId, p.id);
    }
    // Recreate all current pairings
    for (const p of form.pairings) {
      if (p.designationId && p.definitionId) {
        await pairingApi.create(itemId, {
          designationId: p.designationId,
          definitionId: p.definitionId,
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
