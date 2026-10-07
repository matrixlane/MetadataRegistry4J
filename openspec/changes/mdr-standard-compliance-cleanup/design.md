# Design: mdr-standard-compliance-cleanup

> 详细设计已获用户确认，见 `docs/superpowers/specs/2026-10-08-mdr-standard-compliance-cleanup-design.md`。本文只记录关键决策。

## Decisions

### D1 单批次迁移 V20261009001（三方言同号、全幂等）

一个迁移版本承载全部 DDL：SQL Server 用 `IF EXISTS`（INFORMATION_SCHEMA/sys）守卫；SQLite 沿用直写 DDL 惯例；达梦用 `ALL_TABLES/ALL_TAB_COLUMNS` 计数 + `EXECUTE IMMEDIATE`。既有 V20260901003/09–13 守卫式迁移与本脚本顺序执行结果一致（前者"列存在才回填+删"，本脚本"列存在才删"），无冲突。

### D2 表更名 9 张（用户调整后的名单）

`conceptual_domain_dec→data_element_concept_domain`（11179-31 §7.2.3.4）、`designation_context_relation→designation_context`（§8.5.2）、`definition_context_relation→definition_context`（§8.5.1）、`item_mapping_subject→subject_mapping`（§11.4.1）、`item_mapping_object→object_mapping`（§11.4.2）、`designation_naming_convention→naming_convention_conformance`（§8.6.5）、`namespace_naming_convention→naming_convention_utilization`（§8.6.6）、`unit_of_measure_measure_class→unit_of_measure_class`（11179-31 §7.5.3.2）、`submission_record_registered_item→registered_item_submission_record`（用户决定，弃用标准泛名 `submission`）。`registered_item_reference_document` 维持现名（用户决定）。

### D3 删列 16 个、零数据搬迁

14 个残留列（C1/C2 清单）全部已核实 0 行非空；`designation`/`definition.acceptability` 各 2 行非空值在 context 行已有同值副本（Registry Context `00000000-0000-0000-0000-000000000002`）。SQL Server 侧经 `sys.foreign_key_columns`/`sys.default_constraints` 动态查出引用约束先删（FK 为 Hibernate 哈希名，不能硬编码）。

### D4 transition 三表迁 ext：类不迁模块

沿用 `Account` 先例（orm-team.xml 中"common-facilities(1): IAM 扩展, 类不迁模块"）：`RegistrationTransitionRule/Approval/RuleApprover` 保留在 common-facilities，`orm-team.xml` 追加 `<table schema="ext"/>`；`OrmTeamXmlTest` 计数同步。SQL Server `ALTER SCHEMA ext TRANSFER`（40 行数据随迁，跨 schema FK 合法）；SQLite 天然 no-op；达梦跳过（单独任务）。

### D5 flyway_schema_history 归位：复用既有机制，不重复搬迁

`FlywayHistoryRelocator`（2026-10-01 归档的 `relocate-flyway-history-to-ext`）在 Flyway 探测前自动 TRANSFER。本变更只做真实库重启验证（其遗留 tasks 3.4）：`OBJECT_ID('mdr.flyway_schema_history') IS NULL`、`ext.flyway_schema_history` 行数=21、无 re-baseline 日志。

### D6 submission_record.source 全栈移除

实体字段 + `SubmissionRecordSource` 枚举 + `ItemAdministrationService.recordSubmission` 签名与必传断言 + `WorkspaceService` 2 处 + DTO `SubmissionRecordSummary` + 前端 `types/common.ts` 及组件 + 测试。审计确认现库 source 全 NULL，无 SYSTEM_DRAFT 实际标注数据。

### D7 designation/definition.acceptability 语义归位

实体删字段；DTO（`DesignationResponse`/`DefinitionRequest`/`DefinitionResponse`/`DesignationDefinitionPairingRequest`/`TextLine`）与 `ItemAdministrationService`、`VersionHistoryService` 直接读写点改经 context 条目；前端编辑器主记录级 acceptability 输入移除，context 条目编辑保留（既有能力）。依据：标准 Table 14/15 未列该属性，§8.7.1 正文仅提及 Context/Pairing 上的 acceptability。

### D8 ValueMeaning 映射去冗余

`ValueMeaning` 弃 `@JoinTable(value_meaning_permissible_value_meaning)`，改 `@OneToMany(mappedBy="valueMeaning")`；迁移删空表。`PermissibleValue` 侧 `@JoinTable(permissible_value_pvm)` 不变。

## Risks

- 主记录 acceptability 是 API 形状变更，前端组件/e2e 可能引用——以 `vitest` + `vue-tsc` + 冒烟兜底；
- 并行会话 WIP 若先提交同文件，合并时按文件仲裁；
- 达梦迁移延续"待实测"状态。
