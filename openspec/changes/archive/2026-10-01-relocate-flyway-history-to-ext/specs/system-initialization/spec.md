# system-initialization 的 Delta

## ADDED Requirements

### Requirement: Flyway schema 历史表位于 ext schema

Flyway 的 schema 历史表是工具表，MUST NOT 与 ISO/IEC 11179 标准表同处 mdr
schema。团队版（SQL Server）部署 MUST 保证 schema 历史表位于 ext schema；
既有库升级时 MUST 在 Flyway 执行任何迁移探测之前把
`mdr.flyway_schema_history` 整体迁入 Flyway 实际查找历史表的位置（搬迁
目标取自 Flyway 自身配置，MUST 与历史表落位一致；历史行、索引、约束原样
保留），且升级过程 MUST NOT 触发 re-baseline 或任何基线后迁移的重放。搬迁
目标 schema 不存在时系统 MUST 中断启动（该状态必然导致 re-baseline），
MUST NOT 仅告警放行。若 ext 与
mdr 同时存在历史表（异常态），系统 MUST 跳过自动搬迁并记录告警，MUST
NOT 自动合并行数据。SQLite（无 schema 概念）与达梦（用户即模式）部署
MUST NOT 因此产生 schema 变更行为。

#### Scenario: 既有团队库升级自动搬迁

- **WHEN** 一个历史表位于 `mdr` 的既有 SQL Server 库启动升级后的应用
- **THEN** 启动完成后 `ext.flyway_schema_history` 存在且行数与迁移前
  `mdr.flyway_schema_history` 一致，`mdr.flyway_schema_history` 不再存在，
  启动日志中没有 re-baseline 记录，也没有已应用迁移的重放记录

#### Scenario: 全新团队库直接建于 ext

- **WHEN** 全新 SQL Server 库完成团队版初始化（Hibernate 建表 +
  Flyway 基线化）
- **THEN** schema 历史表自始建于 ext schema，mdr schema 中不存在
  flyway_schema_history

#### Scenario: 异常双表跳过并告警

- **WHEN** 启动时 ext 与 mdr 中同时存在 flyway_schema_history
- **THEN** 不执行搬迁，日志输出告警，Flyway 以 ext 中的历史表为准继续启动

#### Scenario: 目标 schema 缺失中断启动

- **WHEN** 启动时 mdr 存在 flyway_schema_history 而历史表目标 schema（Flyway
  配置落位）不存在
- **THEN** 启动失败并给出明确错误信息（继续启动将触发 re-baseline 并重放
  基线后迁移），不执行任何迁移

#### Scenario: 方言无关业务语义保持不变

- **WHEN** 对任何受支持方言执行数据库清理（初始化向导 / 恢复出厂）或
  迁移导出
- **THEN** flyway_schema_history 仍按裸表名被保留/排除，与其位于哪个
  schema 无关；SQLite 与达梦部署不产生任何 schema 变更
