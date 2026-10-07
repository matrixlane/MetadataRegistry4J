import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useActiveRegistrar } from './useActiveRegistrar';
import { activeRegistrarApi } from '@/api/resources';

vi.mock('@/api/resources', () => ({
  activeRegistrarApi: {
    current: vi.fn(),
  },
}));

const mockedCurrent = vi.mocked(activeRegistrarApi.current);

const identityA = { registrarId: 'reg-1', registrationAuthorityId: 'ra-1', registrationAuthorityName: '甲机构' };
const identityB = { registrarId: 'reg-2', registrationAuthorityId: 'ra-2', registrationAuthorityName: '乙机构' };

describe('useActiveRegistrar（浏览器端激活状态）', () => {
  beforeEach(() => {
    useActiveRegistrar().reset();
    localStorage.removeItem('activeRegistrarId');
    mockedCurrent.mockReset();
  });

  it('默认注册人员优先于首个身份', async () => {
    mockedCurrent.mockResolvedValue({ defaultRegistrarId: 'reg-2', identities: [identityA, identityB] } as any);
    const ar = useActiveRegistrar();
    await ar.load();
    expect(ar.active?.registrarId).toBe('reg-2');
  });

  it('无默认时回退首个身份', async () => {
    mockedCurrent.mockResolvedValue({ defaultRegistrarId: null, identities: [identityA, identityB] } as any);
    const ar = useActiveRegistrar();
    await ar.load();
    expect(ar.active?.registrarId).toBe('reg-1');
  });

  it('localStorage 选择优先于默认，并随请求头语义生效', async () => {
    localStorage.setItem('activeRegistrarId', 'reg-1');
    mockedCurrent.mockResolvedValue({ defaultRegistrarId: 'reg-2', identities: [identityA, identityB] } as any);
    const ar = useActiveRegistrar();
    await ar.load();
    expect(ar.active?.registrarId).toBe('reg-1');
  });

  it('localStorage 值不在身份列表中时回退默认', async () => {
    localStorage.setItem('activeRegistrarId', 'revoked-registrar');
    mockedCurrent.mockResolvedValue({ defaultRegistrarId: 'reg-2', identities: [identityA, identityB] } as any);
    const ar = useActiveRegistrar();
    await ar.load();
    expect(ar.active?.registrarId).toBe('reg-2');
  });

  it('switchRegistrar 写 localStorage 并广播事件（不发请求）', async () => {
    mockedCurrent.mockResolvedValue({ defaultRegistrarId: 'reg-1', identities: [identityA, identityB] } as any);
    const ar = useActiveRegistrar();
    await ar.load();
    const listener = vi.fn();
    window.addEventListener('mdregistry:active-registrar-changed', listener);
    ar.switchRegistrar('reg-2');
    expect(localStorage.getItem('activeRegistrarId')).toBe('reg-2');
    expect(ar.active?.registrarId).toBe('reg-2');
    expect(listener).toHaveBeenCalledTimes(1);
    expect(mockedCurrent).toHaveBeenCalledTimes(1); // 无额外请求
    window.removeEventListener('mdregistry:active-registrar-changed', listener);
  });

  it('switchRegistrar 对未知身份为 no-op', async () => {
    mockedCurrent.mockResolvedValue({ defaultRegistrarId: 'reg-1', identities: [identityA, identityB] } as any);
    const ar = useActiveRegistrar();
    await ar.load();
    ar.switchRegistrar('not-mine');
    expect(localStorage.getItem('activeRegistrarId')).toBeNull();
    expect(ar.active?.registrarId).toBe('reg-1');
  });

  it('load 失败时降为空身份并 reject（消费方依赖失败态）', async () => {
    mockedCurrent.mockRejectedValue(new Error('network'));
    const ar = useActiveRegistrar();
    await expect(ar.load()).rejects.toThrow('network');
    expect(ar.active).toBeNull();
    expect(ar.identities).toEqual([]);
  });
});
