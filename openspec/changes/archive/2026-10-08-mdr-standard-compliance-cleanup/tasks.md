# Tasks: mdr-standard-compliance-cleanup

## 1. 环境与基线

- [x] 1.1 创建隔离 worktree `.worktrees/mdr-standard-cleanup`（基于 master HEAD），主树 118 项并行 WIP 不动
- [x] 1.2 worktree 内跑通基线：web 模块全量测试 + vitest 基线失败集记录（区分既有失败）

## 2. JPA 实体与映射

- [x] 2.1 9 张表更名落注解：`ConceptualDomain`(@JoinTable)、`DesignationContext`/`DefinitionContext`(@Table)、`ItemMappingSubject`/`ItemMappingObject`(@Table)、`Designation`/`Namespace`(@JoinTable)、`UnitOfMeasure`(@JoinTable)、`SubmissionRecord`(@JoinTable→`registered_item_submission_record`)
- [x] 2.2 删字段：`SubmissionRecord.source` + `SubmissionRecordSource` 枚举删除；`Designation`/`Definition` 的 `acceptability` 字段删除
- [x] 2.3 `ValueMeaning` 弃 `@JoinTable(value_meaning_permissible_value_meaning)`，改 `@OneToMany(mappedBy="valueMeaning")`（`PermissibleValueMeaning` 保持 `@ManyToOne value_meaning_id`）
- [x] 2.4 `orm-team.xml` 追加 transition 三实体 `schema="ext"`；`OrmTeamXmlTest` 清单与计数同步；`application-team.yml` jpa 注释口径更新
- [x] 2.5 原生 SQL 旧表名引用改造：`MetadataSearchService`、`RegistrationAuthorityDeletionService`、`DimensionalityService`、`ContextTestDataInitializer`、`SubmissionRecordRepository`、`UnitOfMeasureRepository`、`MeasureClassService`、`UnitOfMeasureRequest`（含注释）

## 3. 服务与 DTO 层

- [x] 3.1 `ItemAdministrationService.recordSubmission`/`recordSubmissionForBatch` 去 `source` 参数与必传断言；`WorkspaceService` 2 处调用点同步；`SubmissionRecordSummary` DTO 删 `source`（TDD：先改测试）
- [x] 3.2 `DesignationResponse`/`DefinitionRequest`/`DefinitionResponse`/`DesignationDefinitionPairingRequest`/`TextLine` 系列 DTO 删主记录级 `acceptability`；`ItemAdministrationService`(1061/1081/1094/1106)、`VersionHistoryService`(254/274)、`ItemRelationshipService`、`DefinitionService`、`DesignationService` 中直接读写点改经 context 条目（保留 Pairing 上的 acceptability）
- [x] 3.3 检查 `DataDictionaryBrowserController`/`MetadataSearchController` 对 acceptability 的透出并适配

## 4. 前端

- [x] 4.1 `types/common.ts` 删 `SubmissionRecordSource` 类型与 `SubmissionRecordSummary.source`；引用组件（提交记录面板、tag 渲染）清理
- [x] 4.2 `DesignationEditor`/`DefinitionEditor`/`MultilingualTextEditor`/`ItemFormDialog` 主记录级 acceptability 输入移除；context 条目编辑保留；相关 spec 更新
- [x] 4.3 `vitest` + `vue-tsc` + 前端 build

## 5. 三方言迁移 V20261009001

- [x] 5.1 SQL Server 版：删 3 测试表 + `value_meaning_permissible_value_meaning`；9 张表 `sp_rename`；三表 `ALTER SCHEMA ext TRANSFER`；16 列删除（先经 `sys.foreign_key_columns`/`sys.default_constraints` 动态清约束）；全 IF EXISTS 守卫
- [x] 5.2 SQLite 版：同名步骤（RENAME TABLE / DROP COLUMN 直写）；transition 三表 no-op；测试用真实 Flyway 重放（既有库 + 全新库双路径）
- [x] 5.3 达梦版：更名/删列按 V20260901009 达梦版式（ALL_TABLES/ALL_TAB_COLUMNS 守卫 + EXECUTE IMMEDIATE）；TRANSFER 段跳过并注释（遗留任务）
- [x] 5.4 迁移与既有 V20260901003/09–13 的顺序执行一致性核对（既有库补应用 + 全新库全量重放）

## 6. 验证

- [x] 6.1 worktree 内 `mvn -pl metadata-registry-web -am test` 全量（新增/改造测试红→绿，既有失败集不扩大）
- [x] 6.2 真实 SQL Server 库：新包应用 V20260901009 → 重启（FlywayHistoryRelocator 生效：`mdr.flyway_schema_history` 消失、`ext.flyway_schema_history` 行数=21、无 re-baseline）→ 全量冒烟（mdr 表/列对照清单、9 新名表、ext 三表、工作空间提交、指称/定义编辑器、计量单位页、item-mapping 页）
- [x] 6.3 SQLite scratch 库冒烟；openapi.json 再生成
- [x] 6.4 `openspec validate --all`；gitnexus `detect_changes`（无 partial/truncated）；`docs/architecture/ARCHITECTURE.md` 受影响检查

## 7. 收尾

- [ ] 7.1 更新 `.superpowers-memory`（CURRENT_STATE + session journal）
- [ ] 7.2 提交（按项目规范 commit），合并回主树并处理并行 WIP 仲裁
- [ ] 7.3 归档（continue-review → continue-archive）
