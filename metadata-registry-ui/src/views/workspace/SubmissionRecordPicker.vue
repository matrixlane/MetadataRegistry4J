<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="480px"
    :close-on-click-modal="false"
    @open="onOpen"
    @closed="reset"
  >
    <template v-if="hasIdentities">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <el-form-item :label="$t('itemForm.submissionInfo.organization')" prop="organizationId">
          <el-select
            v-model="form.organizationId"
            :placeholder="$t('itemForm.submissionInfo.organizationPlaceholder')"
            style="width: 100%;"
            data-testid="submission-organization-select"
          >
            <el-option
              v-for="org in organizations"
              :key="org.organizationId"
              :label="org.organizationName || org.organizationId"
              :value="org.organizationId"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('itemForm.submissionInfo.contact')">
          <el-input
            :model-value="contactDisplay"
            readonly
            :placeholder="$t('workspace.submissionContactAuto')"
            data-testid="submission-contact-display"
          />
        </el-form-item>
      </el-form>
    </template>
    <template v-else>
      <div class="no-identity-tip" data-testid="submission-no-identity">
        <p>{{ $t('workspace.submissionIdentityRequired') }}</p>
        <p class="no-identity-hint">{{ $t('workspace.submissionIdentityRequiredHint') }}</p>
      </div>
    </template>
    <template #footer>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button
        v-if="hasIdentities"
        type="primary"
        :loading="submitting"
        data-testid="submission-confirm"
        @click="confirm"
      >
        {{ $t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { myContactApi } from '@/api/resources';
import { ref, computed, reactive } from 'vue';
import { useI18n } from 'vue-i18n';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { useActiveRegistrar } from '@/composables/useActiveRegistrar';
const activeRegistrarState = useActiveRegistrar();
import type { RegistrarIdentity, MyContactResponse, SubmissionInfo } from '@/types/common';

const { t } = useI18n();

interface Props {
  title?: string;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  confirm: [submission: SubmissionInfo];
}>();

const visible = ref(false);
const submitting = ref(false);
const contacts = ref<MyContactResponse[]>([]);
const contactsLoaded = ref(false);
/** 活动 Registrar 身份（active-registrar-ownership）；每次打开时重新拉取 */
const activeRegistrar = ref<RegistrarIdentity | null>(null);
const formRef = ref<FormInstance>();
const form = reactive({
  organizationId: '',
});

interface OrgOption {
  organizationId: string;
  organizationName?: string;
}

/** 提交组织选项 = 当前账号身份所属组织去重（value=organizationId, label=organizationName） */
const organizations = computed<OrgOption[]>(() => {
  const map = new Map<string, string | undefined>();
  for (const contact of contacts.value || []) {
    if (contact.organizationId && !map.has(contact.organizationId)) {
      map.set(contact.organizationId, contact.organizationName);
    }
  }
  return Array.from(map, ([organizationId, organizationName]) => ({ organizationId, organizationName }));
});

/** 无任何可用身份（或身份均无组织）时不显示表单，显示提示区 */
const hasIdentities = computed(() => organizations.value.length > 0);

/** 所选组织下当前账号的本人身份（默认身份优先） */
const selectedIdentity = computed<MyContactResponse | undefined>(() => {
  const list = (contacts.value || []).filter((c) => c.organizationId === form.organizationId);
  return list.find((c) => c.isDefault) ?? list[0];
});

const contactDisplay = computed(() => {
  const identity = selectedIdentity.value;
  if (!identity) return '';
  if (identity.individualName && identity.organizationName) {
    return `${identity.individualName}（${identity.organizationName}）`;
  }
  return identity.individualName || identity.organizationName || '';
});

const title = computed(() => props.title || t('itemForm.submissionInfo.title'));

const rules = computed<FormRules>(() => ({
  organizationId: [
    { required: true, message: t('workspace.submissionOrganizationRequired'), trigger: 'change' },
  ],
}));

async function loadContacts() {
  if (contactsLoaded.value) return;
  try {
    const res = await myContactApi.list();
    contacts.value = Array.isArray(res) ? res : [];
  } catch {
    contacts.value = [];
    ElMessage.error(t('workspace.loadContactsError'));
  } finally {
    contactsLoaded.value = true;
  }
}

async function loadActiveRegistrar() {
  try {
    await activeRegistrarState.load();
    activeRegistrar.value = activeRegistrarState.active;
  } catch {
    activeRegistrar.value = null;
  }
}

/**
 * 默认选中（D5）：活动 Registrar 身份组织 → 第一条 Registrar 身份组织 → 默认身份组织。
 */
function defaultOrganizationId(): string {
  const list = contacts.value || [];
  const activeRegistrarId = activeRegistrar.value?.registrarId;
  if (activeRegistrarId) {
    const activeIdentity = list.find((c) => c.id === activeRegistrarId && c.organizationId);
    if (activeIdentity) return activeIdentity.organizationId as string;
  }
  const registrarIdentity = list.find((c) => c.isRegistrar && c.registrationAuthorityId && c.organizationId);
  if (registrarIdentity) return registrarIdentity.organizationId as string;
  const defaultIdentity = list.find((c) => c.isDefault && c.organizationId);
  return (defaultIdentity ?? list.find((c) => c.organizationId))?.organizationId ?? '';
}

function onOpen() {
  reset();
  Promise.all([loadContacts(), loadActiveRegistrar()]).then(() => {
    form.organizationId = defaultOrganizationId();
  });
}

function reset() {
  form.organizationId = '';
  contactsLoaded.value = false;
  activeRegistrar.value = null;
}

function open() {
  visible.value = true;
}

async function confirm() {
  if (!formRef.value) return;
  await formRef.value.validate((valid) => {
    if (!valid) return;
    const identity = selectedIdentity.value;
    if (!identity) {
      ElMessage.error(t('workspace.selectContact'));
      return;
    }
    emit('confirm', {
      contactId: identity.id,
      organizationId: form.organizationId,
    });
  });
}

defineExpose({ open });
</script>

<style scoped>
.no-identity-tip {
  padding: 8px 4px;
  color: var(--el-text-color-regular);
}

.no-identity-hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
