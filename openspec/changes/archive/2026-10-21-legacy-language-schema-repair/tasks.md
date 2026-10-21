# 任务清单

> 实施前置条件：用户确认本变更 proposal / design / specs / tasks。
> 每项数据库运行验证均使用临时副本或临时数据库；不得连接或写入本机真实 SQL Server 库。
> 工作区存在并行变更（`administered-item-reference-documents-ui`）的未签入修改：本变更构建验证用 jar 不随本变更签入，签入时仅含本变更文件。

## 1. 启动期遗留修复器

- [x] 1.1 新增 `web/config/LegacyLanguageSchemaRepairer.java`：`@Order(5)` `ApplicationRunner` 骨架、SQLite 方言守卫（`DatabaseEnvironment`）、漂移签名判定（`PRAGMA table_info`，标准 5 列集合之外任何额外列或标准列缺失即漂移）。
- [x] 1.2 数据归位：修复重建前，经 `TransactionTemplate` 单事务执行——`language_name`/`territory_name` → `ext_language_identification`、`enabled` → `language_governance`（均带 `NOT EXISTS` 幂等守卫；卫星表缺失按 `V20260919001`/`V20260901012` DDL 补建）。
- [x] 1.3 表重建：创建 `language_identification_repair`（当前实体 5 列 + PK）→ 动态列清单复制（缺失列 NULL 兜底、`id` 原值保留）→ `DROP` + `RENAME` → `CREATE UNIQUE INDEX IF NOT EXISTS uq_language_identification_identifier`（冲突 WARN 跳过不阻断）；`PRAGMA foreign_keys=OFF` 尽力包裹。
- [x] 1.4 不可归位列（如 `language_id`）存在非空值时 WARN 记录将被丢弃的行数；修复完成/跳过均输出结构化日志。

## 2. 测试（TDD）

- [x] 2.1 带数据漂移表（`language_id` + names，含数据行）修复：修复后列集合 == 标准 5 列；ext 表收到名称数据；`id` 保留；二次运行 no-op。
- [x] 2.2 `enabled` 列迁移：`language_governance` 收到 enabled 值；卫星已有行时 `NOT EXISTS` 守卫不重复插入。
- [x] 2.3 端到端：空漂移表修复 → 续接 `LanguagePresetSeeder` 种入 22 语言 + 22 名称（zh-CN 名称为"中文"）。
- [x] 2.4 健康库 no-op（结构不变）、非 SQLite 方言 no-op（零 SQL 执行）、唯一索引数据冲突场景 WARN 容忍。
- [x] 2.5 web 模块全量回归：既有测试（含 `LanguagePresetSeederTest.toleratesDriftedLegacySchema`、`FreshDatabaseInitializerTest`、守护测试）无新增失败。

## 3. 本机重建与实证（运维验证，不入签入）

- [x] 3.1 备份 `data/metadata-registry.db`（空库也留底，可回退）。
- [x] 3.2 以含修复器的新 jar 起 8082 旁路实例（`DB_URL=jdbc:sqlite:./data/<旧库副本>.db`），实证修复器命中漂移签名、数据归位、表重建、语言预置 22+22 无跳过 WARN；`GET :8082/api/v1/languages` 复核 22 条。
- [x] 3.3 停旁路实例，删除副本与旧库；8081 正式启动（start.bat 同款）走 FRESH 重建：验证 `flyway_schema_history`（baseline v20261009012）、`GET :8081/api/v1/languages` 22 条（zh-CN"中文"/"中国"）、`GET /api/v1/init-wizard/status` 200。

## 4. 文档与收尾

- [x] 4.1 填写本变更 `validation.md`（修复器测试证据、旁路实证日志摘录、本机重建验证结果）。
- [x] 4.2 GitNexus `detect-changes --scope all` 全量分析无 partial/truncated；HIGH/CRITICAL/UNKNOWN 按 AGENTS.md 复核。
- [x] 4.3 按变更分组签入：仅本变更文件（修复器 + 测试 + OpenSpec 产物 + Superpowers 设计稿），排除并行变更文件与 `logs/`、`data/` 杂物。
- [x] 4.4 `.superpowers-memory`：`CURRENT_STATE.md` 更新 + 会话 journal 记录。

