import axios from 'axios';
import type { AxiosInstance, AxiosError } from 'axios';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';

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
    return body.data;
  },
  (error: AxiosError<{ message?: string }>) => {
    if ((error.response?.data as any)?.code === 499) {
      // 已在向导页时不再整页跳转（location.href 会触发向导的 beforeunload 原生弹窗）
      if (window.location.pathname !== '/init-wizard') {
        window.location.href = '/init-wizard';
      } else {
        ElMessage.error('系统尚未完成初始化，请按向导完成配置');
      }
    } else if ((error.response?.data as any)?.code === 498 || error.response?.status === 498) {
      // Session is in force-change-password state (e.g. wizard session token,
      // temp password, emergency login). Surface the dialog in AppLayout so
      // the user can set a new password without being kicked back to login.
      const auth = useAuthStore();
      if (auth.user) {
        auth.user.forceChangePassword = true;
      }
      ElMessage.warning('当前会话需要先修改密码才能继续操作');
    } else if (error.response?.status === 401) {
      useAuthStore().logout();
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
