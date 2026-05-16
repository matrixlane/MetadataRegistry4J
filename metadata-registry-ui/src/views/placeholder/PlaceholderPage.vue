<template>
  <div class="placeholder-page">
    <div class="placeholder-content">
      <div class="placeholder-icon">
        <el-icon :size="64" color="#9ca3af">
          <Tools />
        </el-icon>
      </div>
      <h2 class="placeholder-title">{{ pageTitle }}</h2>
      <p class="placeholder-desc">功能开发中，敬请期待</p>
      <p class="placeholder-hint" v-if="routeKey">
        功能标识：<code>{{ routeKey }}</code>
      </p>
      <el-button type="primary" plain @click="goBack" class="back-btn">
        <el-icon><ArrowLeft /></el-icon>
        返回上一页
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Tools, ArrowLeft } from '@element-plus/icons-vue';
import { routeTable } from '@/router/NavigationRouteTable';

const route = useRoute();
const router = useRouter();

const routeKey = computed(() => (route.meta?.routeKey as string) ?? route.name as string ?? '');

const pageTitle = computed(() => {
  const title = route.meta?.title as string | undefined;
  if (title) return title;

  if (routeKey.value) {
    const reg = routeTable.get(routeKey.value);
    if (reg) return reg.title;
  }
  return '页面开发中';
});

function goBack() {
  if (window.history.length > 1) {
    router.back();
  } else {
    router.push('/dashboard');
  }
}
</script>

<style scoped lang="scss">
.placeholder-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}

.placeholder-content {
  text-align: center;
  max-width: 420px;
  padding: 48px 24px;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 4px 16px rgba(0, 0, 0, 0.03);
  border: 1px solid #f1f5f9;
}

.placeholder-icon {
  margin-bottom: 24px;
  background: #f9fafb;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: auto;
  margin-right: auto;
}

.placeholder-title {
  font-size: 20px;
  font-weight: 700;
  color: #374151;
  margin: 0 0 8px;
}

.placeholder-desc {
  font-size: 15px;
  color: #6b7280;
  margin: 0 0 8px;
}

.placeholder-hint {
  font-size: 12px;
  color: #9ca3af;
  margin: 0 0 24px;

  code {
    background: #f3f4f6;
    padding: 2px 6px;
    border-radius: 4px;
    font-family: 'SF Mono', 'Cascadia Code', monospace;
    font-size: 11px;
  }
}

.back-btn {
  margin-top: 8px;
}
</style>
