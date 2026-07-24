<template>
  <div v-if="hasError" class="error-boundary">
    <div class="error-content">
      <div class="error-icon">
        <el-icon :size="56" color="#ef4444">
          <WarningFilled />
        </el-icon>
      </div>
      <h3 class="error-title">页面加载失败</h3>
      <p class="error-message">{{ errorMessage }}</p>
      <el-button type="primary" @click="retry">
        <el-icon><Refresh /></el-icon>
        重试
      </el-button>
      <el-button plain @click="goHome">
        <el-icon><HomeFilled /></el-icon>
        返回首页
      </el-button>
    </div>
  </div>
  <slot v-else />
</template>

<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue';
import { useRouter } from 'vue-router';
import { WarningFilled, Refresh, HomeFilled } from '@element-plus/icons-vue';

const props = withDefaults(
  defineProps<{
    /** Optional user-facing error message */
    message?: string;
  }>(),
  { message: '页面组件加载异常，请检查网络连接后重试。' },
);

const router = useRouter();
const hasError = ref(false);
const errorMessage = ref(props.message);

onErrorCaptured((err: any) => {
  // If this is a force password reset error (498), ignore it to let the dialog handle it
  const errStr = String(err);
  const errMessage = err?.message ? String(err.message) : '';
  const errStatus = err?.response?.status || err?.status || (err?.response?.data as any)?.code;

  const is498 = errStatus === 498 ||
                errStr.includes('498') ||
                errMessage.includes('498');
  if (is498) {
    return false;
  }

  // On the login page, authentication failures (401) are already shown by the
  // request interceptor (ElMessage). Do not render the full error boundary.
  const is401 = errStatus === 401 ||
                errStr.includes('401') ||
                errMessage.includes('401');
  if (is401 && window.location.pathname === '/login') {
    return false;
  }
  hasError.value = true;
  errorMessage.value = err instanceof Error ? err.message : props.message;
  // Prevent the error from bubbling further
  return false;
});

function retry() {
  hasError.value = false;
  errorMessage.value = props.message;
  // Force re-render by toggling a key on the parent
  window.location.reload();
}

function goHome() {
  hasError.value = false;
  errorMessage.value = props.message;
  router.push('/dashboard');
}
</script>

<style scoped lang="scss">
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 50vh;
}

.error-content {
  text-align: center;
  max-width: 400px;
  padding: 48px 24px;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 4px 16px rgba(0, 0, 0, 0.03);
  border: 1px solid #f1f5f9;
}

.error-icon {
  margin-bottom: 16px;
}

.error-title {
  font-size: 18px;
  font-weight: 700;
  color: #374151;
  margin: 0 0 8px;
}

.error-message {
  font-size: 14px;
  color: #6b7280;
  margin: 0 0 24px;
  line-height: 1.6;
}
</style>
