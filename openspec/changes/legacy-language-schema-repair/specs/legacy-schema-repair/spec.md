# legacy-schema-repair Delta

## ADDED Requirements

### Requirement: 遗留漂移签名识别与方言守卫

启动期修复器 SHALL 在静态预置数据执行之前检查 `language_identification` 表结构。仅当数据库方言为 SQLite 且该表存在标准列集合（`id`、`language_identifier`、`script_identifier`、`geopolitical_territory_identifier`、`private_use_qualifier`）之外的额外列时，修复器 SHALL 判定该库为遗留漂移库并执行修复；标准列集合内任何列的缺失（旧代建表）MUST 同样视为漂移并纳入重建（缺失列以 NULL 兜底）。健康库（无额外列）与非 SQLite 方言 MUST NOT 执行任何修复 SQL（no-op）。该判定 MUST 覆盖已实证的三代漂移形态：`language_id` + `language_name` + `territory_name`（本机 2026-10-20 实证）、单独 `language_id NOT NULL`（`LanguagePresetSeederTest` 模拟形态）、`enabled`（`V20260723002` baseline 快照形态）。

#### Scenario: 本机代漂移库被识别并修复

- **GIVEN** 一个 SQLite 库，`language_identification` 含 `language_id NOT NULL UNIQUE`、`language_name`、`territory_name` 列（远古 ddl-auto 遗留形状）
- **WHEN** 应用启动进入修复器
- **THEN** 修复器判定漂移并执行修复；修复后 `PRAGMA table_info(language_identification)` 的列集合精确等于当前实体标准 5 列

#### Scenario: 健康库不触发修复

- **GIVEN** 一个 SQLite 库，`language_identification` 恰为标准 5 列（含 FRESH 基线新建库）
- **WHEN** 应用启动进入修复器
- **THEN** 修复器不执行任何 DDL/DML，表结构保持不变

#### Scenario: 非 SQLite 方言不触发修复

- **GIVEN** 一个 SQL Server 或达梦部署
- **WHEN** 应用启动进入修复器
- **THEN** 修复器以 debug 日志跳过，不对数据库执行任何语句

### Requirement: 遗留扩展数据按分层规则归位卫星表

修复器 SHALL 在重建标准表之前，把遗留列中的数据按 mdr/ext 分层规则迁移至对应卫星表，全部迁移语句 MUST 带 `NOT EXISTS` 幂等守卫并可重复执行：`language_name`/`territory_name` SHALL 迁入 `ext_language_identification(id, language_id, language_name, territory_name)`（按 `language_id` = 原行 `id` 一一对应）；`enabled` SHALL 迁入 `language_governance(id, enabled, language_id)`。卫星表缺失时 MUST 按既有迁移 DDL 补建（`V20260919001` 建 `ext_language_identification` + 唯一索引；`V20260901012` 建 `language_governance`）。无对应卫星归宿的遗留列（如 `language_id`）若存在非空值，修复器 MUST 记 WARN 日志说明将被丢弃的行数，MUST NOT 静默丢弃。

#### Scenario: 含中文名称的遗留行迁入卫星表

- **GIVEN** 遗留 `language_identification` 含一行：`id=X`、`language_identifier='zh-CN'`、`language_name='中文'`、`territory_name='中国'`
- **WHEN** 修复器执行数据归位
- **THEN** `ext_language_identification` 存在一行 `language_id=X`、`language_name='中文'`、`territory_name='中国'`

#### Scenario: 卫星表已有数据不重复迁移

- **GIVEN** `ext_language_identification` 已存在 `language_id=X` 的行
- **WHEN** 修复器再次执行数据归位
- **THEN** 不插入重复行，既有行保持不变

#### Scenario: enabled 列迁入语言治理卫星表

- **GIVEN** 遗留 `language_identification` 含 `enabled` 列且存在数据行
- **WHEN** 修复器执行数据归位
- **THEN** `language_governance` 收到对应行的 `enabled` 值（`language_id` 指向原行 `id`），且已存在对应行时不重复插入

### Requirement: 标准表重建保留标识与引用完整性

修复器 SHALL 在单事务内重建 `language_identification` 为当前实体形状：创建符合当前实体的新表、按动态列清单复制数据（源表缺失的当前列以 NULL 兜底）、删除旧表、重命名新表。重建 MUST 保留各行 `id` 原值——`definition`、`designation`、`reference_document_language`、`registration_authority_doc_language`、`registry_specification.default_language_id`、`language_extension`、`language_variant`、`ext_language_identification` 共 8 处按值引用 `language_identification.id`，id 变更会破坏引用。重建后修复器 SHOULD 建立 `uq_language_identification_identifier` 唯一索引（对齐 `V20260926001` 目标形态）；既有数据冲突导致建索引失败时 MUST 仅 WARN 跳过、不得阻断修复。整个修复 MUST 单事务原子执行：任何一步失败回滚，遗留库保持原状，下次启动可重入。

#### Scenario: 有数据行的重建保留 id

- **GIVEN** 遗留 `language_identification` 有数据行且 `ext_language_identification` 已按归位要求引用其 `id`
- **WHEN** 修复器完成重建
- **THEN** 新表列集合为标准 5 列；各行 `id` 值不变；卫星表引用保持有效

#### Scenario: 唯一索引数据冲突被容忍

- **GIVEN** 遗留 `language_identification` 存在 `language_identifier` 重复的行
- **WHEN** 修复器尝试建立 `uq_language_identification_identifier`
- **THEN** 建索引失败仅产生 WARN 日志，修复事务不回滚、不抛异常（重复行场景由应用层防御式解析兜底，符合 `language-identification-uniqueness` 语义）

#### Scenario: 修复失败可重入

- **GIVEN** 修复过程在重建中途失败（如磁盘错误）
- **WHEN** 下次启动再次进入修复器
- **THEN** 事务已回滚、库保持漂移原状；修复器重新完整执行修复并成功

### Requirement: 修复后语言预置完整重播

遗留漂移库完成修复后，`LanguagePresetSeeder` 重放 `V20260919003__Seed_builtin_languages.sql` MUST 能够完整执行：22 条内置语言代码全部写入 `language_identification`、22 条中文名称/地区名称全部写入 `ext_language_identification`，无任何语句因 schema 不兼容被跳过。初始化向导的语言数据源（`/api/v1/languages`）MUST 能返回含 `zh-CN`（名称为"简体中文"、地区"中国"）在内的 22 条语言。

#### Scenario: 修复后端到端播种

- **GIVEN** 一个空的遗留漂移 `language_identification` 表（本机 2026-10-20 实际形态）
- **WHEN** 修复器完成修复，随后静态预置 Runner 执行 `presetAll()`
- **THEN** 启动日志中语言预置无"schema 漂移跳过"WARN；`language_identification` 22 行、`ext_language_identification` 22 行；`zh-CN` 名称为"简体中文"（V20260929001 口径修正后）

#### Scenario: 修复器不重复修复已修复库

- **GIVEN** 上一轮启动已完成修复的库
- **WHEN** 再次启动进入修复器
- **THEN** 漂移签名判定不命中，修复器直接跳过（幂等）


