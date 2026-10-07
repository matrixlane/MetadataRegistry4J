# mdr schema 标准一致性清理 — 设计稿（2026-10-08）

> 状态：待用户确认。变更名（OpenSpec）：`mdr-standard-compliance-cleanup`。
> 前置：2026-10-08 mdr schema 全量审计（90 表 / 312 列 对照 ISO/IEC 11179-3/30/31 原文），用户已给出 7 项决定并在方案对比中确认方案 A 及 3 处调整。

## 1. 背景与目标

`mdr` schema 只应存放 ISO/IEC 11179 标准定义的表与列（AGENTS.md 强制分层规则）。审计发现三类不一致，本变更一次性收敛：

1. 标准之外的多余表（测试残留 ×3、Flyway 工具表、非标准工作流表 ×3、冗余映射表 ×1）；
2. 与标准命名不一致的表（9 张，用户调整后名单）；
3. 表名正确但含非标准字段（残留列 14 个 + 在用非标准列 3 个）。

目标状态：mdr schema 的每张表、每个字段均可在 ISO/IEC 11179-3/31 中找到出处；ext 承载全部应用扩展；三方言（SQL Server / SQLite / 达梦）迁移同版本同语义。

## 2. 范围

### 2.1 纳入

| # | 事项 | 用户决定 |
|---|---|---|
| 1 | 删除 3 张 `*$test_item` 测试残留表 | 直接删 |
| 2 | `mdr.flyway_schema_history` → `ext.flyway_schema_history` | 迁移至 ext |
| 3 | 9 张表按标准/约定更名 | 改名（名单见 4.2） |
| 4 | 删列：namespace ×4、scoped_identifier ×7、naming_convention.rules_json、language_identification.enabled、contact.account_id、data_type_scheme.reference、slot.value、slot.value_datatype、role 内联 email_address/phone_number | 清理 |
| 5 | designation.acceptability、definition.acceptability | 移至 context 关系表（现有列已有数据，零搬迁） |
| 6 | submission_record.source | 标准不存在，全栈移除 |
| 7 | registration_transition_rule/_rule_approver/_approval 三表 | 迁 ext schema |
| 8 | value_meaning_permissible_value_meaning | 去冗余，删表 |

### 2.2 不纳入（明确遗留）

- `registered_item_reference_document` 维持现名（用户决定，§9.5.2 关联 `reference` 名过泛不采用）；
- 15 张多值属性子表保留现名（`individual_email`、`organization_phone` 等，用户决定 4）；
- 达梦 schema 下三表无法 `TRANSFER`（用户即模式）：达梦脚本跳过迁 schema 步骤，单独任务跟进；
- 标准有而库中缺失的字段（`scoped_identifier.Full/Shorthand_expansion`、`registrar` 的 Contact 关联等）——"缺失"侧问题，另行处理；
- `designation.value`/`definition.value`（标准名 sign/text）、`reference_document.notation_value`/`title_value`、`creation_date_time` 等列名风格偏差——语义一致，低优先级，本批不动。

## 3. 方案形态

方案 A：单一 OpenSpec 变更 + 单批次三方言迁移（`V20261009001`）+ 隔离 worktree（`.worktrees/mdr-standard-cleanup`，主树存在 118 项并行会话未提交 WIP，必须隔离）实施。

## 4. 详细设计

### 4.1 迁移 `V20261009001`（三方言同名，全部幂等）

SQL Server 用 `IF EXISTS`（INFORMATION_SCHEMA/sys 元数据）守卫；SQLite 沿用既有"直写 DDL"惯例（全量重放既有局限不影响，基线机制保证只对新库生效一次）；达梦用 `ALL_TABLES/ALL_TAB_COLUMNS` 计数 + `EXECUTE IMMEDIATE` 包裹（沿用 V20260901009 达梦版式），schema 迁移段达梦跳过。

脚本顺序（防 FK 依赖）：
1. 删测试表：`DROP TABLE IF EXISTS` ×3（`definition/designation/item_administration_service_test$test_item`，表名含 `$` 需方括号/引号转义）；
2. 删冗余表 `value_meaning_permissible_value_meaning`（前置条件：JPA 映射已改走 `mappedBy`，见 4.7）；
3. 9 张表更名（见 4.2，含关联索引/约束随表迁移）；
4. 三表迁 schema：`ALTER SCHEMA ext TRANSFER mdr.registration_transition_*` ×3（达梦跳过；SQLite no-op）；
5. 删列 16 个（见 4.3）。SQL Server 侧对每个目标列先经 `sys.foreign_key_columns`/`sys.default_constraints` 动态查出引用约束并 `DROP`，再 `ALTER TABLE DROP COLUMN`（FK 名为 Hibernate 哈希名，不能硬编码）；
6. `submission_record.source` 删列（同样先清约束）；
7. designation/definition 的 `acceptability` 列删除——**零数据搬迁**：审计已核实主表 4 行非空值（designation 2 / definition 2，均 `preferred`）在各自 context 行（Registry Context `00000000-0000-0000-0000-000000000002`）已有同值副本。

