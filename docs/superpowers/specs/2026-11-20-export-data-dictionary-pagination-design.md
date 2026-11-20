# 设计稿：export-data-dictionary 分页导出（2026-11-20）

## 1. 问题定义（数据实证）

MCP 工具 `export-data-dictionary` 无分页参数：服务端 `MetadataExportService.queryExports()`
一次性将全量命中行载入内存，MCP 层再整体缓冲为单个 JSON-RPC 响应返回。本会话两次全量导出
（值域 2156 条 ≈1.7MB、数据元 2212 条 ≈1.9MB）均由会话管道转存文件才得以处理；全库导出
（1.1 万+ 条，约 9MB+）将显著放大内存与传输压力，且 LLM 侧单次工具结果有上下文上限。
REST 同步路径虽有 `MAX_SYNC_EXPORT_SIZE=10000` 上限，但 MCP 走的是无上限的
`exportJsonToStream/exportCsvToStream` 流式路径——防意外需要工具自身支持分批获取。

## 2. 方案（选定：请求级 page/size，方言分页下推）

**备选**：① 键集游标（keyset，按 id 游标）——翻页稳定且高效，但客户端要维护游标，MCP 工具
语义偏重；② offset/limit 页码分页——实现直白、与 `browse-data-dictionary` 的 page/size
语义一致，万级数据 offset 成本可接受。选 ②，与现有 MCP 分页习惯对齐。

### 2.1 请求模型

`MetadataExportRequest` 追加两个可空字段：`Integer page`（从 0 起）、`Integer size`。
- 均为 null → 不分页，行为与现状完全一致（REST 不受影响）；
- 紧凑构造器校验：page ≥ 0；size 1..10000；两者必须同有同无（违反即 IllegalArgumentException）。
- MCP 工具侧 `size` 上限收紧为 1000（`ToolSchemas.integerProp`），与 browse 的 100 默认/上限
  习惯相近但允许更大批。

### 2.2 SQL 下推（MetadataExportService）

- `buildExportQuery`：ORDER BY 追加确定性 tiebreaker `, ai.id`（原 `ai.creation_date_time`
  不唯一，分页必须稳定序）；分页时方言分支——
  - SQL Server：`OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`（参数 page*size, size）
  - 其他（SQLite/达梦）：`LIMIT ? OFFSET ?`（参数 size, page*size）
- 抽取 WHERE 组装为独立方法，供 SELECT 与新增 COUNT 复用；新增 `buildCountQuery`：
  `SELECT COUNT(*) FROM administered_item ai JOIN registered_item ri ... <同 WHERE>`。
- `queryExports` 不变（主查询已分页）；新增 `countExports(request)`。
- `exportJsonToStream`：paged 请求时 `exportMetadata.totalCount` 取全量命中数（countExports），
  便于客户端判定终止条件；未分页时维持现语义（= items.size()）。
- `exportCsvToStream`：分页同样生效（每页一个 CSV 文本，BOM+表头保留）。

### 2.3 MCP 工具（ExportDataDictionaryTool）

`inputSchema` 增加可选 `page`（integerProp 0..100000）与 `size`（integerProp 1..1000）；
description 补充分页用法说明。handle 解析后透传 request。

## 3. 影响面

- 调用方仅两处：REST `MetadataExportController`（不传分页字段，行为不变）与 MCP 工具。
- 文本搜索核实调用方（图谱 impact 对本方法名解析失真，已用 grep 佐证）：无其他调用方。
- 不改 `export()` 同步上限语义、不改 CSV/JSON 结构（仅 metadata.totalCount 在分页时语义增强）。

## 4. 验收

1. 单测（MetadataExportServiceTest）：
   - paged SQL（SQLite 方言）含 `LIMIT ? OFFSET ?`，参数顺序 [..., size, page*size]；
   - ORDER BY 含 `ai.id` tiebreaker；
   - SQL Server 方言（改 env）含 `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`；
   - 校验异常：page<0 / size=0 / size>10000 / 仅传其一；
   - countExports SQL 与主查询 WHERE 一致。
2. 集成测试（MetadataExportSqliteIntegrationTest）：paged JSON 导出首页 size 条、
   totalCount 为全量；末页可能不足 size；越界页空数组且 totalCount 不变。
3. 工具测试（MetadataReadToolsTest）：schema 含 page/size；handle 透传。
4. `mvnw -pl metadata-registry-web test` 相关测试全绿。
