# 实施计划：add-measureclass-dimensionality-association（2026-09-21）

依据：`openspec/changes/add-measureclass-dimensionality-association/{proposal,design,specs,tasks}.md`、`docs/superpowers/specs/2026-09-21-measureclass-dimensionality-association-design.md`。模式对照刚完成的 `add-uom-measure-class-association`。

## 步骤

1. 后端 DTO：`DimensionalityRef`（dto.dimensionality）、`MeasureClassRequest/Response`（dto.measureclass）；`DimensionalityRepository.findByMeasureClasses_Id`。
2. TDD：`MeasureClassServiceTest` 补关联用例 → `MeasureClassService` 换专用 DTO + `applyDimensionalities` 反向调和（null/空=清空、404、差异增删维度侧 measureClasses、内存双向同步、create 先 save 取 id）；`MeasureClassController` 换 DTO。
3. 前端：`MeasureClassList.vue` 维度多选 + 维度数列 + 回填。
4. 验证：web 全量 mvn test、vitest + build、openapi.json 导出（`-Dtest=OpenApiExportTest -Dopenapi.export=true`）、scratch 冒烟（复制 uom-measure-class-smoke 改造）、detect_changes。

## 环境要点（沿用 UoM 变更冒烟经验）

- 多实例：`mvn spring-boot:run` + `SPRING_APPLICATION_JSON`（副本库，绕开用户锁定的 jar）。
- 冒烟载荷纯 ASCII 且无空格（命名规范正则拒绝空格）；DELETE 业务错误 HTTP 状态为 400，断言看 body code。
- SQLite 64 表 join 限制：带 AdministeredItem 的条目 update 会 500（既有平台限制）——冒烟中计量类以**无注册块**创建以验证更新语义；维度创建可带 registration（维度侧既有代码，其 PUT 在维度冒烟已验证通过——维度无 AdministeredItem 时也正常；为稳妥同样不带注册块）。
