import { describe, it, expect } from 'vitest';
import {
  parseHotkey,
  formatHotkey,
  captureHotkeyFromEvent,
  eventMatchesHotkey,
} from './hotkey';

describe('hotkey 工具', () => {
  it('解析合法绑定（修饰键有序排列 + 主键大写）', () => {
    expect(parseHotkey('Ctrl+Shift+D')).toEqual({
      ctrl: true, alt: false, shift: true, meta: false, key: 'D',
    });
    expect(parseHotkey('ctrl+alt+j')).toEqual({
      ctrl: true, alt: true, shift: false, meta: false, key: 'J',
    });
    expect(parseHotkey('F5')!.key).toBe('F5');
  });

  it('非法绑定返回 null：无主键、多主键、空串', () => {
    expect(parseHotkey('Ctrl+Shift')).toBeNull();
    expect(parseHotkey('Ctrl+A+B')).toBeNull();
    expect(parseHotkey('')).toBeNull();
  });

  it('formatHotkey 按固定序输出', () => {
    expect(formatHotkey({ ctrl: true, alt: true, shift: true, meta: false, key: 'D' }))
      .toBe('Ctrl+Alt+Shift+D');
  });

  it('captureHotkeyFromEvent：仅修饰键返回 null，否则输出绑定串', () => {
    expect(captureHotkeyFromEvent(new KeyboardEvent('keydown', { key: 'Control', ctrlKey: true })))
      .toBeNull();
    expect(
      captureHotkeyFromEvent(new KeyboardEvent('keydown', { key: 'j', ctrlKey: true, altKey: true })),
    ).toBe('Ctrl+Alt+J');
  });

  it('eventMatchesHotkey：修饰键与主键全等才命中', () => {
    const parsed = parseHotkey('Ctrl+Shift+D')!;
    expect(eventMatchesHotkey(new KeyboardEvent('keydown', { key: 'D', ctrlKey: true, shiftKey: true }), parsed)).toBe(true);
    expect(eventMatchesHotkey(new KeyboardEvent('keydown', { key: 'D', ctrlKey: true }), parsed)).toBe(false);
    expect(eventMatchesHotkey(new KeyboardEvent('keydown', { key: 'd', ctrlKey: true, shiftKey: true }), parsed)).toBe(true);
    expect(eventMatchesHotkey(new KeyboardEvent('keydown', { key: 'E', ctrlKey: true, shiftKey: true }), parsed)).toBe(false);
  });
});
