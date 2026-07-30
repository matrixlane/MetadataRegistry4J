<template>
  <LoginLayout>
    <el-card class="login-card" shadow="always">
      <div class="login-header">
        <img src="/favicon.svg" width="56" height="56" alt="logo" />
        <h2>{{ $t('app.name') }}</h2>
        <p>MetaDataRegistry</p>
      </div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleSubmit"
      >
        <el-form-item :label="$t('auth.username')" prop="username">
          <el-input
            v-model="form.username"
            type="text"
            name="username"
            autocomplete="username"
            :placeholder="$t('auth.pleaseInputUsername')"
            size="large"
            clearable
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item :label="$t('auth.password')" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            name="password"
            autocomplete="current-password"
            show-password
            :placeholder="$t('auth.pleaseInputPassword')"
            size="large"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item v-if="errorMessage" class="error-alert-item">
          <el-alert
            :title="errorMessage"
            type="error"
            :closable="false"
            show-icon
            effect="light"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            style="width: 100%; margin-top: 8px;"
            @click="handleSubmit"
          >
            {{ $t('auth.login') }}
          </el-button>
        </el-form-item>
        <div v-if="allowRegistration" style="text-align: center; margin-top: 16px;">
          <el-link type="primary" @click="showRegister = true">新用户注册</el-link>
        </div>
      </el-form>
    </el-card>

    <el-dialog
      v-model="showRegister"
      title="用户注册"
      width="400px"
      append-to-body
      destroy-on-close
    >
      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        label-position="top"
        @keyup.enter="handleRegister"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="请输入用户名"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            show-password
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            show-password
            placeholder="请确认密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div style="text-align: right;">
          <el-button @click="showRegister = false">取消</el-button>
          <el-button type="primary" :loading="registerLoading" @click="handleRegister">注册</el-button>
        </div>
      </template>
    </el-dialog>
  </LoginLayout>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import { User, Lock } from '@element-plus/icons-vue';
import LoginLayout from '@/layouts/LoginLayout.vue';
import request from '@/utils/request';

const router = useRouter();
const { t } = useI18n();
const auth = useAuthStore();
const formRef = ref<FormInstance>();
const loading = ref(false);
const errorMessage = ref('');

const form = reactive({
  username: '',
  password: '',
});

const rules = reactive<FormRules>({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
});

watch([() => form.username, () => form.password], () => {
  errorMessage.value = '';
});

const showRegister = ref(false);
const registerLoading = ref(false);
const registerFormRef = ref<FormInstance>();
const allowRegistration = ref(true);

async function checkAllowRegistration() {
  try {
    const res = await request.get<boolean>('/auth/allow-registration');
    allowRegistration.value = res;
  } catch (err) {
    // Fallback to true
  }
}

onMounted(() => {
  checkAllowRegistration();
});

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
});

const validateConfirmPassword = (rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请再次输入密码'));
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入密码不一致!'));
  } else {
    callback();
  }
};

const registerRules = reactive<FormRules>({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_\-.]{3,32}$/, message: '用户名仅可包含字母数字_-.，长度 3-32', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度需在 8-64 位之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ]
});

async function handleRegister() {
  if (!registerFormRef.value) return;
  await registerFormRef.value.validate(async (valid) => {
    if (!valid) return;
    registerLoading.value = true;
    try {
      await request.post('/auth/register', {
        username: registerForm.username,
        password: registerForm.password
      });
      ElMessage.success('注册成功！请等待管理员审核。');
      showRegister.value = false;
      registerForm.username = '';
      registerForm.password = '';
      registerForm.confirmPassword = '';
    } finally {
      registerLoading.value = false;
    }
  });
}

async function handleSubmit() {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (!valid) return;
    errorMessage.value = '';
    loading.value = true;
    try {
      await auth.login(form);
      // After login, check if the system has been initialized.
      // Factory-reset followed by a re-login must land on the wizard, not the app.
      const isInitialized = await auth.checkInitialized();
      router.push(isInitialized ? '/' : '/init-wizard');
    } catch (err: any) {
      // Show the error directly on the login form. The request interceptor is
      // intentionally skipped for /login 401s so the message appears here.
      const msg = err?.response?.data?.message || err?.message || t('auth.invalidCredentials');
      errorMessage.value = msg;
      ElMessage.error(msg);
    } finally {
      loading.value = false;
    }
  });
}
</script>

<style scoped lang="scss">
.login-card {
  width: 420px;
  padding: 8px;
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.5);

  :deep(.el-card__body) {
    padding: 32px 28px;
  }
}

.login-header {
  text-align: center;
  margin-bottom: 28px;

  img {
    filter: drop-shadow(0 4px 8px rgba(37, 99, 235, 0.25));
  }

  h2 {
    margin: 16px 0 4px;
    font-size: 24px;
    font-weight: 700;
    color: #1e40af;
    letter-spacing: 0.5px;
  }

  p {
    margin: 0;
    color: #6b7c93;
    font-size: 14px;
    letter-spacing: 1px;
  }
}

:deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 0 0 1px #e5e7eb inset;
  padding: 4px 12px;

  &.is-focus {
    box-shadow: 0 0 0 1px #2563eb inset;
  }
}

.error-alert-item {
  margin-bottom: 4px;

  :deep(.el-alert) {
    padding: 8px 12px;
  }
}
</style>
