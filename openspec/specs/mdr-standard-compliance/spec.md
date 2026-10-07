# mdr-standard-compliance Specification

## Purpose

`mdr` schema（ISO/IEC 11179 标准层）的结构纯度强制约束：每张表/每列必须可在 ISO/IEC 11179-3/31
中找到出处，命名与标准一致；应用扩展（非标准工作流表、Flyway 工具表、多值属性之外的内联扩展列）
一律归 `ext` schema 卫星表或子表承载。审计、更名、剥离与归位以本 spec 为验收口径。

## Requirements

### Requirement: mdr schema 表与标准命名一致

`mdr` schema 中的每张表 MUST 对应 ISO/IEC 11179-3:2023 或 ISO/IEC 11179-31:2023 中同名（snake_case 化）的类或关联（类/关联类），MUST NOT 使用自创缩写或词序颠倒的表名。本批次落位的更名：`conceptual_domain_dec→data_element_concept_domain`（11179-31 §7.2.3.4）、`designation_context_relation→designation_context`（§8.5.2）、`definition_context_relation→definition_context`（§8.5.1）、`item_mapping_subject→subject_mapping`（§11.4.1）、`item_mapping_object→object_mapping`（§11.4.2）、`designation_naming_convention→naming_convention_conformance`（§8.6.5）、`namespace_naming_convention→naming_convention_utilization`（§8.6.6）、`unit_of_measure_measure_class→unit_of_measure_class`（11179-31 §7.5.3.2）、`submission_record_registered_item→registered_item_submission_record`。

#### Scenario: 迁移后标准关联表就位

- **WHEN** 在任一支持方言（SQL Server / SQLite / 达梦）上应用 `V20261009005`
- **THEN** 9 张新名表存在且旧名表不存在；JPA 实体经 `@Table`/`@JoinTable` 注解映射到新名表

#### Scenario: 全新库与存量库一致收敛

- **WHEN** 一个全新数据库执行完整迁移序列，与该序列在既有库上补应用的结果对比
- **THEN** 两库 mdr schema 表清单一致（新库不重建旧名表）

### Requirement: mdr 标准表不得包含非标准列

`mdr` schema 中标准类对应的表 MUST 只包含标准定义的属性列（含关系映射所需的技术列：主键 id、关联外键 `<关联端>_id`、多值属性子表），MUST NOT 残留应用扩展列。本批次删除的 22 个 (表,列) 对：`namespace.identifier_input_mode/manual_identifier_pattern/version_input_mode/versioning_policy`、`scoped_identifier.identifier_creation_mode/version_creation_mode/version_status/created_at/published_at/deprecated_at/obsolete_at`、`naming_convention.rules_json`、`language_identification.enabled`、`contact.account_id`、`data_type_scheme.reference`、`slot.value/value_datatype`、`role.email_address/phone_number`（内联）、`designation.acceptability`、`definition.acceptability`、`submission_record.source`。扩展语义 MUST 经 `ext` schema 卫星表承载。

#### Scenario: 存量库残留列被清除

- **WHEN** 在一个仍保留剥离前物理列的既有库上应用 `V20261009005`
- **THEN** 上述 16 列全部被删除，数据经审计为零损失（所有列应用前 0 行非空）

#### Scenario: 全新库不重建残留列

- **WHEN** 全新数据库执行完整迁移序列
- **THEN** 上述 16 列不存在，对应扩展语义仅存在于 ext 卫星表

### Requirement: 非标准工作流表归 ext schema

应用自身的工作流/引擎表 MUST NOT 存放于 `mdr` schema。`registration_transition_rule`、`registration_transition_rule_approver`、`registration_transition_approval`（注册状态流转审批工作流，ISO/IEC 11179 各分部均无此概念）MUST 存放于 `ext` schema；JPA 实体保留原模块，经 `orm-team.xml` 登记 `schema="ext"`（SQLite 无 schema 概念，天然免登记）。

#### Scenario: 团队版迁表

- **WHEN** 在 SQL Server 团队版库应用 `V20260901009`（本变更批次）
- **THEN** 三张表位于 `ext` schema 且行数据保留；JPA 读写指向 `ext` 表

#### Scenario: 达梦与 SQLite 不迁移

- **WHEN** 在达梦或 SQLite 上应用本批次迁移
- **THEN** 三表保持在原位置且应用正常运行（达梦迁 schema 方案为单独遗留任务）

### Requirement: flyway 历史表归位 ext

`flyway_schema_history` 是 Flyway 工具表而非 ISO/IEC 11179 标准表，MUST 由 `ext` schema 管理。团队版下 `FlywayHistoryRelocator` MUST 在 Flyway 探测前把仍留在主 schema 的历史表整体 `ALTER SCHEMA TRANSFER` 到 Flyway 实际查找位置（default-schema），并保证零重放、不 re-baseline。

#### Scenario: 既有库启动归位

- **WHEN** 一个历史表仍在 `mdr` 的既有 SQL Server 库启动应用
- **THEN** 启动后 `mdr.flyway_schema_history` 不存在、`ext.flyway_schema_history` 行数与原表一致、日志无 re-baseline

#### Scenario: 已归位库幂等跳过

- **WHEN** 历史表已在 `ext` 的库再次启动
- **THEN** Relocator 跳过搬迁，应用正常启动

### Requirement: 测试与冗余表不得残留

`mdr` schema MUST NOT 包含无实体映射的孤儿表（含历史功能残留与测试垃圾表）或与既有结构语义重复的冗余表。本批次删除：`definition_service_test$test_item`、`designation_service_test$test_item`、`item_administration_service_test$test_item`、`value_meaning_permissible_value_meaning`（冗余：Permissible_Value_Meaning 已含 `value_meaning_id`）。

#### Scenario: 应用启动后无孤儿表

- **WHEN** 应用本批次迁移后检查 `mdr` schema 表清单
- **THEN** 上述 4 张表不存在；`mdr` 全部表均有 JPA 实体映射或 Flyway 历史表（位于 ext）

### Requirement: 可接受性仅存在于上下文与配对

按 ISO/IEC 11179-3:2023 §8.7.1 口径，Designation/Definition 主记录 MUST NOT 携带 `acceptability` 列；可接受性 MUST 仅存在于 `designation_context`、`definition_context`（上下文关系）与 `designation_definition_pairing`（配对）上。存量主记录的非空可接受性值迁移前 MUST 已在对应上下文行存在同值副本（零数据搬迁）。

#### Scenario: 主表列移除

- **WHEN** 应用本批次迁移
- **THEN** `designation` 与 `definition` 表无 `acceptability` 列；三张关系表保留该列且数据不变

#### Scenario: API 不再暴露主记录可接受性

- **WHEN** 读取 Item 元数据（ItemMetaResponse）
- **THEN** 指称/定义条目不包含主记录级 `acceptability` 字段；上下文条目仍携带可接受性
