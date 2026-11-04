import { describe, expect, it } from 'vitest';
import { isLocalFileUri, isRepoRelativePath } from './refdocUri';

describe('refdocUri', () => {
  it('识别本地文件 URI（盘符/UNC/file://）', () => {
    expect(isLocalFileUri('D:\\docs\\a.pdf')).toBe(true);
    expect(isLocalFileUri('e:/标准/a.pdf')).toBe(true);
    expect(isLocalFileUri('\\\\server\\share\\a.pdf')).toBe(true);
    expect(isLocalFileUri('file:///local/a.pdf')).toBe(true);
    expect(isLocalFileUri('https://example.com/a.pdf')).toBe(false);
    expect(isLocalFileUri('data/docs/data-standards/ws/WS363/a.pdf')).toBe(false);
  });

  it('识别仓库内相对路径（无 scheme、非协议相对）', () => {
    expect(isRepoRelativePath('data/docs/data-standards/ws/WS363/a.pdf')).toBe(true);
    expect(isRepoRelativePath('data\\docs\\a.pdf')).toBe(true);
    expect(isRepoRelativePath('../outside.pdf')).toBe(true); // 是否越出由后端拒绝
    expect(isRepoRelativePath('https://example.com/a.pdf')).toBe(false);
    expect(isRepoRelativePath('http://example.com')).toBe(false);
    expect(isRepoRelativePath('file:///local/a.pdf')).toBe(false);
    expect(isRepoRelativePath('//example.com/a.pdf')).toBe(false);
    expect(isRepoRelativePath('E:\\a.pdf')).toBe(false); // 盘符含 scheme 形态
    expect(isRepoRelativePath('  ')).toBe(false);
  });
});
