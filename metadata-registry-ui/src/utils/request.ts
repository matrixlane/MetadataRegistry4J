import axios from 'axios';
import type { AxiosInstance, AxiosError } from 'axios';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';

const baseURL = import.meta.env.VITE_API_BASE;

const request: AxiosInstance = axios.create({
  baseURL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

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
      ElMessage.error(body.message || '请求失败');
      return Promise.reject(new Error(body.message));
    }
    return body.data;
  },
  (error: AxiosError<{ message?: string }>) => {
    if (error.response?.status === 401) {
      useAuthStore().logout();
      window.location.href = '/login';
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络错误');
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
