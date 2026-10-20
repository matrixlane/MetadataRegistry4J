# 遗留语言表 Schema 漂移修复设计（启动期修复器 + 本机空库重建）

日期：2026-10-20
状态：待用户确认
变更名（OpenSpec）：`legacy-language-schema-repair`

## 1. 背景与问题实证

本机 `data/metadata-registry.db` 为远古 ddl-auto 时代遗留库（`FreshDatabaseInitializer` 判定的 `LEGACY_UNHISTORIED`）：111 张表、无 `flyway_schema_history`、全部业务数据仅 `account`×1 + `identifier_strategy_config`×1（均为 `StaticDataPresetService.presetAll()` 可幂等重种的预置行，PRAGMA/SQL 实证 2026-10-20），8 张语言表全空。

`language_identification` 物理表残留三个遗留列（PRAGMA 实证）：

```
language_id blob not null unique   -- 阻断预置的 NOT NULL 列
language_name varchar(255)         -- 非标准扩展（现属 ext 卫星表）
territory_name varchar(255)        -- 非标准扩展（现属 ext 卫星表）
```

而当前实体 `LanguageIdentification`（`metadata-registry-common-facilities`，`@StandardReference("ISO/IEC 11179-3:2023, 6.3.7")`）的标准列仅：`id`、`language_identifier`、`script_identifier`、`geopolitical_territory_identifier`、`private_use_qualifier`（+ `language_variant`/`language_extension` 两张集合表）。

后果链（2026-10-20 19:39 启动日志实证）：

1. 预置脚本 `V20260919003__Seed_builtin_languages.sql` 的 INSERT 列清单不含 `language_id` → 在漂移表上每条必失败（`SQLITE_CONSTRAINT_NOTNULL`）；
2. `dd8d9dbe` 的"单条语句失败仅 WARN 跳过"兜底使启动不崩溃，但 22 条语言代码 + 22 条中文名称**全部跳过**——内置语言永远种不进，向导"文档语言"等语言数据源为空；
3. `ddl-auto=update` 只增列不删列 → 遗留列永久驻留，漂移不可自愈。

分层规则核对（AGENTS.md）：`language_name`/`territory_name` 正是 `ext_language_identification` 卫星表（V20260919001）的列；遗留 `enabled` 列（baseline 代漂移形态）对应 `language_governance` 卫星表（V20260901012）。修复的数据流向与 mdr/ext 分层规则方向一致。

## 2. 目标 / 非目标

### 目标

1. **启动期遗留修复器**（代码级，SQLite 方言守卫）：识别 `language_identification` 漂移签名 → 遗留扩展数据迁往对应卫星表（mdr → ext 归位）→ 标准表重建为当前实体形状 → `LanguagePresetSeeder` 重播成功。其他有真实数据的同类遗留库可自愈。
2. **本机空库重建**（运维级）：删除漂移库，`FreshDatabaseInitializer` 走 FRESH 路径（共同基线 v20261009012 + 当前实体建表），全部 111 张表归位到最新代码形状；预置数据自动重种。
3. 验证闭环：修复器对**真实遗留库文件副本**自愈实证（旁路实例）；本机重建后 22 语言 + 名称入列、向导接口可用。

### 非目标

- 其余遗留表的非语言列漂移修复（本机重建顺带归位全库；其他库其余表的漂移维持现状，另立变更）。
- Flyway 历史迁移脚本重写（`V20260723002__Schema_baseline.sql` 等不动）。
- SQL Server / 达梦方言的修复路径：遗留漂移是 SQLite ddl-auto 时代产物，团队库均为迁移建库；修复器对非 SQLite 方言 no-op。

## 3. 方案比较与裁定

| 方案 | 内容 | 裁定 |
|---|---|---|
| **A. 修复器 + 本机重建** | 系统性启动修复器（有数据遗留库自愈）+ 本机空库走 FRESH 重建（全库归位） | **采用（用户裁定 2026-10-20）** |
| B. 仅本机重建 | 删库走 FRESH，零代码最快 | 放弃：其他有数据的遗留个人版库无法自愈 |
| C. 仅修复器 | 不删本机库，启动时就地修语言表 | 放弃：本机其余 110 张远古表保持漂移 |

## 4. 详细设计

### 4.1 修复器 `LegacyLanguageSchemaRepairer`（新类，`metadata-registry-web` → `web/config`）

- `@Component`、`ApplicationRunner`、`@Order(5)`：先于 `StaticDataPresetRunner`（`@Order(10)`，启动期跑 `presetAll()` 含语言预置重播），后于 EMF/Flyway 初始化完成。
- **方言守卫**：`DatabaseEnvironment.vendor() == SQLITE` 才执行；否则 debug 日志跳过（no-op）。
- **漂移签名判定**：`PRAGMA table_info(language_identification)`；标准列集合 = `{id, language_identifier, script_identifier, geopolitical_territory_identifier, private_use_qualifier}`；存在任何额外列即判定漂移。该判定覆盖已实证的三代漂移形态：
  - 本机代：`language_id NOT NULL UNIQUE` + `language_name` + `territory_name`；
  - 单 `language_id` 代（`LanguagePresetSeederTest.toleratesDriftedLegacySchema` 模拟形态）；
  - baseline 代：`enabled`（`V20260723002` 快照形状）。