### 4.2 表更名清单（9 张）与 JPA 改动点

| 现名 | 新名 | 标准出处 | JPA 改动 |
|---|---|---|---|
| `conceptual_domain_dec` | `data_element_concept_domain` | 11179-31 §7.2.3.4 关联 | `ConceptualDomain` `@JoinTable` |
| `designation_context_relation` | `designation_context` | 11179-3 §8.5.2 关联类 | `DesignationContext` `@Table` |
| `definition_context_relation` | `definition_context` | 11179-3 §8.5.1 关联类 | `DefinitionContext` `@Table` |
| `item_mapping_subject` | `subject_mapping` | 11179-3 §11.4.1 关联类 | `ItemMappingSubject` `@Table` |
| `item_mapping_object` | `object_mapping` | 11179-3 §11.4.2 关联类 | `ItemMappingObject` `@Table` |
| `designation_naming_convention` | `naming_convention_conformance` | 11179-3 §8.6.5 关联 | `Designation` `@JoinTable` |
| `namespace_naming_convention` | `naming_convention_utilization` | 11179-3 §8.6.6 关联 | `Namespace` `@JoinTable` |
| `unit_of_measure_measure_class` | `unit_of_measure_class` | 11179-31 §7.5.3.2 关联 | `UnitOfMeasure` `@JoinTable` |
| `submission_record_registered_item` | `registered_item_submission_record` | 关联（标准名 `submission` 过泛，按用户调整） | `SubmissionRecord` `@JoinTable` |

同步改动（已文本检索定位）：`MetadataSearchService`、`RegistrationAuthorityDeletionService`、`DimensionalityService`、`ContextTestDataInitializer`、`SubmissionRecordRepository`、`UnitOfMeasureRepository`、`MeasureClassService`、`UnitOfMeasureRequest` 中引用旧表名的原生 SQL/注释。

### 4.3 删列清单（16 列，全部已核实 0 行非空）

| 表 | 列 | 性质 |
|---|---|---|
| namespace | identifier_input_mode, manual_identifier_pattern, version_input_mode, versioning_policy | 残留（ext.namespace_identifier_settings 已接管，JPA 未映射） |
| scoped_identifier | identifier_creation_mode, version_creation_mode, version_status, created_at, published_at, deprecated_at, obsolete_at | 残留（ext.scoped_identifier_lifecycle 已接管） |
| naming_convention | rules_json | 残留（ext.naming_convention_rules 已接管） |
| language_identification | enabled | 残留（ext.language_governance 已接管） |
| contact | account_id | 残留（ext.contact_account_binding 已接管，含 1 个哈希名 FK 需先删） |
| data_type_scheme | reference | 残留（标准仅 scheme_reference；V20260901003 曾覆盖但本库未执行） |
| slot | value, value_datatype | 残留（Value 1..* 已由 slot_value 承载；value_datatype 标准无此属性） |
| role | email_address, phone_number | 残留（0..* 已由 role_email/role_phone 承载） |
| designation / definition | acceptability | 移至 context 关系表（既有列已有同值副本） |
| submission_record | source | 全栈移除（见 4.5） |

既有迁移 V20260901003/09/10/11/12/13 在本库（flyway 历史缺 2026-09-01 批次）与将来新库上均不会与本脚本冲突：前者"列存在才回填+删"，本脚本"列存在才删"，两者顺序执行结果一致。

### 4.4 registration_transition 三表迁 ext

- JPA 类不迁模块（沿用 `Account` 先例）：`RegistrationTransitionRule`、`RegistrationTransitionApproval`、`RegistrationTransitionRuleApprover`（及所在包）保留在 common-facilities，`orm-team.xml` 追加三条 `<entity ...><table schema="ext"/></entity>`；`OrmTeamXmlTest` 的清单计数同步 +3；
- SQL Server：`ALTER SCHEMA ext TRANSFER`（数据 40 行随迁，FK 跨 schema 合法）；
- SQLite：无 schema 概念，天然 no-op；达梦：跳过（遗留任务）；
- 影响：`orm-team.xml` 头注与 `application-team.yml` 注释中的"27 实体/28 表"口径同步更新。

