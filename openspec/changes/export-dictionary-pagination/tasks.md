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

## 5. 评审修复（code review 追加）

- [x] 5.1 MAJOR：REST 经 record 绑定天然获得分页，同步 export() 分页 totalCount 与流式同口径（全量命中数）；MAX_SYNC_EXPORT_SIZE 守卫仅约束未分页；shouldUseAsyncExport 分页恒 false
- [x] 5.2 MINOR：COUNT 与主查询 FROM/JOIN 链抽取 fromClause() 共用；intInRange 委托 intArg
- [x] 5.3 测试：export() 分页 totalCount 打桩、shouldUseAsyncExport 分页恒 false、COUNT+itemTypes 下推；单测 30 例
- [x] 5.4 产物同步：proposal/design/spec 如实表述 REST 分页能力（新增"同步导出路径分页语义一致"需求）
