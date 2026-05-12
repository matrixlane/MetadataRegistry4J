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
            show-password
            :placeholder="$t('auth.pleaseInputPassword')"
            size="large"
            :prefix-icon="Lock"
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
      </el-form>
    </el-card>
  </LoginLayout>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { FormInstance, FormRules } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import { User, Lock } from '@element-plus/icons-vue';
import LoginLayout from '@/layouts/LoginLayout.vue';

const router = useRouter();
const auth = useAuthStore();
const formRef = ref<FormInstance>();
const loading = ref(false);

const form = reactive({
  username: '',
  password: '',
});

const rules = reactive<FormRules>({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
});

async function handleSubmit() {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (!valid) return;
    loading.value = true;
    try {
      await auth.login(form);
      router.push('/');
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
</style>
