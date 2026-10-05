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

interface InitWizardStatus {
  initialized: boolean;
  edition?: string | null;
}

/** Role hierarchy: higher number = more privileges */
const ROLE_HIERARCHY: Record<string, number> = {
  VIEWER: 0,
  REGISTRAR: 1,
  ADMIN: 2,
};

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('token') || '');
  const user = ref<AppUserDto | null>(null);

  /**
   * null  = not yet checked (unknown)
   * true  = system is initialized
   * false = system is NOT initialized → must go through init-wizard
   */
  const initialized = ref<boolean | null>(null);
  const edition = ref<string | null>(null);

  const isLoggedIn = computed(() => !!token.value);
  const isAdmin = computed(() => user.value?.sysRole === 'SUPER_ADMIN');
  const isRegistrar = computed(() => user.value?.sysRole === 'SUPER_ADMIN' || !!user.value?.isRegistrar);
  /** 是否具备 Registrar 业务角色（不含 SUPER_ADMIN）。AI 使用类入口一律以此门控：
   *  后端 /inference/** 与 /assistant/** 已收紧为仅 ROLE_REGISTRAR。 */
  const isRegistrarUser = computed(() => !!user.value?.isRegistrar);

  /**
   * Check if the current user has at least the specified role.
   * Hierarchy: VIEWER < REGISTRAR < ADMIN
   */
  function hasRole(minRole: string): boolean {
    if (!user.value) return false;
    let userRole = 'VIEWER';
    if (user.value.sysRole === 'SUPER_ADMIN') {
      userRole = 'ADMIN';
    } else if (user.value.isRegistrar) {
      userRole = 'REGISTRAR';
    }
    const userLevel = ROLE_HIERARCHY[userRole] ?? -1;
    const requiredLevel = ROLE_HIERARCHY[minRole] ?? 99;
    return userLevel >= requiredLevel;
  }

  function setInitialized(value: boolean | null) {
    initialized.value = value;
  }

  /**
   * Fetch initialization status from the backend.
   * Result is cached in `initialized`; subsequent calls return the cached value
   * without hitting the network (unless `force` is true).
   */
  async function checkInitialized(force = false): Promise<boolean> {
    if (!force && initialized.value !== null) {
      return initialized.value;
    }
    try {
      const status = await request.get<InitWizardStatus>('/init-wizard/status');
      initialized.value = status.initialized;
      edition.value = status.edition ?? null;
      return status.initialized;
    } catch {
      // If the check fails we conservatively treat the system as initialized
      // so as not to block users on network errors unrelated to init state.
      initialized.value = true;
      return true;
    }
  }

  async function login(payload: LoginPayload) {
    const result = await request.post<LoginResult>('/auth/login', payload);
    token.value = result.token;
    user.value = result.user;
    localStorage.setItem('token', result.token);
    // Invalidate cached init status so the next navigation check re-fetches
    initialized.value = null;
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
    initialized.value = null;
    localStorage.removeItem('token');
  }

  /**
   * Adopt an externally-issued session (e.g. one returned by the init-wizard
   * setup endpoint) without re-issuing a /auth/login. This is used to keep
   * the passwordless initialization flow single round-trip.
   */
  function adoptSession(payload: { token: string; user: AppUserDto }) {
    token.value = payload.token;
    user.value = payload.user;
    localStorage.setItem('token', payload.token);
    initialized.value = true;
  }

  return {
    token, user, isLoggedIn, isAdmin, isRegistrar, isRegistrarUser, initialized,
    hasRole, setInitialized, checkInitialized, login, fetchMe, logout, adoptSession,
    edition,
  };
});
