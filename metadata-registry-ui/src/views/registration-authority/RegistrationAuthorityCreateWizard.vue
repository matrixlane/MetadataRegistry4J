<template>
  <el-dialog
    v-model="visible"
    :title="$t('registrationAuthority.wizard.title')"
    width="720px"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="resetState"
  >
    <el-steps :active="currentStep" align-center finish-status="success" style="margin-bottom: 24px;">
      <el-step :title="$t('registrationAuthority.wizard.stepRa')" />
      <el-step :title="$t('registrationAuthority.wizard.stepNamespace')" />
    </el-steps>

    <!-- 步骤一：注册机构信息 -->
    <el-form
      v-if="currentStep === 0"
      ref="step1FormRef"
      :model="form"
      :rules="step1Rules"
      label-width="auto"
    >
      <el-alert
        v-if="edition === 'PERSONAL'"
        type="info"
        :closable="false"
        :title="$t('registrationAuthority.createAutoRegistrarHint')"
        data-testid="create-registrar-hint"
        style="margin-bottom: 12px;"
      />
      <el-form-item
        :label="$t('registrationAuthority.form.internationalCodeDesignator')"
        prop="internationalCodeDesignator"
        required
      >
        <el-input v-model="form.internationalCodeDesignator" data-testid="ra-icd-input" />
      </el-form-item>
      <el-form-item
        :label="$t('registrationAuthority.form.identifier')"
        prop="organizationIdentifier"
        required
      >
        <el-input v-model="form.organizationIdentifier" data-testid="ra-org-input" />
      </el-form-item>
      <el-form-item :label="$t('registrationAuthority.form.organizationPartIdentifier')">
        <el-input v-model="form.organizationPartIdentifier" data-testid="ra-org-part-input" />
      </el-form-item>
      <el-form-item :label="$t('registrationAuthority.form.name')">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item :label="$t('registrationAuthority.form.englishName')">
        <el-input v-model="form.englishName" />
      </el-form-item>
      <el-form-item :label="$t('registrationAuthority.form.mailAddress')">
        <el-input v-model="form.mailAddress" data-testid="ra-mail-input" />
      </el-form-item>
      <el-form-item :label="$t('registrationAuthority.form.emailAddress')">
        <el-select
          v-model="form.emailAddress"
          multiple
          filterable
          allow-create
          default-first-option
          :placeholder="$t('registrationAuthority.form.emailAddressPlaceholder')"
          style="width: 100%;"
          data-testid="ra-email-input"
        />
      </el-form-item>
      <el-form-item :label="$t('registrationAuthority.form.phoneNumber')">
        <el-select
          v-model="form.phoneNumber"
          multiple
          filterable
          allow-create
          default-first-option
          :placeholder="$t('registrationAuthority.form.phoneNumberPlaceholder')"
          style="width: 100%;"
          data-testid="ra-phone-input"
        />
      </el-form-item>
      <el-form-item :label="$t('registrationAuthority.form.webAddress')">
        <el-input v-model="form.webAddress" data-testid="ra-web-input" />
      </el-form-item>
      <el-form-item
        v-if="edition !== 'PERSONAL'"
        :label="$t('registrationAuthority.wizard.selfRegistrarSwitch')"
      >
        <el-switch v-model="form.selfAsRegistrar" data-testid="ra-wizard-self-registrar-switch" />
      </el-form-item>
    </el-form>

    <!-- 步骤二：命名空间（可选） -->
    <div v-else>
      <div style="margin-bottom: 16px;">
        <el-switch
          v-model="form.createNamespace"
          :active-text="$t('registrationAuthority.wizard.createNamespaceSwitch')"
          data-testid="ra-wizard-create-ns-switch"
        />
      </div>
      <el-form
        v-if="form.createNamespace"
        ref="step2FormRef"
        :model="form"
        :rules="step2Rules"
        label-position="top"
      >
        <el-form-item :label="$t('registrationAuthority.wizard.namespaceName')" prop="namespaceName">
          <el-input
            v-model="form.namespaceName"
            :placeholder="$t('registrationAuthority.wizard.namespaceNamePlaceholder')"
            clearable
            data-testid="ra-wizard-ns-name-input"
          />
        </el-form-item>

        <el-divider content-position="left">{{ $t('registrationAuthority.wizard.identifierSection') }}</el-divider>
        <el-form-item :label="$t('registrationAuthority.wizard.inputMode')" prop="identifierInputMode">
          <el-radio-group v-model="form.identifierInputMode">
            <el-radio-button value="AUTO_ONLY">{{ $t('registrationAuthority.wizard.modeAutoOnly') }}</el-radio-button>
            <el-radio-button value="AUTO_WITH_OVERRIDE">{{ $t('registrationAuthority.wizard.modeAutoWithOverride') }}</el-radio-button>
            <el-radio-button value="MANUAL_ONLY">{{ $t('registrationAuthority.wizard.modeManualOnly') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-alert
          v-if="form.identifierInputMode === 'MANUAL_ONLY'"
          :title="$t('registrationAuthority.wizard.modeManualOnlyHint')"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px;"
        />
        <el-form-item :label="$t('registrationAuthority.wizard.identifierTemplate')" prop="identifierTemplate">
          <SegmentTemplateDesigner
            v-model="form.identifierTemplate"
            :disabled="form.identifierInputMode === 'MANUAL_ONLY'"
            style="width: 100%;"
          />
        </el-form-item>

        <el-divider content-position="left">{{ $t('registrationAuthority.wizard.versionSection') }}</el-divider>
        <el-form-item :label="$t('registrationAuthority.wizard.inputMode')" prop="versionInputMode">
          <el-radio-group v-model="form.versionInputMode">
            <el-radio-button value="AUTO_ONLY">{{ $t('registrationAuthority.wizard.modeAutoOnly') }}</el-radio-button>
            <el-radio-button value="AUTO_WITH_OVERRIDE">{{ $t('registrationAuthority.wizard.modeAutoWithOverride') }}</el-radio-button>
            <el-radio-button value="MANUAL_ONLY">{{ $t('registrationAuthority.wizard.modeManualOnly') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-alert
          v-if="form.versionInputMode === 'MANUAL_ONLY'"
          :title="$t('registrationAuthority.wizard.versionManualOnlyHint')"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px;"
        />
        <el-form-item :label="$t('registrationAuthority.wizard.versionPolicy')" prop="versionPolicy">
          <el-select
            v-model="form.versionPolicy"
            :placeholder="$t('registrationAuthority.wizard.versionPolicyPlaceholder')"
            style="width: 100%;"
            :disabled="form.versionInputMode === 'MANUAL_ONLY'"
          >
            <el-option :label="$t('registrationAuthority.wizard.policySemver')" value="SEMVER" />
            <el-option :label="$t('registrationAuthority.wizard.policySequential')" value="SEQUENTIAL" />
            <el-option :label="$t('registrationAuthority.wizard.policyDate')" value="DATE" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button v-if="currentStep === 1" data-testid="ra-wizard-prev" @click="prevStep">
        {{ $t('registrationAuthority.wizard.prev') }}
      </el-button>
      <el-button
        v-if="currentStep === 0"
        type="primary"
        data-testid="ra-wizard-next"
        @click="nextStep"
      >
        {{ $t('registrationAuthority.wizard.next') }}
      </el-button>
      <el-button
        v-else
        type="primary"
        :loading="saving"
        data-testid="ra-wizard-submit"
        @click="handleSubmit"
      >
        {{ $t('registrationAuthority.wizard.submit') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { registrationAuthorityApi } from '@/api/resources';
import SegmentTemplateDesigner from '@/components/identifier/SegmentTemplateDesigner.vue';

interface Props {
  /** 部署版本（PERSONAL 时注册人员恒成立且无开关） */
  edition: string | null;
}

const props = defineProps<Props>();
const emit = defineEmits<{ success: [] }>();

const { t } = useI18n();

const visible = ref(false);
const saving = ref(false);
const currentStep = ref(0);
const step1FormRef = ref<FormInstance>();
const step2FormRef = ref<FormInstance>();

const defaultIdentifierTemplate = JSON.stringify({
  segments: [
    { type: 'OBJECT_ABBREVIATION', name: 'objectAbbr', params: {} },
    { type: 'SEPARATOR', params: { char: '.' } },
    { type: 'SEQUENCE', name: 'seq', params: { start: 1, increment: 1, pad: 5, overflow: 'REJECT' } },
  ],
});

function buildInitialForm() {
  return {
    internationalCodeDesignator: '',
    organizationIdentifier: '',
    organizationPartIdentifier: '',
    name: '',
    englishName: '',
    mailAddress: '',
    emailAddress: [] as string[],
    phoneNumber: [] as string[],
    webAddress: '',
    // 个人版后端强制 selfAsRegistrar=true，前端仅作展示；团队版由开关控制（缺省开）
    selfAsRegistrar: true,
    createNamespace: false,
    namespaceName: '默认命名空间',
    identifierInputMode: 'AUTO_WITH_OVERRIDE',
    versionInputMode: 'AUTO_WITH_OVERRIDE',
    identifierTemplate: defaultIdentifierTemplate,
    versionPolicy: 'SEMVER',
  };
}

const form = reactive(buildInitialForm());

const step1Rules = computed<FormRules>(() => ({
  internationalCodeDesignator: [
    { required: true, message: t('registrationAuthority.wizard.icdRequired'), trigger: 'blur' },
  ],
  organizationIdentifier: [
    { required: true, message: t('registrationAuthority.wizard.orgIdRequired'), trigger: 'blur' },
  ],
}));

/**
 * 标识模板校验：手动录入模式下豁免；自动模式下模板必须可解析，
 * 且恰好包含一个唯一性段（顺序号/时间戳/UUID）。与初始化向导规则一致。
 */
function validateIdentifierTemplate(rule: unknown, value: unknown, callback: (error?: Error) => void) {
  if (form.identifierInputMode === 'MANUAL_ONLY') {
    callback();
    return;
  }
  if (!value) {
    callback(new Error(t('registrationAuthority.wizard.templateRequired')));
    return;
  }
  try {
    const parsed = JSON.parse(value as string);
    const segs: Array<{ type: string }> = parsed.segments || [];
    const uniqueness = segs.filter((s) => ['SEQUENCE', 'TIMESTAMP', 'UUID'].includes(s.type));
    if (uniqueness.length !== 1) {
      callback(new Error(t('registrationAuthority.wizard.templateUniqueness')));
      return;
    }
    callback();
  } catch {
    callback(new Error(t('registrationAuthority.wizard.templateInvalid')));
  }
}

const step2Rules = computed<FormRules>(() => ({
  namespaceName: [
    { required: true, message: t('registrationAuthority.wizard.nsNameRequired'), trigger: 'blur' },
    { min: 2, max: 100, message: t('registrationAuthority.wizard.nsNameLength'), trigger: 'blur' },
  ],
  identifierTemplate: [
    { validator: validateIdentifierTemplate, trigger: 'change' },
  ],
  versionPolicy: [
    { required: true, message: t('registrationAuthority.wizard.versionPolicyRequired'), trigger: 'change' },
  ],
}));

function resetState() {
  Object.assign(form, buildInitialForm());
  currentStep.value = 0;
}

function open() {
  resetState();
  visible.value = true;
}

async function nextStep() {
  if (!step1FormRef.value) return;
  await step1FormRef.value.validate((valid) => {
    if (valid) currentStep.value = 1;
  });
}

function prevStep() {
  currentStep.value = 0;
}

function buildPayload() {
  const payload: Record<string, unknown> = {
    selfAsRegistrar: props.edition === 'PERSONAL' ? true : form.selfAsRegistrar,
    createNamespace: form.createNamespace,
  };
  const optionalStrings: Array<[string, string]> = [
    ['internationalCodeDesignator', form.internationalCodeDesignator],
    ['organizationIdentifier', form.organizationIdentifier],
    ['organizationPartIdentifier', form.organizationPartIdentifier],
    ['name', form.name],
    ['englishName', form.englishName],
    ['mailAddress', form.mailAddress],
    ['webAddress', form.webAddress],
  ];
  for (const [key, value] of optionalStrings) {
    if (value) payload[key] = value;
  }
  if (form.emailAddress.length) payload.emailAddress = [...form.emailAddress];
  if (form.phoneNumber.length) payload.phoneNumber = [...form.phoneNumber];
  if (form.createNamespace) {
    payload.namespaceName = form.namespaceName;
    payload.identifierInputMode = form.identifierInputMode;
    payload.versionInputMode = form.versionInputMode;
    payload.versionPolicy = form.versionPolicy;
    payload.identifierTemplate = form.identifierTemplate;
  }
  return payload;
}

async function handleSubmit() {
  if (form.createNamespace && step2FormRef.value) {
    const valid = await step2FormRef.value.validate().catch(() => false);
    if (!valid) return;
  }
  saving.value = true;
  try {
    await registrationAuthorityApi.create(buildPayload());
    ElMessage.success(t('registrationAuthority.wizard.createSuccess'));
    visible.value = false;
    emit('success');
  } catch (err) {
    // request 拦截器已展示错误
    console.error('创建注册机构失败', err);
  } finally {
    saving.value = false;
  }
}

defineExpose({ open });
</script>
