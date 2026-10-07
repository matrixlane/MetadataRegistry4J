import { ref, computed, reactive } from 'vue';
import { activeRegistrarApi } from '@/api/resources';
import type { RegistrarIdentity } from '@/types/common';

/**
 * 活动 Registrar 身份（personal-no-login-registrar-default：激活状态浏览器化）。
 *
 * 激活选择保存在 localStorage（按浏览器维度），随请求经 `X-Active-Registrar-Id`
 * 头传递给服务端；服务端按 头 → ext.registrar 默认标志 → 首个有效身份 解析。
 * 本 composable 为模块级单例：顶栏切换后所有消费方同步生效。
 */
const STORAGE_KEY = 'activeRegistrarId';

const identities = ref<RegistrarIdentity[]>([]);
const defaultRegistrarId = ref<string | null>(null);
const selectedId = ref<string | null>(localStorage.getItem(STORAGE_KEY));
const loaded = ref(false);
let inflight: Promise<void> | null = null;

export function useActiveRegistrar() {
  /** 当前生效身份：浏览器端选择（须在身份列表中有效）→ 默认注册人员 → 首个。 */
  const active = computed<RegistrarIdentity | null>(() => {
    if (selectedId.value) {
      const hit = identities.value.find((i) => i.registrarId === selectedId.value);
      if (hit) return hit;
    }
    if (defaultRegistrarId.value) {
      const hit = identities.value.find((i) => i.registrarId === defaultRegistrarId.value);
      if (hit) return hit;
    }
    return identities.value[0] ?? null;
  });

  function load(force = false): Promise<void> {
    if (loaded.value && !force) return Promise.resolve();
    // 在途去重：同一挂载树多个消费方共享一次请求
    if (inflight) return inflight;
    inflight = doLoad().finally(() => {
      inflight = null;
    });
    return inflight;
  }

  async function doLoad(): Promise<void> {
    // 每次加载重读 localStorage：覆盖"写入后首次加载"之外的变更路径
    selectedId.value = localStorage.getItem(STORAGE_KEY);
    try {
      const res = await activeRegistrarApi.current();
      identities.value = res.identities ?? [];
      defaultRegistrarId.value = res.defaultRegistrarId ?? null;
      loaded.value = true;
    } catch (e) {
      // 失败不置 loaded：下次 load() 自动重试，避免一次瞬时故障毒化整个会话；
      // 消费方（如归属提示的失败态展示）依赖 reject 语义
      identities.value = [];
      defaultRegistrarId.value = null;
      throw e;
    }
  }

  /** 切换激活身份：纯客户端动作（写 localStorage + 广播事件），MUST NOT 调用任何持久化端点。 */
  function switchRegistrar(registrarId: string): void {
    if (!registrarId || !identities.value.some((i) => i.registrarId === registrarId)) return;
    if (registrarId === active.value?.registrarId) return;
    selectedId.value = registrarId;
    localStorage.setItem(STORAGE_KEY, registrarId);
    window.dispatchEvent(new CustomEvent('mdregistry:active-registrar-changed'));
  }

  /** 登出/换账号时重置本地状态。 */
  function reset(): void {
    identities.value = [];
    defaultRegistrarId.value = null;
    selectedId.value = null;
    loaded.value = false;
    localStorage.removeItem(STORAGE_KEY);
  }

  return reactive({ identities, defaultRegistrarId, active, load, switchRegistrar, reset });
}
