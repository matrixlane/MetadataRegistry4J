<template>
  <div class="profile-dashboard">
    <!-- Top Profile Banner -->
    <div class="profile-header-banner">
      <div class="banner-gradient">
        <!-- Floating decorative blobs for modern visual depth -->
        <div class="blob blob-1"></div>
        <div class="blob blob-2"></div>
        <div class="blob blob-3"></div>
      </div>
      <div class="banner-content">
        <div class="avatar-wrapper" @click="triggerFilePick" v-loading="avatarUploading" element-loading-background="rgba(255,255,255,0.6)">
          <el-avatar :size="110" class="profile-avatar" :src="avatarDataUrl || undefined" icon="UserFilled" />
          <div class="avatar-hover-overlay">
            <el-icon><Camera /></el-icon>
          </div>
          <input ref="fileInputRef" type="file" accept="image/png,image/jpeg,image/webp" style="display: none" @change="handleFileChange" />
        </div>
        <div class="profile-title-info">
          <div class="name-row">
            <h2>{{ form.name || auth.user?.displayName || auth.user?.username }}</h2>
            <el-tag size="small" type="primary" effect="dark" class="role-badge">
              <el-icon><UserFilled /></el-icon> {{ auth.user?.sysRole === 'SUPER_ADMIN' ? '超级管理员' : (auth.user?.isRegistrar ? '注册管理员' : '普通账户') }}
            </el-tag>
          </div>
          <p class="title-subtext" v-if="form.title || auth.user?.username">
            <el-icon class="title-icon"><Briefcase /></el-icon>
            {{ form.title || '暂无职位信息' }}
          </p>
          <div class="meta-tags">
            <span class="email-meta" v-if="accountEmail">
              <el-icon><Message /></el-icon> {{ accountEmail }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Columns -->
    <el-row :gutter="24" class="profile-body-row">
      <!-- Left Column: User Summary & Stats -->
      <el-col :span="8" :xs="24">
        <div class="left-column-wrapper">
          <!-- Profile Completeness Card -->
          <el-card class="info-card info-card-premium" shadow="hover">
            <template #header>
              <div class="card-title-icon">
                <el-icon class="icon-gradient"><Compass /></el-icon>
                <span>资料完整度</span>
              </div>
            </template>
            <div class="completeness-body">
              <div class="progress-wrapper">
                <el-progress 
                  type="dashboard" 
                  :percentage="completenessPercentage" 
                  :color="progressColors"
                  :stroke-width="8"
                  :width="120"
                />
              </div>
              <div class="completeness-status">
                <span class="completeness-label">{{ completenessLabel }}</span>
                <span class="completeness-tip">完善个人资料有助于提升工作协同效率</span>
              </div>
            </div>
          </el-card>

          <!-- Account Details Card -->
          <el-card class="info-card info-card-premium" shadow="hover">
            <template #header>
              <div class="card-title-icon">
                <el-icon class="icon-gradient"><InfoFilled /></el-icon>
                <span>账号状态</span>
              </div>
            </template>
            <div class="detail-list">
              <div class="detail-item">
                <div class="detail-label-icon">
                  <el-icon><Key /></el-icon>
                  <span class="lbl">登录用户名</span>
                </div>
                <span class="val">{{ auth.user?.username }}</span>
              </div>
              <div class="detail-item">
                <div class="detail-label-icon">
                  <el-icon><Message /></el-icon>
                  <span class="lbl">账户邮箱</span>
                </div>
                <span class="val">{{ accountEmail || '未绑定' }}</span>
              </div>
              <div class="detail-item">
                <div class="detail-label-icon">
                  <el-icon><Calendar /></el-icon>
                  <span class="lbl">创建时间</span>
                </div>
                <span class="val">{{ formatDate(auth.user?.createdAt) }}</span>
              </div>
              <div class="detail-item">
                <div class="detail-label-icon">
                  <el-icon><Clock /></el-icon>
                  <span class="lbl">最后登录</span>
                </div>
                <span class="val">{{ formatDate(auth.user?.lastLoginAt) }}</span>
              </div>
            </div>
          </el-card>
        </div>
      </el-col>

      <!-- Right Column: Profile Editor Form -->
      <el-col :span="16" :xs="24">
        <el-card class="form-card form-card-premium" shadow="hover">
          <template #header>
            <div class="card-title-icon">
              <el-icon class="icon-gradient"><EditPen /></el-icon>
              <span>编辑个人资料</span>
            </div>
          </template>
          
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            v-loading="loading"
            class="premium-form"
          >
            <el-tabs v-model="activeTab" class="profile-tabs">
              <el-tab-pane label="基本资料" name="basic">
                <div class="tab-pane-content">
                  <el-row :gutter="20">
                    <el-col :span="12" :xs="24">
                      <el-form-item label="真实姓名" prop="name" required>
                        <el-input v-model="form.name" placeholder="请输入您的姓名" :prefix-icon="User" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12" :xs="24">
                      <el-form-item label="职务/头衔" prop="title">
                        <el-input v-model="form.title" placeholder="如：系统架构师、数据治理专家" :prefix-icon="Briefcase" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  
                  <el-form-item label="邮寄地址" prop="mailAddress">
                    <el-input 
                      v-model="form.mailAddress" 
                      placeholder="请输入您的详细邮寄地址" 
                      :prefix-icon="Location" 
                      type="textarea"
                      :rows="2"
                    />
                  </el-form-item>
                </div>
              </el-tab-pane>

              <el-tab-pane label="电子邮箱" name="emails">
                <div class="tab-pane-content">
                  <div class="section-desc">您可以关联多个工作或个人邮箱地址</div>
                  <el-form-item>
                    <transition-group name="list" tag="div" class="dynamic-rows-container">
                      <div v-for="(email, index) in form.emailAddress" :key="index" class="dynamic-row">
                        <el-input v-model="form.emailAddress[index]" placeholder="请输入邮箱地址" :prefix-icon="Message">
                          <template #append>
                            <el-button icon="Delete" @click="removeEmail(index)" class="delete-btn-icon" />
                          </template>
                        </el-input>
                      </div>
                    </transition-group>
                    <el-button type="primary" plain size="small" icon="Plus" @click="addEmail" class="add-btn-link">
                      添加新邮箱
                    </el-button>
                  </el-form-item>
                </div>
              </el-tab-pane>

              <el-tab-pane label="联系电话" name="phones">
                <div class="tab-pane-content">
                  <div class="section-desc">配置多组联系电话以方便沟通</div>
                  <el-form-item>
                    <transition-group name="list" tag="div" class="dynamic-rows-container">
                      <div v-for="(phone, index) in form.phoneNumber" :key="index" class="dynamic-row">
                        <el-input v-model="form.phoneNumber[index]" placeholder="请输入电话号码" :prefix-icon="Phone">
                          <template #append>
                            <el-button icon="Delete" @click="removePhone(index)" class="delete-btn-icon" />
                          </template>
                        </el-input>
                      </div>
                    </transition-group>
                    <el-button type="primary" plain size="small" icon="Plus" @click="addPhone" class="add-btn-link">
                      添加新电话
                    </el-button>
                  </el-form-item>
                </div>
              </el-tab-pane>
            </el-tabs>

            <div class="form-actions-footer">
              <el-button type="primary" @click="handleSave" :loading="saving" class="save-button-primary">
                <el-icon><Check /></el-icon> 保存修改
              </el-button>
            </div>
          </el-form>
        </el-card>

        <!-- 联系人身份管理 -->
        <el-card class="form-card form-card-premium contact-identity-card" shadow="hover" style="margin-top: 24px;">
          <template #header>
            <div class="card-title-icon">
              <el-icon class="icon-gradient"><OfficeBuilding /></el-icon>
              <span>{{ $t('contactIdentity.title') }}</span>
            </div>
          </template>

          <div v-loading="contactsLoading" class="contact-identity-body">
            <div v-if="contacts.length === 0" class="contact-empty">
              {{ $t('contactIdentity.empty') }}
            </div>

            <div v-else class="contact-list">
              <div
                v-for="contact in contacts"
                :key="contact.id"
                class="contact-item"
                :class="{ 'is-default': contact.isDefault }"
                data-testid="contact-identity-item"
              >
                <div class="contact-main">
                  <div class="contact-name">
                    <el-icon><User /></el-icon>
                    <span>{{ contact.isRegistrar ? $t('contactIdentity.registrar') : $t('contactIdentity.contact') }}</span>
                    <el-tag v-if="contact.isDefault" size="small" type="success" effect="dark" data-testid="contact-default-badge">
                      {{ $t('contactIdentity.default') }}
                    </el-tag>
                  </div>
                  <div class="contact-meta">
                    <span v-if="contact.organizationName">
                      <el-icon><OfficeBuilding /></el-icon> {{ contact.organizationName }}
                    </span>
                    <span v-if="contact.roleName">
                      <el-icon><Postcard /></el-icon> {{ contact.roleName }}
                    </span>
                    <span v-if="contact.registrationAuthorityName && contact.registrationAuthorityName !== contact.organizationName">
                      <el-icon><Medal /></el-icon> {{ contact.registrationAuthorityName }}
                    </span>
                  </div>
                </div>
                <div class="contact-actions">
                  <el-button
                    link
                    type="primary"
                    size="small"
                    data-testid="contact-edit-btn"
                    @click="openContactDialog(contact)"
                  >
                    {{ $t('common.edit') }}
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    size="small"
                    data-testid="contact-delete-btn"
                    @click="deleteContact(contact)"
                  >
                    {{ $t('common.delete') }}
                  </el-button>
                  <el-button
                    v-if="!contact.isDefault"
                    link
                    type="success"
                    size="small"
                    data-testid="contact-set-default-btn"
                    @click="setDefaultContact(contact.id)"
                  >
                    {{ $t('contactIdentity.setDefault') }}
                  </el-button>
                </div>
              </div>
            </div>

            <div class="contact-add">
              <el-button type="primary" plain size="small" data-testid="contact-add-btn" @click="openContactDialog()">
                <el-icon><Plus /></el-icon> {{ $t('contactIdentity.add') }}
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 联系人身份新建/编辑弹窗 -->
    <el-dialog
      v-model="contactDialogVisible"
      :title="editingContact ? $t('contactIdentity.edit') : $t('contactIdentity.add')"
      width="520px"
      :close-on-click-modal="false"
      data-testid="contact-dialog"
    >
      <el-form
        ref="contactFormRef"
        :model="contactForm"
        :rules="contactRules"
        label-position="top"
      >
        <el-form-item :label="$t('contactIdentity.organization')" prop="organizationId">
          <el-select
            v-model="contactForm.organizationId"
            :placeholder="$t('contactIdentity.organizationPlaceholder')"
            :disabled="editingContact?.isRegistrar === true"
            filterable
            clearable
            style="width: 100%;"
            data-testid="contact-organization-select"
          >
            <template #label="{ label, value }">
              <span v-if="isPendingOption(value, organizationOptions, organizationsLoaded)">…</span>
              <span v-else-if="isUnresolvedOption(value, organizationOptions)">{{ $t('common.unresolvedEntry') }}</span>
              <span v-else>{{ label }}</span>
            </template>
            <el-option
              v-for="org in organizationOptions"
              :key="org.id"
              :label="org.label"
              :value="org.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('contactIdentity.role')">
          <el-select
            v-model="contactForm.roleId"
            :placeholder="$t('contactIdentity.rolePlaceholder')"
            filterable
            clearable
            style="width: 100%;"
            data-testid="contact-role-select"
          >
            <template #label="{ label, value }">
              <span v-if="isPendingOption(value, roleOptions, rolesLoaded)">…</span>
              <span v-else-if="isUnresolvedOption(value, roleOptions)">{{ $t('common.unresolvedEntry') }}</span>
              <span v-else>{{ label }}</span>
            </template>
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="contactSaving" data-testid="contact-save-btn" @click="saveContact">
          {{ $t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useAuthStore } from '@/stores/auth';
import request from '@/utils/request';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import {
  User,
  Briefcase,
  Postcard,
  Location,
  Phone,
  Message,
  Plus,
  InfoFilled,
  EditPen,
  Compass,
  Key,
  Calendar,
  Clock,
  Check,
  Camera,
  OfficeBuilding,
  Medal,
} from '@element-plus/icons-vue';
import { myContactApi, organizationApi, roleApi } from '@/api/resources';
import type { MyContactResponse } from '@/types/common';

const auth = useAuthStore();
const { t } = useI18n();
const formRef = ref<FormInstance>();
const loading = ref(false);
const saving = ref(false);
const activeTab = ref('basic');
const avatarDataUrl = ref<string>('');
const avatarUploading = ref(false);
const fileInputRef = ref<HTMLInputElement>();

// ── Contact Identity Management ─────────────────────────────────────
const contacts = ref<MyContactResponse[]>([]);
const contactsLoading = ref(false);
const contactDialogVisible = ref(false);
const contactSaving = ref(false);
const editingContact = ref<MyContactResponse | null>(null);
const contactFormRef = ref<FormInstance>();
const contactForm = reactive({
  organizationId: '',
  roleId: undefined as string | undefined,
});
const organizationOptions = ref<{ id: string; label: string }[]>([]);
const roleOptions = ref<{ id: string; roleName: string }[]>([]);
const organizationsLoaded = ref(false);
const rolesLoaded = ref(false);

/** 选项加载完成前，未命中的选中值显示占位而非原始 id */
function isPendingOption(value: unknown, options: { id: string }[], done: boolean): boolean {
  if (!value || typeof value !== 'string') return false;
  if (done) return false;
  return !options.some((o) => o.id === value);
}

/** 加载完成后仍不在选项中（如组织已被删除）：永不回显原始 id */
function isUnresolvedOption(value: unknown, options: { id: string }[]): boolean {
  if (!value || typeof value !== 'string') return false;
  return !options.some((o) => o.id === value);
}

const contactRules = reactive<FormRules>({
  organizationId: [{ required: true, message: t('contactIdentity.organizationRequired'), trigger: 'change' }],
});

async function loadContacts() {
  contactsLoading.value = true;
  try {
    contacts.value = await myContactApi.list();
  } catch {
    // 全局响应拦截器已提示错误
    contacts.value = [];
  } finally {
    contactsLoading.value = false;
  }
}

async function loadOrganizations() {
  try {
    // /organizations/options 不分页，避免 list(size:200) 截断导致部分组织无法解析
    const opts = await organizationApi.options();
    organizationOptions.value = (opts || []).map((o) => ({
      id: o.id,
      label: o.name || '—',
    }));
  } catch {
    organizationOptions.value = [];
  } finally {
    organizationsLoaded.value = true;
  }
}

async function loadRoles() {
  try {
    roleOptions.value = await roleApi.options();
  } catch {
    roleOptions.value = [];
  } finally {
    rolesLoaded.value = true;
  }
}

function openContactDialog(contact?: MyContactResponse) {
  editingContact.value = contact || null;
  contactForm.organizationId = contact?.organizationId || '';
  contactForm.roleId = contact?.roleId || undefined;
  contactDialogVisible.value = true;
}

async function saveContact() {
  if (!contactFormRef.value) return;
  await contactFormRef.value.validate(async (valid) => {
    if (!valid) return;
    contactSaving.value = true;
    try {
      const payload = {
        organizationId: contactForm.organizationId,
        roleId: contactForm.roleId || undefined,
      };
      if (editingContact.value) {
        await myContactApi.update(editingContact.value.id, payload);
      } else {
        await myContactApi.create(payload);
      }
      ElMessage.success(t('contactIdentity.saveSuccess'));
      contactDialogVisible.value = false;
      await loadContacts();
      await auth.fetchMe();
    } catch {
      // 全局响应拦截器已提示错误
    } finally {
      contactSaving.value = false;
    }
  });
}

async function deleteContact(contact: MyContactResponse) {
  try {
    await ElMessageBox.confirm(
      t('contactIdentity.deleteConfirmMessage', {
        org: contact.organizationName || t('common.unknownEntry'),
        kind: contact.isRegistrar ? t('contactIdentity.registrar') : t('contactIdentity.contact'),
      }),
      t('contactIdentity.deleteConfirmTitle'),
      { type: 'warning' }
    );
    await myContactApi.delete(contact.id);
    ElMessage.success(t('contactIdentity.deleteSuccess'));
    await loadContacts();
    await auth.fetchMe();
  } catch (e: unknown) {
    if (e !== 'cancel') {
      // 全局响应拦截器已提示错误
    }
  }
}

async function setDefaultContact(id: string) {
  try {
    await myContactApi.setDefault(id);
    ElMessage.success(t('contactIdentity.setDefaultSuccess'));
    await loadContacts();
  } catch {
    // 全局响应拦截器已提示错误
  }
}

// ── Profile form (existing) ─────────────────────────────────────────

const AVATAR_MAX_BYTES = 2 * 1024 * 1024;

async function loadAvatar() {
  try {
    const res = await request.get<{ dataUrl?: string }>('/auth/me/avatar');
    avatarDataUrl.value = res?.dataUrl || '';
  } catch {
    avatarDataUrl.value = '';
  }
}

function triggerFilePick() {
  fileInputRef.value?.click();
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (file.size > AVATAR_MAX_BYTES) {
    ElMessage.error('头像大小不能超过 2MB');
    input.value = '';
    return;
  }
  const reader = new FileReader();
  reader.onload = async () => {
    avatarUploading.value = true;
    try {
      await request.put('/auth/me/avatar', { dataUrl: reader.result });
      avatarDataUrl.value = reader.result as string;
      ElMessage.success('头像已更新');
    } catch {
      // 全局拦截器已提示错误原因，此处仅终止流程、保留原头像
    } finally {
      avatarUploading.value = false;
      input.value = '';
    }
  };
  reader.onerror = () => {
    ElMessage.error('读取文件失败');
    input.value = '';
  };
  reader.readAsDataURL(file);
}

const form = reactive({
  name: '',
  title: '',
  mailAddress: '',
  emailAddress: [] as string[],
  phoneNumber: [] as string[],
});

// 账户邮箱与个人资料中的电子邮箱保持同步：取资料中第一个已填写的邮箱
const accountEmail = computed(() => {
  const first = form.emailAddress.find(e => e && e.trim() !== '');
  return (first && first.trim()) || auth.user?.email || '';
});

const rules = reactive<FormRules>({
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
});

// Calculate completeness percentage based on filled data fields
const completenessPercentage = computed(() => {
  let pct = 0;
  if (form.name && form.name.trim()) pct += 30;
  if (form.title && form.title.trim()) pct += 20;
  if (form.mailAddress && form.mailAddress.trim()) pct += 20;
  if (form.emailAddress && form.emailAddress.some(e => e && e.trim())) pct += 15;
  if (form.phoneNumber && form.phoneNumber.some(p => p && p.trim())) pct += 15;
  return pct;
});

const completenessLabel = computed(() => {
  const pct = completenessPercentage.value;
  if (pct === 0) return '尚未完善资料';
  if (pct < 40) return '资料较为简单';
  if (pct < 70) return '资料基本完整';
  if (pct < 100) return '资料非常完善';
  return '资料完美无缺';
});

const progressColors = [
  { color: '#ef4444', percentage: 30 },
  { color: '#f59e0b', percentage: 60 },
  { color: '#16a34a', percentage: 90 },
  { color: '#059669', percentage: 100 },
];

async function loadProfile() {
  loading.value = true;
  try {
    const res = await request.get<{
      name?: string;
      title?: string;
      mailAddress?: string;
      emailAddress?: string[];
      phoneNumber?: string[];
    }>('/auth/me/individual');
    if (res) {
      form.name = res.name || '';
      form.title = res.title || '';
      form.mailAddress = res.mailAddress || '';
      form.emailAddress = res.emailAddress || [];
      form.phoneNumber = res.phoneNumber || [];
    }
  } catch {
    ElMessage.error('加载个人信息失败');
  } finally {
    loading.value = false;
  }
}

function addEmail() {
  form.emailAddress.push('');
}

function removeEmail(index: number) {
  form.emailAddress.splice(index, 1);
}

function addPhone() {
  form.phoneNumber.push('');
}

function removePhone(index: number) {
  form.phoneNumber.splice(index, 1);
}

async function handleSave() {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true;
      try {
        const payload = {
          ...form,
          emailAddress: form.emailAddress.filter(e => e && e.trim() !== ''),
          phoneNumber: form.phoneNumber.filter(p => p && p.trim() !== ''),
        };
        await request.put('/auth/me/individual', payload);
        ElMessage.success('保存成功');
        await loadProfile();
        await auth.fetchMe();
      } catch {
        ElMessage.error('保存失败');
      } finally {
        saving.value = false;
      }
    }
  });
}

