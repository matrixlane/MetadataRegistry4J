# 遗留语言表 Schema 漂移修复 — 实现计划

日期：2026-10-20
变更：`legacy-language-schema-repair`（OpenSpec 产物已定案并经用户确认）
依据设计：`docs/superpowers/specs/2026-10-20-legacy-language-schema-repair-design.md`（D1–D6）

## 实现步骤（TDD）

### Step 1 — 测试先行（红）

新建 `metadata-registry-web/src/test/java/io/github/matrixlane/metadataregistry/web/config/LegacyLanguageSchemaRepairerTest.java`：

1. `repairsLocalGenerationDriftWithData`：本机代漂移（`language_id NOT NULL UNIQUE` + names，含 zh-CN 数据行）→ 修复后列集合==标准 5 列；ext 收到名称（language_id==原 id）；二次运行 no-op。
2. `migratesEnabledColumnToGovernance`：`enabled` 列（true/false/null）→ `language_governance` 收到 coalesce 值；守卫不重复。
3. `seedsTwentyTwoLanguagesAfterRepair`：空漂移表 → 修复 → `LanguagePresetSeeder` 22+22，zh-CN 名称"中文"。
4. `noOpOnHealthySchema` / `noOpOnNonSqliteVendor`：no-op 验证。
5. `toleratesUniqueIndexConflict`：重复 language_identifier → 修复完成、索引 WARN 跳过、双行保留。
6. `backfillsMissingStandardColumnsWithNull`：缺 `private_use_qualifier` 的旧代建表 → 修复后 5 列、缺失列 NULL。

基建：Hikari SQLite 内存库（pool=1，仿 `LanguagePresetSeederTest`）；`TransactionTemplate(new DataSourceTransactionManager(ds))`；`DatabaseEnvironment("", "jdbc:sqlite:memdb", "", "")`。

### Step 2 — 实现（绿）

新建 `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/config/LegacyLanguageSchemaRepairer.java`：

- `@Component @Order(5) ApplicationRunner`；构造注入 `JdbcTemplate`、`DataSource`、`DatabaseEnvironment`；内部自建 `DataSourceTransactionManager` 的 `TransactionTemplate`（保证 JDBC 事务连接绑定，避免 JpaTransactionManager 混合 JDBC 的连接不确定性）。
- 方言守卫（非 SQLite debug 跳过）→ `PRAGMA table_info` 列清单 → 额外列/缺失列均判定漂移 → 单事务：`PRAGMA foreign_keys=OFF`（尽力）→ 数据归位（names→ext、enabled→governance，NOT EXISTS 守卫、缺表补建）→ 不可归位列 WARN → 重建（`language_identification_repair` 新表 + 动态列 SELECT + DROP/RENAME）→ 唯一索引 IF NOT EXISTS（冲突 WARN 容忍）。

### Step 3 — 回归

- 定向：`LegacyLanguageSchemaRepairerTest`、`LanguagePresetSeederTest`、`FreshDatabaseInitializerTest`、`StaticDataPresetServiceTest`。
- 全量：`mvnw.cmd -pl metadata-registry-web test`（工作区含并行变更未签入修改，基线 = 其文档化基线 7F+4E 既有三类；本变更要求零新增失败）。

### Step 4 — 本机实证（tasks 3.1–3.3）

备份旧库 → 新 jar 8082 旁路 + 旧库副本实证修复 → 删旧库 8081 FRESH 重建 → 验证 22 语言 + 向导接口。

### Step 5 — 收尾

validation.md（4.1）、detect-changes（4.2）、按变更分组签入（4.3，显式文件清单排除并行变更）、`.superpowers-memory`（4.4）。

## 风险对策

- DDL 事务性：SQLite 表级 DDL 支持事务内回滚；修复全程单事务，失败可重入。
- 唯一索引冲突：WARN 跳过（应用层 `LanguageIdentifierResolver` 已有防御）。
- 并行变更污染：签入显式路径清单；构建 jar 仅用于本地验证。
