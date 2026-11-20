# 规格：导出分页（export-pagination）

## ADDED Requirements

### Requirement: 导出请求可选分页参数

The system SHALL accept optional `page` (0-based) and `size` pagination fields on metadata export requests. When both are absent, the export SHALL behave exactly as before (no pagination). Both fields MUST be present together; `page` MUST be ≥ 0; `size` MUST be between 1 and 10000 inclusive. Violations SHALL be rejected with `IllegalArgumentException` before any query executes.

### Requirement: 分页 SQL 方言下推

When pagination is requested, the export main query SHALL append a deterministic `ORDER BY ai.creation_date_time, ai.id` (stable ordering across pages) and dialect-specific paging:

- SQL Server: `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY` with parameters `(page * size, size)`
- SQLite / 达梦: `LIMIT ? OFFSET ?` with parameters `(size, page * size)`

Paging clauses SHALL be parameter-bound (no string interpolation of user input).

### Requirement: 分页时 totalCount 报告全量命中数

For paginated JSON exports, `exportMetadata.totalCount` SHALL equal the total number of matching records (obtained via a COUNT query sharing the main query's WHERE assembly), so clients can determine when to stop paging. For unpaginated exports, `totalCount` SHALL remain the number of items in the result (current behavior). Out-of-range pages SHALL return an empty `items` array with the unchanged full `totalCount`.

### Requirement: MCP 工具透传分页

The MCP tool `export-data-dictionary` SHALL accept optional `page` (integer, 0..100000) and `size` (integer, 1..1000) arguments and pass them to the export service. Absent arguments SHALL produce unpaged exports (current behavior).

### Requirement: CSV 导出同样分页

`exportCsvToStream` SHALL apply the same pagination semantics (each page returned as a complete CSV text with BOM and header).