function formatDate(val: string | null | undefined) {
  if (!val) return '-';
  const d = new Date(val);
  return d.toLocaleString('zh-CN', { hour12: false });
}

onMounted(() => {
  loadProfile();
  loadAvatar();
  loadContacts();
  loadOrganizations();
  loadRoles();
});
</script>

<style scoped lang="scss">
.profile-dashboard {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
  padding: 8px 0;

  /* Header Banner */
  .profile-header-banner {
    position: relative;
    background: #ffffff;
    border-radius: 20px;
    overflow: hidden;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.04);
    border: 1px solid rgba(226, 232, 240, 0.8);
    transition: transform 0.3s ease, box-shadow 0.3s ease;

    &:hover {
      box-shadow: 0 15px 40px rgba(0, 0, 0, 0.08);
    }

    .banner-gradient {
      height: 160px;
      position: relative;
      background: linear-gradient(135deg, #14301f 0%, #16a34a 40%, #059669 100%);
      overflow: hidden;

      /* Decorative Blobs */
      .blob {
        position: absolute;
        border-radius: 50%;
        filter: blur(40px);
        opacity: 0.45;
        animation: float-blobs 12s infinite alternate ease-in-out;
      }

      .blob-1 {
        width: 140px;
        height: 140px;
        background: #ec4899;
        top: -20px;
        left: 20%;
      }

      .blob-2 {
        width: 180px;
        height: 180px;
        background: #8b5cf6;
        bottom: -50px;
        right: 15%;
        animation-delay: -4s;
      }

      .blob-3 {
        width: 100px;
        height: 100px;
        background: #f59e0b;
        top: 30px;
        right: 40%;
        animation-delay: -8s;
      }
    }

    .banner-content {
      display: flex;
      align-items: flex-end;
      padding: 0 40px 30px;
      gap: 28px;
      position: relative;
      z-index: 10;

      .avatar-wrapper {
        position: relative;
        border-radius: 50%;
        cursor: pointer;
        margin-top: -60px;
        
        .profile-avatar {
          border: 5px solid #ffffff;
          box-shadow: 0 8px 24px rgba(22, 163, 74, 0.15);
          background: #f1f5f9;
          color: #94a3b8;
          transition: transform 0.3s ease;
        }

        .avatar-hover-overlay {
          position: absolute;
          top: 5px;
          left: 5px;
          right: 5px;
          bottom: 5px;
          border-radius: 50%;
          background: rgba(15, 23, 42, 0.4);
          display: flex;
          align-items: center;
          justify-content: center;
          opacity: 0;
          transition: opacity 0.3s ease;
          color: #ffffff;
          font-size: 20px;
        }

        &:hover {
          .profile-avatar {
            transform: scale(1.03);
          }
          .avatar-hover-overlay {
            opacity: 1;
          }
        }
      }

      .profile-title-info {
        flex: 1;
        padding-bottom: 5px;

        .name-row {
          display: flex;
          align-items: center;
          gap: 12px;
          flex-wrap: wrap;

          h2 {
            margin: 0;
            font-size: 26px;
            font-weight: 800;
            color: #0f172a;
            letter-spacing: -0.5px;
          }

          .role-badge {
            font-weight: 600;
            letter-spacing: 0.5px;
            border-radius: 6px;
            display: flex;
            align-items: center;
            gap: 4px;
            padding: 4px 8px;
          }
        }

        .title-subtext {
          margin: 6px 0 0;
          font-size: 14px;
          color: #475569;
          display: flex;
          align-items: center;
          gap: 6px;
          font-weight: 500;

          .title-icon {
            color: #64748b;
          }
        }

        .meta-tags {
          margin-top: 12px;
          display: flex;
          align-items: center;
          gap: 16px;

          .email-meta {
            display: flex;
            align-items: center;
            gap: 6px;
            color: #64748b;
            font-size: 13px;
            background: #f8fafc;
            padding: 4px 10px;
            border-radius: 20px;
            border: 1px solid #f1f5f9;
          }
        }
      }
    }
  }

  /* Body Columns */
  .profile-body-row {
    .left-column-wrapper {
      display: flex;
      flex-direction: column;
      gap: 24px;
    }

    .info-card-premium, .form-card-premium {
      border-radius: 20px;
      border: 1px solid rgba(226, 232, 240, 0.8);
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.02);
      transition: all 0.3s ease;

      &:hover {
        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
      }

      .card-title-icon {
        display: flex;
        align-items: center;
        gap: 10px;
        font-weight: 700;
        color: #1e293b;
        font-size: 16px;
        
        .icon-gradient {
          color: var(--el-color-primary);
          font-size: 18px;
        }
      }
    }

    /* Completeness Card styles */
    .completeness-body {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 10px 0;
      text-align: center;

      .progress-wrapper {
        margin-bottom: 16px;
      }

      .completeness-status {
        display: flex;
        flex-direction: column;
        gap: 6px;

        .completeness-label {
          font-size: 16px;
          font-weight: 700;
          color: #0f172a;
        }

        .completeness-tip {
          font-size: 12px;
          color: #94a3b8;
          line-height: 1.5;
        }
      }
    }

    /* Details Card styles */
    .info-card {
      .detail-list {
        display: flex;
        flex-direction: column;
        gap: 18px;

        .detail-item {
          display: flex;
          justify-content: space-between;
          align-items: center;
          border-bottom: 1px dashed #e2e8f0;
          padding-bottom: 14px;

          &:last-child {
            border-bottom: none;
            padding-bottom: 0;
          }

          .detail-label-icon {
            display: flex;
            align-items: center;
            gap: 8px;
            color: #64748b;
            font-size: 13px;

            .el-icon {
              font-size: 15px;
            }

            .lbl {
              font-weight: 500;
            }
          }

          .val {
            font-size: 13px;
            color: #1e293b;
            font-weight: 600;
          }
        }
      }
    }

    /* Form Card styles */
    .form-card {
      .profile-tabs {
        margin-top: -10px;

        :deep(.el-tabs__item) {
          font-weight: 600;
          font-size: 15px;
          height: 48px;
          color: #64748b;
          
          &.is-active {
            color: var(--el-color-primary);
          }
        }

        :deep(.el-tabs__active-bar) {
          background-color: var(--el-color-primary);
          height: 3px;
          border-radius: 3px;
        }

        :deep(.el-tabs__nav-wrap::after) {
          background-color: #f1f5f9;
        }
      }

      .tab-pane-content {
        padding: 20px 4px 10px;

        .section-desc {
          font-size: 13px;
          color: #64748b;
          margin-bottom: 16px;
          font-weight: 500;
        }
      }

      .premium-form {
        :deep(.el-form-item__label) {
          font-weight: 600;
          color: #475569;
          padding-bottom: 6px;
        }

        :deep(.el-input__wrapper) {
          border-radius: 8px;
          box-shadow: 0 0 0 1px #e2e8f0 inset;
          padding: 8px 12px;
          transition: all 0.2s ease;

          &:hover {
            box-shadow: 0 0 0 1px #cbd5e1 inset;
          }

          &.is-focus {
            box-shadow: 0 0 0 1px #16a34a inset, 0 0 0 3px rgba(22, 163, 74, 0.15) !important;
          }
        }

        :deep(.el-textarea__inner) {
          border-radius: 8px;
          box-shadow: 0 0 0 1px #e2e8f0 inset;
          padding: 8px 12px;
          transition: all 0.2s ease;

          &:hover {
            box-shadow: 0 0 0 1px #cbd5e1 inset;
          }

          &:focus {
            box-shadow: 0 0 0 1px #16a34a inset, 0 0 0 3px rgba(22, 163, 74, 0.15) !important;
          }
        }

        .dynamic-rows-container {
          display: flex;
          flex-direction: column;
          gap: 12px;
          width: 100%;
          margin-bottom: 12px;
        }

        .dynamic-row {
          display: flex;
          align-items: center;
          width: 100%;

          :deep(.el-input-group__append) {
            background-color: transparent;
            box-shadow: none;
            border-left: none;
            padding: 0;
          }

          .delete-btn-icon {
            color: #ef4444;
            background: #fef2f2;
            border: 1px solid #fee2e2;
            height: 38px;
            width: 44px;
            flex: none;
            margin: 0 0 0 8px;
            border-top-left-radius: 8px;
            border-bottom-left-radius: 8px;
            transition: all 0.2s ease;

            &:hover {
              color: #ffffff;
              background: #ef4444;
              border-color: #ef4444;
            }
          }
        }

        .add-btn-link {
          border-radius: 8px;
          font-weight: 600;
          transition: all 0.2s ease;
        }

        .form-actions-footer {
          margin-top: 24px;
          padding-top: 20px;
          border-top: 1px solid #f1f5f9;
          display: flex;
          justify-content: flex-end;

          .save-button-primary {
            min-width: 140px;
            height: 42px;
            font-weight: 700;
            border-radius: 10px;
            box-shadow: 0 4px 12px rgba(22, 163, 74, 0.2);
            transition: all 0.2s ease;

            &:hover {
              transform: translateY(-1px);
              box-shadow: 0 6px 16px rgba(22, 163, 74, 0.35);
            }
          }
        }
      }
    }
  }
}

