<template>
  <div class="app-layout">
    <header class="app-header">
      <div class="header-left">
        <img src="/favicon.svg" alt="logo" width="32" height="32" />
        <span class="header-title">{{ $t('app.name') }}</span>
      </div>
      <div class="header-right" data-testid="header-right">
        <el-tag v-if="auth.user" type="primary" size="small" effect="light" round style="cursor: pointer;" @click="goToProfile" data-testid="header-user-tag">
          <el-icon><UserFilled /></el-icon>
          {{ auth.user.displayName || auth.user.username }}
        </el-tag>
        <el-button text type="primary" size="small" @click="handleLogout" data-testid="header-logout-btn">
          <el-icon><SwitchButton /></el-icon>
          {{ $t('auth.logout') }}
        </el-button>
      </div>
    </header>
    <div class="app-body">
      <aside class="app-sidebar" :class="{ collapsed }">
        <div class="sidebar-header">
          <div class="sidebar-brand">
            <img src="/favicon.svg" width="32" height="32" alt="MDR" />
            <span v-if="!collapsed" class="brand-text">MDR</span>
          </div>
          <el-button
            v-if="!collapsed"
            text
            circle
            size="small"
            class="collapse-btn"
            @click="collapsed = true"
          >
            <el-icon><Fold /></el-icon>
          </el-button>
          <el-button
            v-else
            text
            circle
            size="small"
            class="collapse-btn"
            @click="collapsed = false"
          >
            <el-icon><Expand /></el-icon>
          </el-button>
        </div>
        <el-scrollbar>
          <el-menu
            :collapse="collapsed"
            :default-active="activeMenu"
            router
            background-color="transparent"
          >
            <!-- 概览 -->
            <el-menu-item index="/dashboard">
              <el-icon><DataLine /></el-icon>
              <template #title>{{ $t('nav.dashboard') }}</template>
            </el-menu-item>

            <!-- 数据字典 -->
            <el-menu-item index="/data-dictionary">
              <el-icon><Reading /></el-icon>
              <template #title>{{ $t('nav.dictionary') }}</template>
            </el-menu-item>

            <!-- 注册通用设施 -->
            <el-sub-menu index="/common-facilities-group">
              <template #title>
                <el-icon><OfficeBuilding /></el-icon>
                <span>{{ $t('nav.commonFacilities') }}</span>
              </template>
              <el-menu-item index="/registration-authorities">{{ $t('nav.registrationAuthority') }}</el-menu-item>
              <el-menu-item index="/registry-specifications">{{ $t('nav.registrySpecification') }}</el-menu-item>
              <el-menu-item index="/registrars">{{ $t('nav.registrar') }}</el-menu-item>
              <el-menu-item index="/namespaces">{{ $t('nav.namespace') }}</el-menu-item>
              <el-menu-item index="/naming-conventions">{{ $t('nav.namingConvention') }}</el-menu-item>
              <el-menu-item index="/languages">{{ $t('nav.language') }}</el-menu-item>
              <el-menu-item index="/contexts">{{ $t('nav.context') }}</el-menu-item>
            </el-sub-menu>

            <!-- 主体与文档 -->
            <el-sub-menu index="/party-document-group">
              <template #title>
                <el-icon><User /></el-icon>
                <span>{{ $t('nav.partyAndDocument') }}</span>
              </template>
              <el-menu-item index="/organizations">{{ $t('nav.organization') }}</el-menu-item>
              <el-menu-item index="/individuals">{{ $t('nav.individual') }}</el-menu-item>
              <el-menu-item index="/contacts">{{ $t('nav.contact') }}</el-menu-item>
              <el-menu-item index="/reference-documents">{{ $t('nav.referenceDocument') }}</el-menu-item>
            </el-sub-menu>

            <!-- 概念区域 -->
            <el-sub-menu index="/concept-region-group">
              <template #title>
                <el-icon><SetUp /></el-icon>
                <span>{{ $t('nav.conceptRegion') }}</span>
              </template>
              <el-menu-item index="/object-classes">{{ $t('nav.objectClass') }}</el-menu-item>
              <el-menu-item index="/properties">{{ $t('nav.property') }}</el-menu-item>
              <el-menu-item index="/data-element-concepts?group=concepts">{{ $t('nav.dataElementConcept') }}</el-menu-item>
              <el-menu-item index="/conceptual-domains?group=concepts">{{ $t('nav.conceptualDomain') }}</el-menu-item>
              <el-menu-item index="/units-of-measure">{{ $t('nav.unitOfMeasure') }}</el-menu-item>
              <el-menu-item index="/measure-classes">{{ $t('nav.measureClass') }}</el-menu-item>
              <el-menu-item index="/dimensionality">{{ $t('nav.dimensionality') }}</el-menu-item>
            </el-sub-menu>

            <!-- 表示区域 -->
            <el-sub-menu index="/representation-region-group">
              <template #title>
                <el-icon><Document /></el-icon>
                <span>{{ $t('nav.representationRegion') }}</span>
              </template>
              <el-menu-item index="/data-elements">{{ $t('nav.dataElement') }}</el-menu-item>
              <el-menu-item index="/value-domains">{{ $t('nav.valueDomain') }}</el-menu-item>
              <el-menu-item index="/data-types">{{ $t('nav.dataType') }}</el-menu-item>
              <el-menu-item index="/data-type-schemes">{{ $t('nav.dataTypeScheme') }}</el-menu-item>
            </el-sub-menu>

            <!-- 分类与映射 -->
            <el-sub-menu index="/classification-mapping-group">
              <template #title>
                <el-icon><Connection /></el-icon>
                <span>{{ $t('nav.classificationAndMapping') }}</span>
              </template>
              <el-menu-item index="/classification">{{ $t('nav.classificationManagement') }}</el-menu-item>
              <el-menu-item index="/mapping-schemes">{{ $t('nav.mappingScheme') }}</el-menu-item>
              <el-menu-item index="/mapping-network">{{ $t('nav.mappingNetwork') }}</el-menu-item>
              <el-menu-item index="/interop-mappings">{{ $t('nav.interopMappings') }}</el-menu-item>
            </el-sub-menu>

            <!-- 系统 -->
            <el-sub-menu v-if="auth.isAdmin" index="/system-group">
              <template #title>
                <el-icon><Setting /></el-icon>
                <span>{{ $t('nav.system') }}</span>
              </template>
              <el-menu-item index="/app-users">{{ $t('nav.userManagement') }}</el-menu-item>
              <el-menu-item index="/system-settings">{{ $t('nav.systemSettings') }}</el-menu-item>
            </el-sub-menu>
          </el-menu>
        </el-scrollbar>

        <!-- 我的：固定于侧边栏底部 -->
        <div class="sidebar-footer" data-testid="sidebar-footer">
          <div class="sidebar-footer-item" data-testid="nav-my" @click="goToProfile">
            <el-icon><User /></el-icon>
            <span v-if="!collapsed" class="sidebar-footer-label">{{ $t('nav.my') }}</span>
          </div>
        </div>
      </aside>
      <main class="app-main">
        <router-view />
      </main>

    <!-- 安全改密弹窗 -->
    <el-dialog
      :model-value="showChangePasswordDialog"
      title="安全提示：需要修改密码"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      width="450px"
    >
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-position="top"
      >
        <p style="margin: 0 0 16px; color: #e6a23c; font-size: 14px; line-height: 1.5;">
          {{ auth.user?.emergencyLogin ? '您当前使用的是应急码登录，为了系统安全，请立即设置新密码。新密码不能与原密码相同。' : '您当前使用的是临时密码或系统初始凭证登录，请设置新密码以继续使用系统。' }}
        </p>
        <el-form-item
          v-if="requireOldPassword"
          label="原密码 (或应急码)"
          prop="oldPassword"
        >
          <el-input
            v-model="pwdForm.oldPassword"
            type="password"
            placeholder="请输入原密码或应急码"
            show-password
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            placeholder="请输入新密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="pwdForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          type="primary"
          :loading="submitLoading"
          @click="submitPasswordChange"
          style="width: 100%;"
        >
          确认修改并登录
        </el-button>
      </template>
    </el-dialog>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { Reading } from '@element-plus/icons-vue';
