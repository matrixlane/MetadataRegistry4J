<template>
  <div class="connecting-screen">
    <div class="connecting-card">
      <img src="/favicon.svg" width="56" height="56" alt="logo" />
      <h1 class="connecting-title">元数据注册中心</h1>
      <p class="connecting-subtitle">MetaDataRegistry</p>
      <div class="connecting-spinner" aria-hidden="true"></div>
      <p class="connecting-status">正在连接服务器…</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

/**
 * 连接恢复页：路由守卫确认不了初始化状态（后端瞬时不可达/响应非法）时
 * 落到本页，由本页静默重试直至状态已知，再分流：
 * 未初始化 → 初始化向导；个人版已初始化 → 免登录直达目标页；
 * 团队版未登录 → 登录页。个人版因此永远不会渲染登录页（含闪现）。
 */
async function resumeWhenKnown() {
  const redirect = (route.query.redirect as string) || '/dashboard';
  for (;;) {
    await auth.checkInitialized(true);
    if (auth.initialized !== null) break;
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  if (!auth.initialized) {
    router.replace('/init-wizard');
    return;
  }
  if (auth.bypassLogin || auth.isLoggedIn) {
    router.replace(redirect);
  } else {
    router.replace({ path: '/login', query: { redirect } });
  }
}

onMounted(resumeWhenKnown);
</script>

<style scoped>
.connecting-screen {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #14301f 0%, #1e4530 50%, #2d5a3d 100%);
  font-family: -apple-system, 'PingFang SC', 'Microsoft YaHei', system-ui, sans-serif;
}

.connecting-card {
  width: 340px;
  padding: 40px 32px 32px;
  box-sizing: border-box;
  text-align: center;
  background: rgba(255, 255, 255, 0.98);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}

.connecting-title {
  margin: 16px 0 4px;
  font-size: 22px;
  font-weight: 700;
  color: #1e4530;
  letter-spacing: 0.5px;
}

.connecting-subtitle {
  margin: 0;
  color: #6b7c93;
  font-size: 13px;
  letter-spacing: 1px;
}

.connecting-spinner {
  width: 36px;
  height: 36px;
  margin: 28px auto 20px;
  border-radius: 50%;
  border: 3px solid #e5e7eb;
  border-top-color: #16a34a;
  animation: connecting-spin 0.9s linear infinite;
}

.connecting-status {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #374151;
}

@keyframes connecting-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
