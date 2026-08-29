import { ref, computed, watch, onUnmounted } from 'vue';
import { metadataSearchApi } from '@/api/resources';
import type { AdministeredItemSearchHit } from '@/types/common';

export type SmartPickerScope = 'current' | 'all' | { namespaceId: string };

export interface SmartPickerQueryOptions {
  q?: string;
  type?: string[];
  scope?: SmartPickerScope;
  size?: number;
  /** 是否触发精确匹配路径（NS.ID / 当前 namespace identifier 解析结果） */
  exact?: { identifier: string; namespaceShorthand?: string };
}

interface CacheEntry {
  hits: AdministeredItemSearchHit[];
  expires: number;
}

const NS_ID_PATTERN = /^[A-Za-z0-9]{1,12}\.([A-Za-z0-9._-]{1,32})$/;
const PURE_ID_PATTERN = /^[A-Za-z0-9._-]{1,32}$/;
const CACHE_TTL_MS = 60_000;
const CACHE_MAX = 50;
const DEBOUNCE_MS = 250;

/**
 * smart-item-picker composable（D4 决议）：
 * - debounce 250ms 触发搜索
 * - AbortController 取消上一次未完成请求
 * - TTL 60s Map 缓存（最多 50 条）
 * - smart-query 解析：NS.ID / 当前 namespace identifier / 自由文本
 */
export function useSmartPicker(currentNamespace?: { id?: string; shorthandPrefix?: string } | null) {
  const hits = ref<AdministeredItemSearchHit[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const cache = new Map<string, CacheEntry>();
  let abortController: AbortController | null = null;
  let debounceTimer: ReturnType<typeof setTimeout> | null = null;
  let activeToken = 0;

  function cacheKey(opts: SmartPickerQueryOptions): string {
    const scopeKey = opts.scope === 'current'
      ? 'current'
      : opts.scope === 'all'
        ? 'all'
        : `ns:${(opts.scope as { namespaceId: string }).namespaceId}`;
    const exactKey = opts.exact ? `EXACT:${opts.exact.namespaceShorthand ?? ''}/${opts.exact.identifier}` : '';
    return `${opts.q ?? ''}|${(opts.type ?? []).sort().join(',')}|${scopeKey}|${opts.size ?? 20}|${exactKey}`;
  }

  function resolveScope(opts: SmartPickerQueryOptions): { namespaceId?: string; forceAll?: boolean } {
    if (opts.scope === 'current') {
      return currentNamespace?.id ? { namespaceId: currentNamespace.id } : { forceAll: true };
    }
    if (opts.scope && typeof opts.scope === 'object') {
      return { namespaceId: opts.scope.namespaceId };
    }
    return { forceAll: true };
  }

  /**
   * 智能查询解析（D3 决议）：
   * - `MD.VD.00001` → exact match (NS.ID)
   * - `VD.00001` in current namespace → exact match (current identifier)
   * - 中文/含空格/特殊字符 → free text fuzzy
   */
  function parseQuery(input: string): {
    mode: 'fuzzy';
    q: string;
  } | {
    mode: 'exact';
    identifier: string;
    namespaceShorthand?: string;
  } {
    const trimmed = input.trim();
    if (!trimmed) {
      return { mode: 'fuzzy', q: '' };
    }
    const nsMatch = NS_ID_PATTERN.exec(trimmed);
    if (nsMatch) {
      const dotIdx = trimmed.indexOf('.');
      const ns = trimmed.slice(0, dotIdx);
      const id = trimmed.slice(dotIdx + 1);
      return { mode: 'exact', identifier: trimmed, namespaceShorthand: ns };
    }
    if (PURE_ID_PATTERN.test(trimmed) && currentNamespace?.shorthandPrefix) {
      return {
        mode: 'exact',
        identifier: trimmed,
        namespaceShorthand: currentNamespace.shorthandPrefix,
      };
    }
    return { mode: 'fuzzy', q: trimmed };
  }

  async function execute(opts: SmartPickerQueryOptions): Promise<AdministeredItemSearchHit[]> {
    const key = cacheKey(opts);
    const cached = cache.get(key);
    if (cached && cached.expires > Date.now()) {
      return cached.hits;
    }
    if (abortController) {
      abortController.abort();
    }
    const controller = new AbortController();
    abortController = controller;
    const token = ++activeToken;
    loading.value = true;
    error.value = null;
    try {
      const params: Record<string, unknown> = {
        size: opts.size ?? 20,
      };
      if (opts.type && opts.type.length > 0) {
        params.type = opts.type.join(',');
      }
      const scope = resolveScope(opts);
      if (scope.namespaceId) {
        params.namespaceId = scope.namespaceId;
      }
      if (opts.exact) {
        params.exactMatch = true;
        params.identifier = opts.exact.identifier;
        if (opts.exact.namespaceShorthand) {
          params.namespaceShorthand = opts.exact.namespaceShorthand;
        }
      } else if (opts.q) {
        params.q = opts.q;
      }
      const res = await metadataSearchApi.search(params);
      if (token !== activeToken) {
        return [];
      }
      const list: AdministeredItemSearchHit[] = res.content || [];
      if (cache.size >= CACHE_MAX) {
        const firstKey = cache.keys().next().value;
        if (firstKey) cache.delete(firstKey);
      }
      cache.set(key, { hits: list, expires: Date.now() + CACHE_TTL_MS });
      return list;
    } catch (e: any) {
      if (token === activeToken) {
        error.value = e?.message || '搜索失败';
      }
      return [];
    } finally {
      if (token === activeToken) {
        loading.value = false;
      }
    }
  }

  function search(opts: SmartPickerQueryOptions): void {
    if (debounceTimer) {
      clearTimeout(debounceTimer);
    }
    debounceTimer = setTimeout(async () => {
      const result = await execute(opts);
      hits.value = result;
    }, DEBOUNCE_MS);
  }

  async function searchImmediate(opts: SmartPickerQueryOptions): Promise<AdministeredItemSearchHit[]> {
    if (debounceTimer) {
      clearTimeout(debounceTimer);
    }
    const result = await execute(opts);
    hits.value = result;
    return result;
  }

  async function fetchById(id: string): Promise<AdministeredItemSearchHit | null> {
    // 先查缓存（其它查询可能已包含此 id）
    for (const entry of cache.values()) {
      if (entry.expires < Date.now()) continue;
      const hit = entry.hits.find((h) => h.itemId === id);
      if (hit) return hit;
    }
    // 否则按 id 搜索
    const list = await searchImmediate({
      q: id,
      type: undefined,
      scope: 'all',
      size: 5,
    });
    return list.find((h) => h.itemId === id) || null;
  }

  function clear() {
    if (debounceTimer) clearTimeout(debounceTimer);
    if (abortController) abortController.abort();
    hits.value = [];
    loading.value = false;
    error.value = null;
  }

  onUnmounted(() => clear());

  return {
    hits,
    loading,
    error,
    search,
    searchImmediate,
    fetchById,
    parseQuery,
    clear,
  };
}