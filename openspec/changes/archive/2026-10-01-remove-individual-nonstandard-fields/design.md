# Design: remove-individual-nonstandard-fields

详见 `docs/superpowers/specs/2026-09-30-remove-individual-nonstandard-fields-design.md`（用户已确认的完整设计）。本文档为 OpenSpec 侧技术设计摘要。

## Context

`mdr.individual` 现有非标准列 `identifier`（V20260917001）与 `avatar`（V20260901016）。`identifier` 来自已作废变更 `add-individual-identifier`（已 `git rm`）；`avatar` 语义为账号级属性但宿主错放在标准表上。`account` 表在 V20260901014 已从 mdr 迁入 ext schema（SQL Server），SQLite 无 schema 概念留 main。约束：三方言迁移语义一致、幂等；SQL Server 真实库存在对 `mdr.individual` 的 QP 错误 8624 史（DELETE 触发，非 DDL）；工作区有并行会话未提交 WIP（与本变更文件零重叠）。

## Goals / Non-Goals

**Goals:**

- `mdr.individual` 列集与 ISO/IEC 11179-3:2023 §6.3.2 Table 1 严格一致（name/title/mail_address/email_address/phone_number + id）
- avatar 以 `ext.account.avatar` 存续，存量数据搬迁不丢失（孤儿 Individual 行除外，属 2026-09-30 清理对象）
- 头像语义与 Individual 生命周期解耦

**Non-Goals:**

- `mdr.registrar.identifier`（同为非标准字段）——记 backlog，本次不动
- mdr 其他表的标准符合性审计——记 backlog
- 旧 avatar 端点 `/auth/me/individual/avatar` 的重定向兼容（前后端同源发布，无第三方消费者）

## Decisions

1. **硬移除而非软废弃**：一次性删列+清代码。备选两阶段（先停写后删列）——单库单发仓库无兼容期必要，徒增复杂度。
2. **数据搬迁用关联子查询**：`UPDATE ... SET avatar = (SELECT ... WHERE individual.id = account.individual_id)`，三方言通用；备选 `UPDATE ... FROM` 方言差异大。
3. **avatar 端点改名 `/auth/me/avatar`**：存储宿主已变，路径继续带 `individual` 误导语义；改动面 3 端点 + 3 处 UI 调用 + openapi。
4. **上传头像不再创建 Individual**：头像纯账号级；原"无 Individual 先创建"行为随宿主迁移自然消失（测试用例同步改写）。
5. **SQLite 用裸 `DROP COLUMN`（不带 IF EXISTS）**：3.35+ 支持；项目既有失败先例是 `DROP COLUMN IF EXISTS`。实测驱动内置 < 3.35 时回退重建表法（V20260921001 模式）。

## Risks / Trade-offs

- [SQL Server 该实例对 individual 表有 QP 8624 史] → 本次为 DDL 非 DML；触发则启用换表法（备份→新表→重命名→补 PK/FK，2026-09-30 已实操一遍）
- [SQLite 驱动内置版本可能 < 3.35 不支持 DROP COLUMN] → 迁移任务第一步 scratch 副本库实测，失败切重建表法
- [孤儿 Individual 的 avatar 数据丢失] → 可接受：该类行属 2026-09-30 清理对象；设计已记录
- [API 契约删字段/端点改名] → 前后端同源发布，同变更组内同步修改，无外部消费者

## Migration Plan

1. 代码与迁移同变更组提交（先 TDD 红绿，后打包）
2. 后端重启时 Flyway 自动应用 `V20260930001`（SQLite/SQL Server）；达梦待实测
3. 验证：scratch 副本库造数（带 avatar 账号 + 孤儿 individual）应用迁移 → 真实库重启应用 → 冒烟（头像上传/回显/删除、Individual 列表、关键词搜索）
4. 回滚策略：代码 `git revert`；数据列删除不可回滚——迁移应用前确认 scratch 验证通过，真实库应用前可由 DBA 备份 `individual`/`account` 两表

## Open Questions

无（D1–D3 决策已在设计评审中由用户确认）。
