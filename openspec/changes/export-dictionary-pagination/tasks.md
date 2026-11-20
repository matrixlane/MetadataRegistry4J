# 任务：export-data-dictionary 分页导出

## 1. 服务层（TDD：先写测试）

- [x] 1.1 `MetadataExportServiceTest`：分页 SQL（SQLite 方言）含 `LIMIT ? OFFSET ?`，参数顺序 `[..., size, page*size]`
- [x] 1.2 `MetadataExportServiceTest`：ORDER BY 含 `ai.id` tiebreaker
- [x] 1.3 `MetadataExportServiceTest`：SQL Server 方言（改 env）含 `OFFSET ? ROWS FETCH NEXT ? ROWS ONLY`
- [x] 1.4 `MetadataExportServiceTest`：校验异常——page<0、size=0、size>10000、仅传其一
- [x] 1.5 `MetadataExportServiceTest`：`countExports` SQL 与主查询 WHERE 一致（含类型/状态过滤）
- [x] 1.6 实现：`MetadataExportRequest` 增加 page/size + 紧凑构造器校验
- [x] 1.7 实现：`buildExportQuery` 抽取 WHERE 组装、加 tiebreaker 与方言分页；新增 `countExports`
- [x] 1.8 实现：`exportJsonToStream` 分页时 totalCount 取全量命中数

## 2. 集成测试

- [x] 2.1 `MetadataExportSqliteIntegrationTest`：paged JSON 导出首页 size 条、totalCount 为全量
- [x] 2.2 越界页空数组且 totalCount 不变

## 3. MCP 工具层

- [x] 3.1 `ExportDataDictionaryTool` schema 增加 page/size 并透传；description 补充分批用法
- [x] 3.2 `MetadataReadToolsTest`：schema 含 page/size；handle 透传断言

## 4. 验证

- [x] 4.1 `mvnw -pl metadata-registry-web test -Dtest='MetadataExport*,MetadataReadToolsTest,McpToolCatalogTest'` 全绿
- [x] 4.2 实机抽查：MCP `export-data-dictionary`（page=0,size=100）返回 100 条且 totalCount=2156
