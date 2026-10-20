# 遗留语言表 Schema 漂移修复（启动期修复器 + 本机空库重建）

## Why

远古 ddl-auto 时代遗留的 SQLite 个人版库，`language_identification` 物理表残留 `language_id NOT NULL UNIQUE`、`language_name`、`territory_name` 等遗留列，与当前实体形状（ISO/IEC 11179-3:2023 §6.3.7，标准列仅 id/language_identifier/script_identifier/geopolitical_territory_identifier/private_use_qualifier）不一致。预置脚本 `V20260919003__Seed_builtin_languages.sql` 的 INSERT 列清单不含 `language_id`，在该类库上重放每条必失败（`SQLITE_CONSTRAINT_NOTNULL`，2026-10-20 本机启动日志实证：22 条语言代码 + 22 条中文名称全部跳过）——内置语言永远种不入，初始化向导的"文档语言"数据源为空；`ddl-auto=update` 只增列不删列，漂移永久驻留。`dd8d9dbe` 落地的"单条语句失败仅 WARN 跳过"只是防启动崩溃的兜底，不是修复。

## What Changes

- 新增启动期遗留修复器（SQLite 方言守卫）：`PRAGMA table_info` 漂移签名判定 → 遗留扩展数据按 mdr/ext 分层规则迁往卫星表（`language_name`/`territory_name` → `ext_language_identification`，`enabled` → `language_governance`，缺失表按既有迁移 DDL 补建）→ `language_identification` 在单事务内重建为当前实体形状（`id` 原值保留以维持 8 张按值引用表，对齐 `V20260926001` 补 `uq_language_identification_identifier` 唯一索引，冲突时 WARN 跳过）→ 语言预置可完整重播。
- 修复器以 `ApplicationRunner @Order(5)` 先于静态预置 Runner（`@Order(10)`）执行；幂等可重入（修复后签名消失即自动跳过）；健康库与非 SQLite 方言 no-op。
- 本机空漂移库（111 表、全部业务数据仅 account×1 + identifier_strategy_config×1，均可自动重种）备份后删除，由既有 `FreshDatabaseInitializer` FRESH 路径按最新实体 + 共同基线 v20261009012 重建，全库 schema 归位（运维操作，证据记入 validation.md，不入签入）。
- 新增 6 组测试：带数据漂移修复、enabled 迁移、修复后端到端播种 22+22、健康库 no-op、非 SQLite no-op、唯一索引冲突容忍。

## Capabilities

### New Capabilities

- `legacy-schema-repair`: 定义遗留漂移 SQLite 库启动期修复的触发条件（漂移签名、方言守卫）、扩展数据归位卫星表的分层规则、标准表重建的标识保留与幂等语义，以及修复后语言预置完整重播的验收标准。

### Modified Capabilities

- `system-initialization`: 新增"遗留漂移库启动期修复先于静态预置"的顺序要求，保证初始化向导的语言数据源在任何来源的库上可用。

## Impact

- 代码：新增 `web/config/LegacyLanguageSchemaRepairer.java` 与对应测试类；`LanguagePresetSeeder`、`StaticDataPresetService` 及其兜底语义不变。
- 启动顺序：EMF/Flyway 初始化 → 遗留修复（`@Order(5)`）→ 静态预置（`@Order(10)`）→ 初始化向导可用。
- 本机运维：`data/metadata-registry.db` 备份后删除重建；后端重启验证（旁路 8082 先实证修复器，再 8081 全新基线）。
- 文档：本变更的 OpenSpec 产物 + `validation.md`；`.superpowers-memory` 收尾更新。README / ARCHITECTURE.md 无用户可见变化。
- 风险边界：修复器仅 SQLite 方言生效；本机重建使用备份文件可整体回退；验证不触碰团队版真实库。

