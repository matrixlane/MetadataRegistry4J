import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { AppUserDto } from '@/types/common';
import request from '@/utils/request';

interface LoginPayload {
  username: string;
  password: string;
}

interface LoginResult {
  token: string;
  user: AppUserDto;
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('token') || '');
  const user = ref<AppUserDto | null>(null);

  const isLoggedIn = computed(() => !!token.value);
  const isAdmin = computed(() => user.value?.role === 'ADMIN');
  const isRegistrar = computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'REGISTRAR');

  async function login(payload: LoginPayload) {
    const result = await request.post<LoginResult>('/auth/login', payload);
    token.value = result.token;
    user.value = result.user;
    localStorage.setItem('token', result.token);
    return result;
  }

  async function fetchMe() {
    const me = await request.get<AppUserDto>('/auth/me');
    user.value = me;
    return me;
  }

  function logout() {
    token.value = '';
    user.value = null;
    localStorage.removeItem('token');
  }

  return { token, user, isLoggedIn, isAdmin, isRegistrar, login, fetchMe, logout };
});
