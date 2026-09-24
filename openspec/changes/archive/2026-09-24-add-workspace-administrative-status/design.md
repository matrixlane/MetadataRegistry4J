# 设计：工作空间与管理状态（add-workspace-administrative-status）

详细需求背景见 `proposal.md`；完整的标准依据、决策记录与文件级改造清单见已确认的 Superpowers 设计文档 `docs/superpowers/specs/2026-09-23-workspace-administrative-status-design.md`（用户已逐项确认）。本文只记录技术决策。

## Context

- `Registration_State.administrativeStatus` 现状为无约束 `String`（`RegistrationState.java:35`），DB 列 `varchar(255)` 可空、无 CHECK 约束——具备"内存枚举 + 持久化字符串"改造条件，零迁移。
- 项目已有 `MappingDegreeConverter`（`autoApply`、枚举自持标准串、读入大小写不敏感）作为 Converter 先例。
- 既有审批模型 `RegistrationTransitionApproval` 只有累积批准 + quorum 生效，**无驳回路径**；`RECORDED→QUALIFIED` 已是默认审核点。
- 版本现状：实体表单行 = 当前版本（编辑就地更新）；`version_history` 存 JSON 快照但仅含指称/定义；`ScopedIdentifier` 版本状态（DRAFT/PUBLISHED）与管理状态是正交概念。
- SQLite 单条 SQL 64 表 join 上限：跨 Item 子类查询须沿用 type() 探测 + `getReference` 代理手法，避免联合子类整图 join。

## Goals / Non-Goals

- Goals：管理状态闭环（Draft→提交→待审→批准/拒绝→修订/回退）；工作空间三视图聚合与批量操作；Recorded+ 版本回退；三方言（SQLite/SQL Server/达梦）一致。
- Non-Goals：Table D.1 其余推进值（Provisionally Candidate/Recorded/Standard 等）的操作实现（枚举仅收录取值）；Request Candidate 的待办提醒增强；`APPROVED_AS_CANDIDATE` 取值（用户裁定剔除）。

## Decisions

1. **枚举映射用 `@Converter(autoApply = true)` 而非 `@Enumerated`**：Converter 自持标准落库串（小写、含空格），读入大小写不敏感；DB 列不变、无 CHECK，符合 `registration-status` 主 spec 的"读取兼容标准书写形式"原则。
2. **管理状态就地更新当前 `Registration_State` 行**：不新建状态行、不动 `previous_state` 链；注册状态变更仍走 `writeTransition`。写入集中在新的 `AdministrativeStatusService` 便于校验与审计。
3. **"清空约定"为全局落点规则**（用户决策 4 推衍）：提交/批准/回退/Request Candidate/其余注册状态迁移后管理状态一律 `null`；唯一例外 Incomplete 提交 → `INITIAL`。
4. **驳回复用审核流而不新建状态机**：`POST /administered-items/{id}/transitions/reject` 置 `REJECTED` + 作废该迁移未生效批准；注册状态不变（D.1.2 拒绝+补救期语义）。
5. **全量快照存既有 `version_history.snapshot_json`**（用户决策 3）：无新表无迁移；JSON 外包 `meta{kind, registrationStatus, administrativeStatus}`；`kind=BASELINE`（进入 Draft 前一刻，回退目标）/ `DRAFT_EDIT` / `SUBMITTED` / `ROLLED_BACK`。回退经 Item Service 内部 restore 路径写回（绕过"编辑→Draft"副作用）。列表直查实体表天然只显示当前版本，加 `registration_state(administrative_status)` 等索引即满足性能。
6. **工作空间归属规则**：当前账号绑定联系人 ∈ {最近 SubmissionRecord.contact, StewardshipRecord.contact}；Registrar 审核叠加既有机构活动 Registrar 资格校验。
7. **Submitter 放行仅限一条迁移**：`INCOMPLETE→CANDIDATE` 且在 `RegistrationProcessService.execute` 归属路径白名单，不放开其他迁移。

## Risks / Trade-offs

- [全量快照体积增长] → 仅 9 类受审计 Item 类型；LOB 列；草稿期编辑保留快照以支持版本历史 UI，规模可控。
- [驳回与"批准累积"模型的交互] → reject 显式作废该 (item,from,to) 的未生效批准并记录驳回人/时间，避免陈旧批准被误用。
- [并发提交/回退] → 操作前按库内当前管理状态复核（乐观校验），无锁；部分失败逐条返回结果。
- [Converter 未知值抛错] → 列现状为空，无存量值风险；响亮失败优于静默吞掉。
- [Submitter 放行误用] → 服务端重复校验归属与 Candidate 最小需求，不信任前端标志。

## Migration Plan

1. 三方言索引迁移 V20260924001（`registration_state.administrative_status)` 等）；无表结构变更。
2. 部署后既有 Item 管理状态列为 NULL（= 无进行中行政流程），与工作空间视图定义一致，无需数据回填。
3. 回滚策略：代码回滚即恢复（列与数据不变）；索引可保留无害。

## Open Questions

无（用户在 Superpowers 阶段已逐项确认决策 1–6）。
