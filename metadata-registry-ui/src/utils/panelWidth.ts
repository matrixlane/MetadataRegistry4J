/** AI 交互调试侧栏宽度调节的纯函数与 localStorage 持久化。 */

export const AI_DEBUG_WIDTH_STORAGE_KEY = 'mdr.aiDebug.width';

export const MIN_PANEL_WIDTH = 360;

/** 首次打开（无 localStorage 记录）的默认宽度：视口的 1/5 */
export const DEFAULT_PANEL_WIDTH_VW = 20;

/**
 * 默认宽度：视口的 20%（不大于全屏宽度 1/5），仍受 360px 下限约束（窄视口下取下限）。
 */
export function defaultPanelWidth(viewportWidth: number): number {
  return clampPanelWidth((viewportWidth * DEFAULT_PANEL_WIDTH_VW) / 100, viewportWidth);
}

/**
 * 约束侧栏宽度：最小 360px、最大 90vw（视口过窄时收缩到不小于最小值）。
 */
export function clampPanelWidth(width: number, viewportWidth: number): number {
  const max = Math.max(MIN_PANEL_WIDTH, Math.floor(viewportWidth * 0.9));
  return Math.min(Math.max(Math.round(width), MIN_PANEL_WIDTH), max);
}

/** 读取记住的宽度；无值或非法返回 null */
export function loadStoredPanelWidth(storage: Storage = window.localStorage): number | null {
  const raw = storage.getItem(AI_DEBUG_WIDTH_STORAGE_KEY);
  if (!raw) return null;
  const value = Number(raw);
  if (!Number.isFinite(value) || value <= 0) return null;
  return value;
}

export function storePanelWidth(width: number, storage: Storage = window.localStorage) {
  storage.setItem(AI_DEBUG_WIDTH_STORAGE_KEY, String(Math.round(width)));
}
