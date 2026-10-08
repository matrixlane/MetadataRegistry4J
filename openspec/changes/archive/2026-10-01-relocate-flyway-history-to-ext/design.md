# 设计：Flyway schema 历史表迁出 mdr，归位 ext

## Context

团队版（SQL Server）Flyway 配置 `schemas: mdr,ext`，历史表落在首个 schema
（`mdr.flyway_schema_history`）。动机见 `proposal.md` - Why。已核实的约束：

1. Flyway `defaultSchema`（未设时取 `schemas[0]`）同时决定历史表落位与迁移
   会话 current schema（官方文档明示），二者无法独立配置。
2. 只改配置不搬表会触发既有库 re-baseline，重放全部 15 个基线后迁移
   （V20260919001..V20260929001，按"已应用不重跑"设计），已排除。
3. 15 个基线后迁移均已 schema 限定（逐一核查，含 V20260926001 带守卫的
   UNIQUE INDEX——索引随表落位 mdr）；基线前 29 个老迁移只在"全新空库全量
   重放"路径执行，而团队版全新安装走 Hibernate 建表 + 基线化，全量重放本就
   不可行（既有记录），基线机制保证老迁移永不重放。
4. 项目已有同款搬运先例：V20260901014 用 `ALTER SCHEMA ext TRANSFER
   mdr.<table>` 迁移 23 张表并保留约束/索引。

Superpowers 设计稿（已确认）：
`docs/superpowers/specs/2026-09-30-relocate-flyway-history-to-ext-design.md`。

## Goals / Non-Goals

**Goals:**

- Flyway schema 历史表物理归属 ext schema（既有库自动搬迁、全新库直接落位）。
- 既有库升级零迁移重放、零 re-baseline；历史行/索引/约束原样保留。
- 业务侧对历史表的清理/导出保留语义不变（按裸表名）。

**Non-Goals:**

- 不改基线前老迁移脚本（全量重放路径非团队版安装方式，且基线保证不重放）。
- 不把 `flyway_schema_history` 加入 `DatabaseEnvironment.EXT_TABLES`
  （EXT_TABLES 与 orm-team.xml 清单保持一致并由 OrmTeamXmlTest 守卫；
  flyway 表不是 ORM 实体，业务代码对其全部按裸表名处理）。
- 不处理"两表并存"异常态的自动行合并（跳过 + WARN，人工处置）。
- 不改 SQLite（personal）与达梦（用户即模式）行为。

## Decisions

### D1：配置用 `default-schema: ext`，而非调整 `schemas` 顺序

`schemas: mdr,ext` 集合语义（Flyway 管理的 schema 集）保持不变，仅显式指定
`default-schema: ${FLYWAY_DEFAULT_SCHEMA:ext}`。若改用 `schemas: ext,mdr`
让首 schema 承担历史表，会同样改变会话 current schema，但可读性差、且
`schemas` 顺序被读者误认为有管理优先级含义。default-schema 语义直白，
并为达梦留出 `FLYWAY_DEFAULT_SCHEMA` 逃逸口（沿用 `FLYWAY_SCHEMAS` 模式）。

### D2：既有库搬迁用启动期组件，而非 Flyway 迁移脚本

迁移脚本在 Flyway 探测历史表**之后**才执行——配置已指向 ext 时 Flyway 先
re-baseline 再跑脚本，重放不可避免（B 方案，已否决）。启动期组件
（`FlywayHistoryRelocator`）由 BFPP 挂到 Flyway 迁移初始化器之前（评审修正：
**经类型查找 `FlywayMigrationInitializer`**——Boot 3.5 的 bean 名为
`flywayInitializer`，字面量查找会静默失效；同修正 `FlywayAfterJpaConfig`
的失效字面量），保证搬运先于 Flyway 任何探测：目标处无表且主 schema 有表且
目标 schema 存在 → `ALTER SCHEMA <目标> TRANSFER <主>.flyway_schema_history`。

### D2a：搬迁目标取自 Flyway 自身配置（评审新增）

