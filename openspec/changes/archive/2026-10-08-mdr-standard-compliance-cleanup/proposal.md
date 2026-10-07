# Proposal: mdr-standard-compliance-cleanup

## Why

2026-10-08 对 `mdr` schema 全量审计（90 表 / 312 列逐一对照 ISO/IEC 11179-3:2023 与 11179-31:2023 原文）发现三类违反项目分层强制规则（mdr 只装标准列、扩展一律 ext）的残留：

1. 标准之外的多余表：3 张 `*$test_item` 测试垃圾表（0 行无引用）、`value_meaning_permissible_value_meaning` 冗余映射表（与 `permissible_value_meaning.value_meaning_id` 重复）、`registration_transition_*` 三表（八个标准分部均不存在此概念，40 行活数据）；
2. 与标准命名不一致的表 9 张（如 `conceptual_domain_dec` 应为 `data_element_concept_domain`，ISO/IEC 11179-31 §7.2.3.4）；
3. 表名正确但含非标准字段：`namespace` 4 列、`scoped_identifier` 7 列、`naming_convention.rules_json`、`language_identification.enabled`、`contact.account_id`、`data_type_scheme.reference`、`slot.value`/`value_datatype`、`role` 内联 `email_address`/`phone_number`（以上均为 0 行非空的剥离残留）；在用非标准列 `submission_record.source`、`designation.acceptability`、`definition.acceptability`。

根因：本机真实库的 flyway 历史缺少 2026-09-01 剥离批次（仅 20260918002 起 21 个迁移），代码侧早已完成剥离但物理列未删。

## What Changes

- **迁移 `V20261009001`**（sqlite/sqlserver/dm 同号，全部幂等守卫）：删 3 张测试表与冗余表；9 张表更名；三表 `ALTER SCHEMA ext TRANSFER`（达梦跳过，遗留任务）；删 16 个非标准列（SQL Server 侧先动态清除引用约束）；`flyway_schema_history` 不重复搬迁——复用已归档的 `FlywayHistoryRelocator` 启动归位 ext 并做真实库验证。
- **JPA 同步**：9 处 `@Table`/`@JoinTable` 更名；`orm-team.xml` 登记 transition 三实体 `schema="ext"`（类不迁模块，Account 先例）；`ValueMeaning` 弃冗余 `@JoinTable` 改 `mappedBy` 反向关联。
- **语义移除**：`SubmissionRecordSource` 枚举与 `submission_record.source` 全栈移除（实体/DTO/服务签名/前端类型）；`designation`/`definition` 主记录 `acceptability` 移除，可接受性仅存在于 Designation_Context/Definition_Context/Pairing（既有列已有同值副本，零数据搬迁）。
- **同步改动**：`MetadataSearchService`、`RegistrationAuthorityDeletionService`、`DimensionalityService`、`ContextTestDataInitializer`、`SubmissionRecordRepository`、`UnitOfMeasureRepository`、`MeasureClassService`、`UnitOfMeasureRequest` 中旧表名引用；`OrmTeamXmlTest` 清单；前端 `types/common.ts` 及编辑器组件。

## Capabilities

### New Capabilities

- `mdr-standard-compliance`: mdr schema 表/列与 ISO/IEC 11179 标准的一致性强制约束（表名/列名有据、非标准对象不得残留、非标准工作流归 ext、flyway 历史表归 ext）。

### Modified Capabilities

- `submission-record`: `source` 字段与"提交记录来源标注"需求整体移除（Submission_Record 回归标准口径：仅 organization/contact）。
- `unit-of-measure-measure-class`: 删除保护需求中的关联表名更新为 `unit_of_measure_class`（行为不变）。

## Impact

- 三方言迁移 + JPA 实体 + 原生 SQL 引用点 + 前端类型/组件 + 相关测试。
- API 形状变更：`SubmissionRecordSummary` 删 `source` 字段；`DesignationResponse`/`DefinitionResponse` 删主记录级 `acceptability`。
- 实施环境：隔离 worktree（主树存在并行会话 118 项未提交 WIP）。