- **数据归位**（与重建同一事务，全部带 `NOT EXISTS` 幂等守卫）：
  - `language_name`/`territory_name` → `ext_language_identification(id, language_id, language_name, territory_name)`（卫星表缺失时按 `V20260919001` DDL 建表 + 唯一索引）；
  - `enabled` → `language_governance(id, enabled, language_id)`（表缺失时按 `V20260901012` DDL 建）；
  - 不可归位列（如 `language_id`）：若存在非空值，WARN 记录将被丢弃的行数（语义不可映射，标准模型无此列；本机库该表为空）。
- **表重建**（单事务；动态列清单，源表缺失的当前列以 NULL 兜底；`PRAGMA foreign_keys=OFF` 尽力包裹——遗留 SQLite 库无声明式 FK，此仅为防御）：
  1. `CREATE TABLE language_identification_repair`（当前形状 5 列 + PK）；
  2. `INSERT INTO ... SELECT` —— **id 原值保留**（`definition`/`designation`/`reference_document_language`/`registration_authority_doc_language`/`registry_specification.default_language_id`/`language_extension`/`language_variant`/`ext_language_identification` 共 8 处按值引用 `language_identification.id`，`V20260926001` 实证）；
  3. `DROP TABLE language_identification` + `ALTER TABLE ... RENAME TO language_identification`；
  4. `CREATE UNIQUE INDEX IF NOT EXISTS uq_language_identification_identifier ON language_identification(language_identifier)`（对齐 `V20260926001` 目标形态；若既有数据冲突则 WARN 跳过不阻断——遗留库 Flyway 关闭，唯一索引为尽力而为）。
- **幂等**：修复后签名消失，二次启动自动跳过；单事务保证失败回滚、可重入。

### 4.2 本机重建步骤（运维操作，不入代码）

1. 构建含修复器的新 jar（`mvnw.cmd -pl metadata-registry-web -am package -DskipTests`，当前工作区另有 `administered-item-reference-documents-ui` 未签入修改——构建产物仅用于本地验证，**不随本变更签入**，签入时仅含本变更文件）；
2. `stop.bat` 停 8081 后端（5173 前端保留）；
3. 备份：`data/metadata-registry.db` → 副本文件（空库也留底，可回退）；
4. **旁路实证修复器**：以 `DB_URL=jdbc:sqlite:./data/<副本>.db`、`SERVER_PORT=8082` 启动新 jar → 观察日志：漂移签名命中 → 数据归位 → 表重建 → `LanguagePresetSeeder` 种入 22+22 无跳过 WARN；`GET :8082/api/v1/languages` 复核；
5. 停旁路实例，删除副本与本机旧库；
6. 8081 正式启动（`start.bat` 同款）→ `FreshDatabaseInitializer` FRESH 路径：验证 `flyway_schema_history`（baseline v20261009012）、`GET :8081/api/v1/languages` 22 条（zh-CN 名称"中文"/"中国"）、`GET /api/v1/init-wizard/status` 200。

### 4.3 测试（TDD，真实 SQLite 内存库，仿 `LanguagePresetSeederTest` 基建）

1. 漂移表（`language_id`+names，含数据行）→ 修复后：PRAGMA 列集合 == 当前形状；ext 表收到名称数据；id 保留；二次运行 no-op。
2. 漂移表（`enabled` 列 + 数据行）→ `language_governance` 收到 enabled 值；卫星已有行时 `NOT EXISTS` 守卫不重复。
3. 空漂移表 → 修复 → 续接 `LanguagePresetSeeder` 种入 22+22（端到端证明"修复后预置可重播"）。
4. 当前形状表 → 修复器 no-op（结构不变）。
5. 非 SQLite 方言（构造 `DatabaseEnvironment` SQLServer）→ no-op、零 SQL 执行。
6. 唯一索引数据冲突场景 → WARN 跳过、无异常抛出。

### 4.4 文档

- OpenSpec 变更 `legacy-language-schema-repair`：`proposal.md` / `design.md` / `specs/` / `tasks.md` / `validation.md`（沿用仓库既有结构）。
- README / ARCHITECTURE.md 无用户可见变化，不动。
- `.superpowers-memory/`：`CURRENT_STATE.md` + 会话 journal 收尾更新。

## 5. 风险与回退

| 风险 | 对策 |
|---|---|
| 修复器误伤健康库 | 签名判定要求"存在额外列"——当前形状（含 FRESH 新库）不触发；测试 4 守护 |
| 本机重建丢数据 | 已实证全库仅 2 行预置数据（启动自动重种）；删库前留备份文件，可整体回退 |
| 重建过程启动失败 | FRESH 路径为既有机制（`sqlite-empty-db-bootstrap` 已验证）；失败则恢复备份文件即回原状 |
| 旁路实例干扰本机 | 8082 端口 + 独立副本库，与 5173 代理、8081 互不干扰，验证后即删 |