import type { FormInstance } from 'element-plus';
import { ElMessage } from 'element-plus';
import request from '@/utils/request';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const collapsed = ref(false);

const activeMenu = computed(() => {
  if (route.query.group) {
    return `${route.path}?group=${route.query.group}`;
  }
  if (route.path === '/conceptual-domains') {
    return '/conceptual-domains?group=specification';
  }
  if (route.path === '/data-element-concepts') {
    return '/data-element-concepts?group=specification';
  }
  return route.path;
});

function handleLogout() {
  auth.logout();
  // Use replace to prevent browser back from showing cached authenticated pages
  router.replace('/login');
}

function goToProfile() {
  router.push('/my-profile');
}

// ── Password Change Dialog for forceChangePassword ─────────────────
const showChangePasswordDialog = computed(() => !!auth.user?.forceChangePassword);

const isFirstLoginAdmin = computed(() => {
  return auth.user?.username === 'admin' &&
    !auth.user?.emergencyLogin &&
    !auth.user?.lastLoginAt;
});

const requireOldPassword = computed(() => {
  return !isFirstLoginAdmin.value &&
    !auth.user?.emergencyLogin &&
    !auth.user?.tempPasswordUsed;
});

const pwdFormRef = ref<FormInstance>();
const submitLoading = ref(false);

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});

const validateConfirmPassword = (rule: any, value: any, callback: any) => {
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入密码不一致'));
  } else {
    callback();
  }
};

const pwdRules = computed(() => {
  const rules: any = {
    newPassword: [
      { required: true, message: '请输入新密码', trigger: 'blur' },
      { min: 6, max: 20, message: '密码长度在 6 到 20 个字符', trigger: 'blur' }
    ],
    confirmPassword: [
      { required: true, message: '请再次输入新密码', trigger: 'blur' },
      { validator: validateConfirmPassword, trigger: 'blur' }
    ]
  };
  if (requireOldPassword.value) {
    rules.oldPassword = [
      { required: true, message: '请输入原密码或应急码', trigger: 'blur' }
    ];
  }
  return rules;
});

async function submitPasswordChange() {
  if (!pwdFormRef.value) return;
  await pwdFormRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true;
      try {
        const payload = {
          oldPassword: isFirstLoginAdmin.value ? '' : pwdForm.oldPassword,
          newPassword: pwdForm.newPassword
        };
        await request.post('/auth/change-password', payload);
        const username = auth.user?.username || 'admin';
        await auth.login({ username, password: pwdForm.newPassword });
        ElMessage.success('密码修改成功，已自动为您登录系统。');
        pwdForm.oldPassword = '';
        pwdForm.newPassword = '';
        pwdForm.confirmPassword = '';
      } catch (err) {
        // Handled by request interceptor
      } finally {
        submitLoading.value = false;
      }
    }
  });
}
</script>

<style scoped lang="scss">
.highlight {
  font-weight: 600;
}
</style>
