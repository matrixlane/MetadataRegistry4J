# 提案：export-data-dictionary 分页导出（export-dictionary-pagination）

## Why

MCP 工具 `export-data-dictionary` 无分批能力：`MetadataExportService.queryExports()` 一次性将全量命中行载入内存，MCP 层再整体缓冲为单个 JSON-RPC 响应。实测全量导出值域 2156 条 ≈1.7MB、数据元 2212 条 ≈1.9MB，已需会话管道转存文件才能处理；全库 1.1 万+ 条（约 9MB+）将放大内存与传输压力，且 Agent 侧单次工具结果存在上下文上限。REST 同步路径有 `MAX_SYNC_EXPORT_SIZE=10000` 上限，但 MCP 走的是无上限的流式路径——防意外需要工具自身支持分批获取。

## What Changes

- `MetadataExportRequest` 追加可空 `Integer page`（从 0 起）与 `Integer size`：均为 null 不分页（REST 与既有 MCP 调用行为不变）；紧凑构造器校验 page ≥ 0、size 1..10000、两者同有同无。
- `MetadataExportService.buildExportQuery`：ORDER BY 追加 `ai.id` tiebreaker（原 `creation_date_time` 不唯一，分页需稳定序）；分页时方言下推——SQL Server `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`，SQLite/达梦 `LIMIT ? OFFSET ?`。
- 新增 `countExports(request)`（与主查询同 WHERE 的 COUNT），`exportJsonToStream` 在分页请求时以全量命中数填充 `exportMetadata.totalCount`，客户端据其判定终止；未分页维持现语义（= 本批条数）。
- MCP 工具 `export-data-dictionary` schema 增加可选 `page`（0..100000）与 `size`（1..1000）并透传；description 补充分批用法。
- CSV 导出同样支持分页（每页一个完整 CSV 文本）。

## Capabilities

### New Capabilities

- `export-pagination`: 注册元数据导出的请求级分页语义——page/size 可空、同有同无；SQL 方言分页下推与确定性排序；分页时 totalCount 报告全量命中数；MCP 导出工具透传分页参数。

### Modified Capabilities

（无。REST 导出不传分页字段，行为完全不变。）
