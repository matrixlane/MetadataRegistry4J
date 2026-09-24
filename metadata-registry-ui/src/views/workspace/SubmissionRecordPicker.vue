<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="480px"
    :close-on-click-modal="false"
    @open="onOpen"
    @closed="reset"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
    >
      <el-form-item :label="$t('itemForm.submissionInfo.contact')" prop="contactId">
        <el-select
          v-model="form.contactId"
          :placeholder="$t('itemForm.submissionInfo.contactPlaceholder')"
          style="width: 100%;"
          data-testid="submission-contact-select"
          @change="onContactChange"
        >
          <el-option
            v-for="contact in contacts"
            :key="contact.id"
            :label="contactLabel(contact)"
            :value="contact.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('itemForm.submissionInfo.organization')">
        <el-input
          :model-value="organizationDisplay"
          readonly
          :placeholder="$t('workspace.organizationAuto')"
          data-testid="submission-organization-display"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" data-testid="submission-confirm" @click="confirm">
        {{ $t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue';
import { useI18n } from 'vue-i18n';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { myContactApi } from '@/api/resources';
import type { MyContactResponse, SubmissionInfo } from '@/types/common';

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
const formRef = ref<FormInstance>();
const form = reactive({
  contactId: '',
});

const organizationDisplay = computed(() => {
  const contact = (contacts.value || []).find((c) => c.id === form.contactId);
  return contact?.organizationName || t('workspace.organizationAuto');
});

const title = computed(() => props.title || t('itemForm.submissionInfo.title'));

const rules = computed<FormRules>(() => ({
  contactId: [
    { required: true, message: t('workspace.contactRequired'), trigger: 'change' },
  ],
}));

function contactLabel(contact: MyContactResponse): string {
  const parts = [
    contact.individualName || contact.organizationName,
    contact.organizationName && contact.individualName ? `(${contact.organizationName})` : undefined,
    contact.roleName,
  ].filter(Boolean);
  const label = parts.join(' · ') || contact.id;
  return contact.isDefault ? `${label} (${t('contactIdentity.default')})` : label;
}

function onContactChange(contactId: string) {
  const contact = (contacts.value || []).find((c) => c.id === contactId);
  if (contact && !contact.isRegistrar) {
    // 联系人为本账号绑定的非 Registrar 联系人，自动推导其所属组织
  }
}

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

function prefillDefaultContact() {
  const list = contacts.value || [];
  const defaultContact = list.find((c) => c.isDefault);
  form.contactId = defaultContact?.id || (list[0]?.id ?? '');
}

function onOpen() {
  reset();
  loadContacts().then(() => {
    prefillDefaultContact();
  });
}

function reset() {
  form.contactId = '';
  contactsLoaded.value = false;
}

function open() {
  visible.value = true;
}

async function confirm() {
  if (!formRef.value) return;
  await formRef.value.validate((valid) => {
    if (!valid) return;
    const contact = (contacts.value || []).find((c) => c.id === form.contactId);
    if (!contact) {
      ElMessage.error(t('workspace.selectContact'));
      return;
    }
    emit('confirm', {
      contactId: contact.id,
      organizationId: contact.organizationId,
    });
  });
}

defineExpose({ open });
</script>