### 4.5 submission_record.source 全栈移除

- `SubmissionRecord` 实体删 `source` 字段；`SubmissionRecordSource` 枚举删除；
- `ItemAdministrationService.recordSubmission` / `recordSubmissionForBatch` 去掉 `source` 参数与必传断言（`WorkspaceService` 2 处调用点同步）；`SubmissionRecordSummary` DTO 删 `source`；相关测试改造；
- 前端：`types/common.ts` 的 `SubmissionRecordSource` 类型及使用组件清理；
- 语义说明：Submission_Record 回归标准口径（organization/contact）；历史"SYSTEM_DRAFT 脏数据标注"语义随列移除，审计确认现库 `source` 全为 NULL、无实际标注数据。

### 4.6 designation/definition.acceptability 语义归位

- 实体删字段；`DesignationResponse`/`DefinitionResponse`/`DefinitionRequest`/`DesignationDefinitionPairingRequest` 等 DTO 及 `ItemAdministrationService`(1061/1081)、`VersionHistoryService`(254/274) 中直接读写主表 acceptability 的点全部改经 DesignationContext/DefinitionContext 条目；
- 前端 `DesignationEditor`/`DefinitionEditor`/`MultilingualTextEditor`/`ItemFormDialog` 中的主记录 acceptability 输入移除，保留 context 条目上的 acceptability 编辑（既有能力）；
- 保留证据：标准 Table 14/15 未列 acceptability，§8.7.1 正文仅提及 Designation_Context/Definition_Context/Pairing 上的 acceptability（提取已核实）。

### 4.7 ValueMeaning 映射去冗余

`ValueMeaning` 弃用 `@JoinTable(value_meaning_permissible_value_meaning)` 的多对多，改为 `@OneToMany(mappedBy = "valueMeaning")` 经 `PermissibleValueMeaning` 反向关联（PVM 表已有 `value_meaning_id` 列且实体已映射）；迁移删除空表 `value_meaning_permissible_value_meaning`。检查 `PermissibleValue` 侧 `@JoinTable(permissible_value_pvm)` 保持不变（承载 PV↔PVM 绑定，语义等同标准关联类）。

### 4.8 flyway_schema_history 归位 ext（无新迁移）

`FlywayHistoryRelocator`（2026-10-01 归档的 `relocate-flyway-history-to-ext`）在 Flyway 探测前自动 `ALTER SCHEMA ext TRANSFER` 并完成"目标处无+主有"守卫。本变更**不重复搬迁**，只做真实库重启验证：启动后 `OBJECT_ID('mdr.flyway_schema_history') IS NULL`、`ext.flyway_schema_history` 行数=21、无 re-baseline 日志——即闭环归档时遗留的 tasks 3.4。

## 5. 影响面（GitNexus + 文本检索证据）

- `SubmissionRecordSource`：GitNexus 两个候选符号（Java 枚举 + TS 类型），调用方靠文本检索定位（ItemAdministrationService、WorkspaceService、DTO、测试、前端 types）；索引落后 HEAD 18 提交，以文本检索为准；
- 表更名引用点已全部文本定位（4.2 清单）；acceptability 直接读写点已定位（4.6）；
- 风险：主表 acceptability 是 API 形状变更（DTO 删字段），前端组件与 e2e 可能引用；`detect_changes` 在提交前复跑。

## 6. 验证计划

1. TDD：迁移脚本改动先行测试（沿用各模块 Repository/Service 测试）；submission 签名变更、acceptability 语义迁移的测试红→绿；
2. 构建与全量测试：`mvn -pl metadata-registry-web -am test`、前端 `vitest` + `vue-tsc`；
3. 真实库（本机 SQL Server）：应用 `V20260901009`（sqlserver）→ 重启应用（触发 Relocator）→ 全量冒烟（mdr 表数/列对照清单、ext 三表、前端提交流、指称/定义编辑器、计量单位页）；
4. SQLite scratch 库迁移冒烟；
5. `openspec validate --all`、`gitnexus detect_changes`（无 partial/truncated）。

## 7. 回滚

迁移脚本全部幂等守卫，旧库重跑同脚本无副作用；代码侧回滚 = revert 本变更提交（worktree 独立分支，未合并前零影响）。回滚后旧库需手工恢复被删列/表（本库被删对象均无数据，重建空壳即可）。

## 8. 遗留

- 达梦迁 schema 方案单独开任务；
- 并行会话 WIP（naming-convention 等）若先提交同文件，合并时按文件仲裁；
- `docs/architecture/ARCHITECTURE.md` 若涉及被改表名，提交前按文档同步规则检查。