/* Animations */
@keyframes float-blobs {
  0% {
    transform: translateY(0px) scale(1) rotate(0deg);
  }
  100% {
    transform: translateY(-20px) scale(1.15) rotate(15deg);
  }
}

/* Transition Lists */
.list-enter-active,
.list-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
.list-enter-from {
  opacity: 0;
  transform: translateY(-10px);
}
.list-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.contact-identity-card {
  .contact-identity-body {
    .contact-empty {
      font-size: 14px;
      color: var(--el-text-color-secondary);
      text-align: center;
      padding: 24px 0;
    }

    .contact-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
      margin-bottom: 16px;

      .contact-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 14px 16px;
        border-radius: 12px;
        border: 1px solid #e2e8f0;
        background: #ffffff;
        transition: all 0.2s ease;

        &.is-default {
          border-color: var(--el-color-success);
          background: #f0fdf4;
        }

        .contact-main {
          display: flex;
          flex-direction: column;
          gap: 8px;

          .contact-name {
            display: flex;
            align-items: center;
            gap: 8px;
            font-weight: 600;
            font-size: 15px;
            color: #0f172a;

            .el-icon {
              color: var(--el-color-primary);
            }
          }

          .contact-meta {
            display: flex;
            flex-wrap: wrap;
            gap: 12px;
            font-size: 13px;
            color: #64748b;

            span {
              display: inline-flex;
              align-items: center;
              gap: 4px;
            }

            .el-icon {
              font-size: 14px;
            }
          }
        }

        .contact-actions {
          display: flex;
          align-items: center;
          gap: 4px;
        }
      }
    }

    .contact-add {
      display: flex;
      justify-content: flex-start;
    }
  }
}
</style>
