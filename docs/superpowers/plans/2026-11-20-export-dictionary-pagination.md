# 实施计划：export-data-dictionary 分页导出（2026-11-20）

依据：`docs/superpowers/specs/2026-11-20-export-data-dictionary-pagination-design.md` +
`openspec/changes/export-dictionary-pagination/tasks.md`。

步骤（TDD：每步先测试后实现）：

1. `MetadataExportRequest` 增加 `Integer page, Integer size` + 紧凑构造器校验（测试 1.4 先行）。
2. `MetadataExportService`：
   - WHERE 组装抽取为私有方法，主查询 ORDER BY 追加 `, ai.id`；
   - 方言分页子句（SQL Server OFFSET/FETCH；其他 LIMIT/OFFSET）；
   - `countExports(request)` COUNT 查询复用 WHERE；
   - `exportJsonToStream` 分页时 totalCount=全量命中数。
3. `ExportDataDictionaryTool` schema 增加 page/size 并透传。
4. 集成测试（SQLite 真库）验证首页/越界页/totalCount。
5. `mvnw -pl metadata-registry-web test` 验证 + 实机 MCP 抽查。
