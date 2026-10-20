# 遗留语言表 Schema 漂移修复设计

详细设计背景与方案比较见 Superpowers 设计稿：`docs/superpowers/specs/2026-10-20-legacy-language-schema-repair-design.md`（已定案，用户裁定 2026-10-20）。本文档记录实现层面的关键决策。

## Context

本机 `data/metadata-registry.db` 为远古 ddl-auto 遗留库（111 表、无 Flyway 历史、业务数据仅 account×1 + identifier_strategy_config×1、8 张语言表全空）。`language_identification` 残留 `language_id NOT NULL UNIQUE`/`language_name`/`territory_name` 遗留列，导致 `V20260919003` 语言预置 44 条语句全部跳过（2026-10-20 启动日志实证）。`dd8d9dbe` 的 WARN 跳过仅防崩溃，需按最新代码修订 schema。

## Decisions

### D1 修复器形态与顺序

新类 `LegacyLanguageSchemaRepairer`（`metadata-registry-web` → `io.github.matrixlane.metadataregistry.web.config`）：`@Component` + `ApplicationRunner` + `@Order(5)`，先于 `StaticDataPresetRunner`（`@Order(10)`），后于 EMF/Flyway 初始化。方言守卫 `DatabaseEnvironment.vendor() == SQLITE`，否则 debug 日志 no-op。

### D2 漂移签名判定

`PRAGMA table_info(language_identification)`；标准列集合 = `{id, language_identifier, script_identifier, geopolitical_territory_identifier, private_use_qualifier}`。存在额外列 → 漂移；**或**标准列缺失（旧代建表）→ 漂移并纳入重建（缺失列以 NULL 兜底）。判定覆盖三代已实证形态：本机 `language_id`+names 代、纯 `language_id` 代、`enabled` 代。

### D3 数据归位（重建前，同一事务，全部 `NOT EXISTS` 幂等）

- `language_name`/`territory_name` → `ext_language_identification(id, language_id, language_name, territory_name)`；卫星表缺失按 `V20260919001` DDL 补建（含唯一索引）。
- `enabled` → `language_governance(id, enabled, language_id)`；表缺失按 `V20260901012` DDL 补建。
- 无归宿列（如 `language_id`）：非空值时 WARN 记录将被丢弃的行数，不静默丢弃。

数据流向与 AGENTS.md mdr/ext 分层规则一致：非标准扩展列迁出标准表、落入对应卫星表。

### D4 表重建（单事务，id 保留）

1. `CREATE TABLE language_identification_repair`（当前实体标准 5 列 + PK）；
2. `INSERT ... SELECT` 动态列清单（源缺失列以 NULL 兜底）；**`id` 原值保留**——`definition`/`designation`/`reference_document_language`/`registration_authority_doc_language`/`registry_specification.default_language_id`/`language_extension`/`language_variant`/`ext_language_identification` 共 8 处按值引用 `language_identification.id`（`V20260926001` 实证）；
3. `DROP` 旧表 + `RENAME`；
4. `CREATE UNIQUE INDEX IF NOT EXISTS uq_language_identification_identifier`（对齐 `V20260926001`；冲突则 WARN 跳过不阻断，重复行场景由 `LanguageIdentifierResolver` 防御式解析兜底）；
5. `PRAGMA foreign_keys=OFF` 尽力包裹（遗留 SQLite 库无声明式 FK，纯防御）。

事务性经 `TransactionTemplate` 保证：任一步失败回滚，遗留库原状，下次启动重入。修复后签名消失 → 幂等。

### D5 本机空库重建（运维，不入代码）

备份 `data/metadata-registry.db` → 8082 旁路实例 + 旧库副本实证修复器自愈（22+22 无跳过）→ 删除副本与旧库 → 8081 由 `FreshDatabaseInitializer` FRESH 路径（基线 v20261009012）重建 → 验证 `flyway_schema_history`、`/api/v1/languages` 22 条（zh-CN 简体中文/中国）、`/api/v1/init-wizard/status` 200。本机库为全空漂移库，重建比就地修复更彻底（其余 110 张远古表一并归位）。

### D6 测试策略（TDD，真实 SQLite 内存库，仿 `LanguagePresetSeederTest` 基建）

6 组：① 带数据漂移修复（结构/数据/id/二次 no-op）；② enabled 迁移 + 卫星守卫；③ 修复后接 `LanguagePresetSeeder` 端到端 22+22；④ 健康库 no-op；⑤ 非 SQLite no-op；⑥ 唯一索引冲突容忍。既有 `LanguagePresetSeederTest.toleratesDriftedLegacySchema` 的"seeder 自身容错"契约保持不变（修复器是独立组件，不削弱 seeder 兜底）。

## Alternatives Considered

| 方案 | 裁定 | 理由 |
|---|---|---|
| A. 启动期修复器 + 本机重建 | 采用 | 用户裁定（2026-10-20）：系统性与本机彻底性兼得 |
| B. 仅本机重建 | 放弃 | 其他有真实数据的遗留个人版库无法自愈 |
| C. 仅修复器 | 放弃 | 本机其余 110 张远古表保持漂移 |
| D. 修改预置 SQL 兼容漂移列 | 否决 | 列清单差异无法用纯 SQL 条件化；且会把非标准列写回标准表，违背分层规则 |

## Risks / Trade-offs

- 修复器误伤健康库：签名判定要求额外列存在，健康/FRESH 库不触发（测试守护）。
- 本机重建丢数据：全库仅 2 行预置数据（自动重种），删库前留备份可整体回退。
- 唯一索引尽力建立：数据冲突时 WARN 跳过，与 `language-identification-uniqueness` 的应用层防御语义一致。
- 工作区并行变更：`administered-item-reference-documents-ui` 未签入修改与本变更无关；构建验证用 jar 不随本变更签入。

