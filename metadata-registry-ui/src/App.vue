<template>
  <!-- App-level loading spinner while router resolves -->
  <div v-if="isLoading" class="app-loading">
    <div class="loading-content">
      <el-icon class="loading-spinner" :size="36" color="#2563eb">
        <Loading />
      </el-icon>
      <p class="loading-text">加载中...</p>
    </div>
  </div>
  <ErrorBoundary v-else>
    <router-view />
  </ErrorBoundary>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { Loading } from '@element-plus/icons-vue';
import ErrorBoundary from '@/components/ErrorBoundary.vue';

const router = useRouter();
const isLoading = ref(true);

onMounted(async () => {
  // Wait for the router to resolve the initial route.
  // This prevents a flash of empty content while the auth guard runs.
  await router.isReady();
  isLoading.value = false;
});
</script>

<style>
body {
  margin: 0;
  padding: 0;
}

.app-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #f8fafc;
}

.loading-content {
  text-align: center;
}

.loading-spinner {
  animation: spin 1s linear infinite;
}

.loading-text {
  margin-top: 16px;
  color: #6b7280;
  font-size: 14px;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
