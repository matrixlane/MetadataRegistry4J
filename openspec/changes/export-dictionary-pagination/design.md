# 设计：export-data-dictionary 分页导出

设计全文见 `docs/superpowers/specs/2026-11-20-export-data-dictionary-pagination-design.md`，此处摘要关键决策。

## 决策

1. **offset/limit 页码分页**（非键集游标）：与 `browse-data-dictionary` 的 page/size 语义一致、实现直白；万级数据 offset 成本可接受。
2. **可空参数、默认不变**：`page/size` 均为 null → 不分页，REST `MetadataExportController` 与既有 MCP 调用零影响。
3. **方言下推**：SQL Server `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`；SQLite/达梦 `LIMIT ? OFFSET ?`；参数绑定防注入。ORDER BY `ai.creation_date_time, ai.id` 保证稳定序。
4. **totalCount 语义**：分页时 `exportMetadata.totalCount` = 全量命中数（COUNT 查询与主查询共用 WHERE 组装）；未分页时 = 本批条数（现状）。
5. **MCP 层 size 上限 1000**；服务层校验 1..10000。

## 影响面（文本搜索核实）

- 调用方仅两处：REST `MetadataExportController` 与 MCP 工具。控制器直接绑定 `MetadataExportRequest`（POST `@RequestBody` / GET 查询参数构造器绑定），page/size 对 REST 天然生效——评审后将其纳入规格而非屏蔽：同步 `export()` 分页 totalCount 与流式同口径（全量命中数），`shouldUseAsyncExport` 分页恒 false，1 万条守卫仅约束未分页请求。
- 不改未分页行为；CSV/JSON 结构不变。
