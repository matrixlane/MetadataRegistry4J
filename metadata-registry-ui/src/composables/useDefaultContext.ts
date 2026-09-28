import { ref, computed } from 'vue';
import { contextApi } from '@/api/resources';
import { withSilentErrors } from '@/utils/request';

/**
 * 系统默认上下文：注册表自身的上下文（RegistrySpecification.context）。
 * 模块级共享缓存：整个应用生命周期内只请求一次；注册规范未关联上下文 / 请求失败时
 * 默认为 null，调用方退化为不预填。
 */
interface DefaultContextState {
  id: string | null;
  label: string | null;
  loading: boolean;
}

const state = ref<DefaultContextState>({
  id: null,
  label: null,
  loading: false,
});

let loaded = false;
let pending: Promise<void> | null = null;

export function useDefaultContext() {
  async function load(): Promise<void> {
    if (loaded) return;
    if (pending) return pending;
    state.value.loading = true;
    pending = Promise.resolve()
      // 后端未部署 /default 端点时为 400，注册规范未关联上下文时为 404：
      // 两者都是可预期降级，抑制全局拦截器的错误 toast，由下方 catch 静默处理
      .then(() => withSilentErrors(() => contextApi.default()))
      .then((res) => {
        state.value.id = res?.id ?? null;
        state.value.label = res?.label ?? null;
        loaded = true;
      })
      .catch(() => {
        // 未初始化系统没有默认上下文，静默降级为不预填
      })
      .finally(() => {
        state.value.loading = false;
        pending = null;
      });
    return pending;
  }

  return {
    defaultContextId: computed(() => state.value.id),
    defaultContextLabel: computed(() => state.value.label),
    loading: computed(() => state.value.loading),
    load,
  };
}
