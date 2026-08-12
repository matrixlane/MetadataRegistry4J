import { ref, computed } from 'vue';
import { registrySpecificationApi } from '@/api/resources';

interface RegistryDefaultLanguageState {
  defaultLanguageId: string | null;
  defaultLanguageIdentifier: string | null;
  loading: boolean;
  error: string | null;
}

const state = ref<RegistryDefaultLanguageState>({
  defaultLanguageId: null,
  defaultLanguageIdentifier: null,
  loading: false,
  error: null,
});

let loaded = false;

export function useRegistryDefaultLanguage() {
  async function load() {
    if (loaded) return;
    state.value.loading = true;
    state.value.error = null;
    try {
      const res = await registrySpecificationApi.list({ size: 1 });
      const first = (res.content || res.data || [])[0];
      state.value.defaultLanguageId = first?.defaultLanguageId || null;
      state.value.defaultLanguageIdentifier = first?.defaultLanguageIdentifier || null;
      loaded = true;
    } catch (err: any) {
      state.value.error = err?.response?.data?.message || err?.message || '加载注册规范失败';
    } finally {
      state.value.loading = false;
    }
  }

  function retry() {
    loaded = false;
    return load();
  }

  return {
    defaultLanguageId: computed(() => state.value.defaultLanguageId),
    defaultLanguageIdentifier: computed(() => state.value.defaultLanguageIdentifier),
    loading: computed(() => state.value.loading),
    error: computed(() => state.value.error),
    load,
    retry,
  };
}
