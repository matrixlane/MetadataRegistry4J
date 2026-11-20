# 设计：export-data-dictionary 分页导出

设计全文见 `docs/superpowers/specs/2026-11-20-export-data-dictionary-pagination-design.md`，此处摘要关键决策。

## 决策

1. **offset/limit 页码分页**（非键集游标）：与 `browse-data-dictionary` 的 page/size 语义一致、实现直白；万级数据 offset 成本可接受。
2. **可空参数、默认不变**：`page/size` 均为 null → 不分页，REST `MetadataExportController` 与既有 MCP 调用零影响。
3. **方言下推**：SQL Server `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`；SQLite/达梦 `LIMIT ? OFFSET ?`；参数绑定防注入。ORDER BY `ai.creation_date_time, ai.id` 保证稳定序。
4. **totalCount 语义**：分页时 `exportMetadata.totalCount` = 全量命中数（COUNT 查询与主查询共用 WHERE 组装）；未分页时 = 本批条数（现状）。
5. **MCP 层 size 上限 1000**；服务层校验 1..10000。

## 影响面（文本搜索核实）

- `MetadataExportController`（REST，唯一另一调用方）不传新字段，行为不变。
- 不改 `export()` 的 10000 条同步上限语义；不改 CSV/JSON 结构。
