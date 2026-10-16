import axios from 'axios';
import type { AxiosInstance, AxiosError } from 'axios';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import { useWorkspaceBadge } from '@/composables/useWorkspaceBadge';

// 生产构建没有 .env.development，VITE_API_BASE 为空时默认 /api/v1（同源部署）
const baseURL = import.meta.env.VITE_API_BASE ?? '/api/v1';

const request: AxiosInstance = axios.create({
  baseURL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

// 批量操作期间抑制逐条错误 toast：计数器支持并发/嵌套调用，仍正常 reject
let silenceCount = 0;

export async function withSilentErrors<T>(fn: () => Promise<T>): Promise<T> {
  silenceCount++;
  try {
    return await fn();
  } finally {
    silenceCount--;
  }
}

request.interceptors.request.use((config) => {
  const token = useAuthStore().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  // 活动 Registrar 身份：浏览器端保存的选择随请求头传递（personal-no-login-registrar-default）
  const activeRegistrarId = localStorage.getItem('activeRegistrarId');
  if (activeRegistrarId) {
    config.headers['X-Active-Registrar-Id'] = activeRegistrarId;
  }
  return config;
});

request.interceptors.response.use(
  (response) => {
    const body = response.data;
    if (body.code !== 0 && body.code !== undefined) {
      if (silenceCount === 0) {
        ElMessage.error(body.message || '请求失败');
      }
      return Promise.reject(new Error(body.message));
    }
    // 成功的写操作（新建/更新/删除草稿等）可能改变工作空间待提交计数，
    // 通知徽标去抖刷新；GET 等读操作不改变状态，不触发。
    const method = response.config.method?.toLowerCase();
    if (method === 'post' || method === 'put' || method === 'patch' || method === 'delete') {
      useWorkspaceBadge().notifyMutation();
    }
    return body.data;
  },
  (error: AxiosError<{ message?: string }>) => {
    if ((error.response?.data as any)?.code === 499) {
      // 未初始化时只有向导页以外的请求需要被拉回向导；向导页内的 499 是预期状态
      // （向导本身就服务于未初始化的系统，且 location.href 会触发 beforeunload 原生弹窗），
      // 此处静默 reject，由调用方决定如何降级。
      if (window.location.pathname !== '/init-wizard') {
        window.location.href = '/init-wizard';
      }
    } else if ((error.response?.data as any)?.code === 498 || error.response?.status === 498) {
      const auth = useAuthStore();
      // 个人版免登录：无密码概念，会话永不进入强制改密状态。498 一律按普通错误处理，
      // 绝不能把 forceChangePassword 置位——该标志只进不退，置位即弹出无法完成的
      // 改密框并锁死整个界面。
      if (!auth.bypassLogin && auth.user) {
        auth.user.forceChangePassword = true;
      }
      ElMessage.warning('当前会话需要先修改密码才能继续操作');
    } else if (error.response?.status === 401) {
      const auth = useAuthStore();
      if (auth.bypassLogin) {
        // 个人版免登录：无登录页可去，直接拒绝由调用方降级
        return Promise.reject(error);
      }
      auth.logout();
      if (window.location.pathname === '/login') {
        // Let the Login page display the message itself to avoid duplicates.
        return Promise.reject(error);
      }
      const msg = (error.response?.data as any)?.message || '认证失败，请重新登录';
      if (silenceCount === 0) {
        ElMessage.error(msg);
      }
      // 初始化向导处于无账号的前置阶段，其端点均匿名放行；
      // 此时跳登录页既无意义，又会触发向导的 beforeunload 原生弹窗
      if (window.location.pathname !== '/init-wizard') {
        window.location.href = '/login';
      }
    } else if (error.response?.status === 404
        && (error.config?.url ?? '').endsWith('/auth/me')
        && useAuthStore().bypassLogin) {
      // 个人版免登录：localStorage 残留的旧令牌（库已重建、账号 UUID 已变）会让
      // /auth/me 404。路由守卫会据此丢弃残留令牌完成自愈——此处静默，
      // 不再弹出 "Account not found" 惊吓用户。
      return Promise.reject(error);
    } else {
      const status = error.response?.status;
      const serverMsg = error.response?.data?.message;
      const fallback = status && status >= 500
        ? '服务器内部错误，请稍后再试或联系管理员'
        : (error.message || '网络错误');
      if (silenceCount === 0) {
        ElMessage.error(serverMsg || fallback);
      }
    }
    return Promise.reject(error);
  }
);

const http = {
  get: <T>(url: string, config?: Parameters<AxiosInstance['get']>[1]) =>
    request.get(url, config) as Promise<T>,
  post: <T>(url: string, data?: unknown, config?: Parameters<AxiosInstance['post']>[2]) =>
    request.post(url, data, config) as Promise<T>,
  put: <T>(url: string, data?: unknown, config?: Parameters<AxiosInstance['put']>[2]) =>
    request.put(url, data, config) as Promise<T>,
  delete: <T>(url: string, config?: Parameters<AxiosInstance['delete']>[1]) =>
    request.delete(url, config) as Promise<T>,
  patch: <T>(url: string, data?: unknown, config?: Parameters<AxiosInstance['patch']>[2]) =>
    request.patch(url, data, config) as Promise<T>,
};

export default http;
