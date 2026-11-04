import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import type { AppUserDto } from '@/types/common';
import request, { withSilentErrors } from '@/utils/request';

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

  const isLoggedIn = computed(() => !!token.value || bypassLogin.value);
  /** 个人版免登录（personal-no-login-registrar-default）：PERSONAL 已初始化时无需任何凭证。 */
  const bypassLogin = computed(() => edition.value === 'PERSONAL' && initialized.value === true);
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

  /** 写入当前部署版本（如初始化向导所选 edition）——bypassLogin 依赖该值。 */
  function setEdition(value: string | null) {
    edition.value = value;
  }

  /**
   * Fetch initialization status from the backend.
   * Result is cached in `initialized`; subsequent calls return the cached value
   * without hitting the network (unless `force` is true).
   *
   * 失败时重试一次再降级：应用重启后，浏览器可能复用已被服务端重置的空闲
   * 连接（半开连接），首个请求瞬时失败而后端实际已就绪——重试即落在新建连接上。
   * 降级结果绝不写入缓存（保持 initialized=null，下次导航重试）：个人版免登录
   * 依赖 edition 判定，缓存"已初始化 + 版本未知"会让 isLoggedIn 恒为 false，
   * 整个页面会话被钉死在登录页且无法自愈（重启后登录页事故）。
   */
  async function checkInitialized(force = false): Promise<boolean> {
    if (!force && initialized.value !== null) {
      return initialized.value;
    }
    try {
      const status = await fetchInitStatusWithRetry();
      initialized.value = status.initialized;
      edition.value = status.edition ?? null;
      return status.initialized;
    } catch {
      // 后端持续不可达：本次导航保守放行（视为已初始化），不弹错误、不缓存。
      return true;
    }
  }

  /**
   * 强制刷新初始化状态，返回**本次**刷新的结论：成功返回新的布尔值，失败返回 null。
   *
   * checkInitialized 在后端不可达时降级返回 true 并保留旧缓存，而重启窗口里的旧缓存
   * 可能是上一套数据库的结论。调用方若直接用它的返回值做分支判断，会在探测失败时把
   * 用户误送到"已初始化"的首页，因此需要一个能区分"取到了新值"与"没取到"的接口。
   */
  async function refreshInitialized(): Promise<boolean | null> {
    try {
      const status = await fetchInitStatusWithRetry();
      initialized.value = status.initialized;
      edition.value = status.edition ?? null;
      return status.initialized;
    } catch {
      return null;
    }
  }

  /** 查询初始化状态，失败或响应非法时间隔 800ms 重试一次（静默，不触发错误提示）。 */
  async function fetchInitStatusWithRetry(): Promise<InitWizardStatus> {
    try {
      return await fetchInitStatusOnce();
    } catch {
      // 落到此处多为重启窗口的瞬时网络错误，间隔后换新连接重试一次
    }
    await new Promise((resolve) => setTimeout(resolve, 800));
    return fetchInitStatusOnce();
  }

  /**
   * 单次查询并校验响应形状：代理/扩展异常时可能拿到 200 但响应体是 HTML，
   * request 拦截器会把它解成 undefined——一律视为失败交给上层重试，
   * 绝不让非法响应缓存成"已初始化 + 版本未知"（重启后登录页事故）。
   */
  async function fetchInitStatusOnce(): Promise<InitWizardStatus> {
    const status = await withSilentErrors(() => request.get<InitWizardStatus>('/init-wizard/status'));
    if (!status || typeof status.initialized !== 'boolean') {
      throw new Error('非法的初始化状态响应');
    }
    return status;
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
    // 活动 Registrar 选择随登录态一并清理（重新进入时按默认注册人员回退）
    localStorage.removeItem('activeRegistrarId');
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
    token, user, isLoggedIn, bypassLogin, isAdmin, isRegistrar, isRegistrarUser, initialized,
    hasRole, setInitialized, setEdition, checkInitialized, refreshInitialized, login, fetchMe, logout, adoptSession,
    edition,
  };
});