目标 schema = `Flyway.getConfiguration().getDefaultSchema()`（未设时取
`schemas[0]`），与 `spring.flyway` 配置永不分叉（评审发现：若取自
`DB_EXT_SCHEMA`，与 `FLYWAY_DEFAULT_SCHEMA` 各设各的会把表搬去 Flyway
找不到的地方，静默 re-baseline）。源 schema 仍取 `DatabaseEnvironment.schema()`。

### D2b：搬迁 DDL 显式 autoCommit（评审新增）

连接池统一 `auto-commit: false`（application.yml），不显式提交的 DDL 会被
连接池在 close 时按脏状态回滚——"日志报成功、实际未搬迁"。搬迁前
`setAutoCommit(true)`、执行后恢复原值。

### D2c：目标 schema 不存在 → fail fast（评审新增）

这是唯一会把 Flyway 送进 re-baseline 的状态，与本组件目的矛盾：改为抛
`IllegalStateException` 中断启动（原实现 WARN 跳过）。

### D3：`ALTER SCHEMA TRANSFER` 整体搬迁，不做行合并

installed_rank、installed_on、checksum 等行数据与
`flyway_schema_history_s_idx` 索引随表整体迁移（SQL Server 语义 + 项目
V20260901014 实证），Flyway 校验只看"已记录版本集合"，语义完全等价。
行合并需重排 installed_rank 且收益为零，放弃。

### D4：方言范围限定 SQL Server

`DatabaseEnvironment.vendor() == SQLSERVER` 才执行；SQLite 无 schema、达梦
用户即模式（历史表本就在连接用户下），均 no-op。schema 名取
`env.extSchema()`/`env.schema()`，尊重 `DB_EXT_SCHEMA`/`DB_DEFAULT_SCHEMA`。

### D5：新增 OpenSpec 需求而非修改既有需求原文

`system-initialization` 既有需求"初始化向导清理保留静态预置表"中
flyway_schema_history 的**保留语义**不变，仅**位置**变化——不改原文，
新增"Flyway schema 历史表位于 ext schema"需求承载位置契约。

## Risks / Trade-offs

- 既有库处于"ext 与 mdr 各有一张历史表"的异常态 → 搬运器跳过并 WARN，
  不自动合并（避免在 Flyway 管理的表上做行级决策），人工处置。
- 目标 schema 不存在（唯一会触发 re-baseline 的状态）→ 搬运器 fail fast
  中断启动（D2c），接线测试钉死顺序保证（D2）。
- `default-schema` 同时改变迁移会话 current schema → 已逐一核查基线后迁移
  全部 schema 限定；基线前迁移不重放（见 Context 3）。
- 达梦部署未设置 `FLYWAY_DEFAULT_SCHEMA` 时该值为 ext，与达梦"用户即模式"
  模型不符 → 沿用既有 `FLYWAY_SCHEMAS` 逃逸模式文档化；达梦多迁移脚本
  本就处于"待实测"状态（既有项，与本变更无关）。

## Migration Plan

1. 发布含本变更的版本。
2. 既有团队库：应用启动时 `FlywayHistoryRelocator` 自动搬运
   （ext schema 已由 schema-init.sql 保证存在）；随后 Flyway 在 ext 找到
   完整历史，validate 通过，仅应用未来新迁移。
3. 验证（SQL Server）：
   `SELECT COUNT(*) FROM ext.flyway_schema_history` 等于旧 mdr 表行数；
   `SELECT OBJECT_ID('mdr.flyway_schema_history') IS NULL`；
   启动日志无 re-baseline、无基线后迁移重放记录。
4. 回滚：把 `FLYWAY_DEFAULT_SCHEMA` 指回 `mdr` 并人工把
   `ext.flyway_schema_history` TRANSFER 回 mdr（两步，需在 Flyway 启动前
   完成第二步——与本搬运器同理）。

## Open Questions

无（达梦默认取值问题已在 D1/风险中按既有逃逸模式处理）。
