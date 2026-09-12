/**
 * 快捷键绑定解析/匹配/采集工具。
 * 绑定格式：修饰键 Ctrl/Alt/Shift/Meta 有序排列 + '+' + 主键（字母大写/数字/功能键），
 * 如 "Ctrl+Shift+D"。与后端 ai-assist.yml 的校验口径保持一致。
 */

export interface ParsedHotkey {
  ctrl: boolean;
  alt: boolean;
  shift: boolean;
  meta: boolean;
  key: string;
}

/** 服务端默认合并保证存在；前端兜底默认（设置读取失败时） */
export const DEFAULT_HOTKEYS: Record<string, string> = {
  'aiDebug.toggle': 'Ctrl+Shift+D',
};

const MODIFIER_KEYS = new Set(['Control', 'Shift', 'Alt', 'Meta']);

/** 解析绑定字符串；格式非法返回 null */
export function parseHotkey(binding: string): ParsedHotkey | null {
  if (typeof binding !== 'string' || !binding.trim()) return null;
  const parts = binding.split('+').map((p) => p.trim()).filter(Boolean);
  if (parts.length === 0) return null;
  const parsed: ParsedHotkey = { ctrl: false, alt: false, shift: false, meta: false, key: '' };
  for (const part of parts) {
    const lower = part.toLowerCase();
    if (lower === 'ctrl' || lower === 'control') parsed.ctrl = true;
    else if (lower === 'alt') parsed.alt = true;
    else if (lower === 'shift') parsed.shift = true;
    else if (lower === 'meta') parsed.meta = true;
    else if (parsed.key) return null; // 多个主键，非法
    else parsed.key = normalizeKey(part);
  }
  if (!parsed.key) return null;
  return parsed;
}

/** 规范化主键：单字符转大写；其余原样（F1..F12、Escape、Space 等） */
function normalizeKey(key: string): string {
  return key.length === 1 ? key.toUpperCase() : key;
}

/** 按固定修饰键序输出绑定字符串 */
export function formatHotkey(parsed: ParsedHotkey): string {
  const parts: string[] = [];
  if (parsed.ctrl) parts.push('Ctrl');
  if (parsed.alt) parts.push('Alt');
  if (parsed.shift) parts.push('Shift');
  if (parsed.meta) parts.push('Meta');
  parts.push(parsed.key);
  return parts.join('+');
}

/** 从 keydown 事件采集绑定；仅按了修饰键返回 null（视为未捕获） */
export function captureHotkeyFromEvent(event: KeyboardEvent): string | null {
  if (MODIFIER_KEYS.has(event.key)) return null;
  return formatHotkey({
    ctrl: event.ctrlKey,
    alt: event.altKey,
    shift: event.shiftKey,
    meta: event.metaKey,
    key: normalizeKey(event.key),
  });
}

/** keydown 事件是否命中绑定 */
export function eventMatchesHotkey(event: KeyboardEvent, parsed: ParsedHotkey): boolean {
  return (
    event.ctrlKey === parsed.ctrl &&
    event.altKey === parsed.alt &&
    event.shiftKey === parsed.shift &&
    event.metaKey === parsed.meta &&
    normalizeKey(event.key).toLowerCase() === parsed.key.toLowerCase()
  );
}
