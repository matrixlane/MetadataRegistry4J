/**
 * 参考文档 URI 分类：决定打开方式（后端代理读取 vs 直接打开）。
 *
 * 本地文件（盘符/UNC/file://）与仓库内相对路径（基准数据随包携带的标准文档，
 * 如 data/docs/data-standards/ws/WS363/….pdf）都经后端 `GET /reference-documents/{id}/content`
 * 代理读取；仅 http(s) 绝对地址由前端直接打开。
 */

/** Windows 盘符路径（E:\...）、UNC（\\server\share）或 file:// URI。 */
export function isLocalFileUri(raw: string): boolean {
  return /^[a-zA-Z]:[\\/]/.test(raw) || raw.startsWith('\\\\') || /^file:\/\//i.test(raw);
}

/**
 * 仓库内相对路径：无 URI scheme（http:/file: 等）、非协议相对（//host）。
 * 由后端相对应用工作目录解析（越出工作目录会被后端拒绝）。
 */
export function isRepoRelativePath(raw: string): boolean {
  const p = raw.trim();
  if (!p || p.startsWith('//')) {
    return false;
  }
  return !/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(p);
}
