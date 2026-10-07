import { describe, it, expect, beforeEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useAuthStore } from './auth';

// 免登录与版本状态语义（personal-no-login-registrar-default）
describe('stores/auth 个人版免登录', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.removeItem('token');
  });

  it('token 存在时 isLoggedIn 为 true（团队版语义不变）', () => {
    const auth = useAuthStore();
    auth.token = 'jwt-token';
    expect(auth.isLoggedIn).toBe(true);
    expect(auth.bypassLogin).toBe(false);
  });

  it('PERSONAL 已初始化时 bypassLogin 为 true 且免登录', () => {
    const auth = useAuthStore();
    auth.setInitialized(true);
    auth.setEdition('PERSONAL');
    expect(auth.bypassLogin).toBe(true);
    expect(auth.isLoggedIn).toBe(true);
  });

  it('edition 非 PERSONAL 时不 bypass（团队版仍须登录）', () => {
    const auth = useAuthStore();
    auth.setInitialized(true);
    auth.setEdition('TEAM');
    expect(auth.bypassLogin).toBe(false);
    expect(auth.isLoggedIn).toBe(false);
  });

  it('未初始化 PERSONAL 不 bypass（应先走向导）', () => {
    const auth = useAuthStore();
    auth.setInitialized(false);
    auth.setEdition('PERSONAL');
    expect(auth.bypassLogin).toBe(false);
  });

  it('logout 清理 token 与活动 Registrar 本地选择', () => {
    localStorage.setItem('token', 'jwt-token');
    localStorage.setItem('activeRegistrarId', 'reg-1');
    const auth = useAuthStore();
    auth.token = 'jwt-token';
    auth.logout();
    expect(auth.token).toBe('');
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('activeRegistrarId')).toBeNull();
  });
});
