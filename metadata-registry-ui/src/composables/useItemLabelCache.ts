import { reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { metadataSearchApi } from '@/api/resources';
import type { AdministeredItemSearchHit } from '@/types/common';

/**
 * useItemLabelCache — 条目 id → 业务标识（identifier）与指称（designation）的会话级共享缓存。
 *
 * 界面禁显 UUID 原则：任何用户可见位置一律经本缓存反查业务标识与指称；
 * 未解析时返回 i18n 占位（未知项目 / 加载中…），绝不回退为 id 或短码。
 *
 * - 批量微批（50ms 窗口）去重反查，避免 N+1 与重复请求；
 * - 模块级 Map 缓存，跨组件/跨页面复用；
 * - 缓存存结构化 { identifier, designation }，派生两种显示：
 *   - labelFor()      「标识符 | 指称」（缺段省略）
 *   - designationFor() 仅指称，缺指称回退业务标识，再缺"未知项目"
 */

type LabelState =
  | { status: 'loading' }
  | { status: 'resolved'; identifier: string | null; designation: string | null }
  | { status: 'unknown' };

const cache = reactive(new Map<string, LabelState>());
const pending = new Set<string>();
let flushTimer: ReturnType<typeof setTimeout> | null = null;
const FLUSH_DELAY_MS = 50;

/** 缓存变更版本号：非模板消费方（如 ECharts 图）watch 它以触发重绘 */
const version = ref(0);

function parseHit(hit: AdministeredItemSearchHit): { identifier: string | null; designation: string | null } {
  const identifier = hit.identifier || null;
  const designation = hit.itemLabel && hit.itemLabel !== hit.identifier ? hit.itemLabel : null;
  return { identifier, designation };
}

function joinParts(identifier: string | null, designation: string | null): string {
  return [identifier, designation].filter(Boolean).join(' | ');
}

async function resolveOne(itemId: string): Promise<void> {
  try {
    const res = await metadataSearchApi.search({ q: itemId, size: 5 });
    const hit = (res.content || []).find((h) => h.itemId === itemId);
    if (hit) {
      const { identifier, designation } = parseHit(hit);
      cache.set(itemId, { status: 'resolved', identifier, designation });
    } else {
      cache.set(itemId, { status: 'unknown' });
    }
  } catch {
    cache.set(itemId, { status: 'unknown' });
  } finally {
    version.value++;
  }
}

function flushPending(): void {
  const batch = Array.from(pending);
  pending.clear();
  batch.forEach(resolveOne);
}

export function useItemLabelCache() {
  const { t } = useI18n();

  /** 请求解析（已缓存/已挂起则跳过） */
  function request(itemId: string | null | undefined): void {
    if (!itemId || cache.has(itemId) || pending.has(itemId)) return;
    cache.set(itemId, { status: 'loading' });
    pending.add(itemId);
    if (flushTimer) clearTimeout(flushTimer);
    flushTimer = setTimeout(flushPending, FLUSH_DELAY_MS);
  }

  function requestAll(ids: Array<string | null | undefined>): void {
    ids.forEach(request);
  }

  /** 模板直接绑定：加载中/未知/「标识符 | 指称」 */
  function labelFor(itemId: string | null | undefined): string {
    if (!itemId) return '—';
    const state = cache.get(itemId);
    if (!state || state.status === 'loading') return t('common.loading');
    if (state.status === 'unknown') return t('common.unknownEntry');
    const text = joinParts(state.identifier, state.designation);
    return text || t('common.unknownEntry');
  }

  /** 仅指称：指称缺失回退业务标识，再缺"未知项目"（绝不显示 id） */
  function designationFor(itemId: string | null | undefined): string {
    if (!itemId) return '—';
    const state = cache.get(itemId);
    if (!state || state.status === 'loading') return t('common.loading');
    if (state.status === 'unknown') return t('common.unknownEntry');
    return state.designation || state.identifier || t('common.unknownEntry');
  }

  /** 仅当已解析时返回「标识符 | 指称」文本，否则 null（摘要/确认消息等"不显示则省略"场景） */
  function resolvedText(itemId: string | null | undefined): string | null {
    const state = itemId ? cache.get(itemId) : undefined;
    if (state?.status !== 'resolved') return null;
    return joinParts(state.identifier, state.designation) || null;
  }

  return { request, requestAll, labelFor, designationFor, resolvedText, version };
}
