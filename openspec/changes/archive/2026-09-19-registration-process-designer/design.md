# Design: registration-process-designer

## Context

现行状态迁移：`RegistrationLifecycleService` 内静态硬编码 `ALLOWED_TRANSITIONS`（迁移白名单）与 `ROLE_PERMISSIONS`（ADMIN/REGISTRAR 角色矩阵），`POST /administered-items/{id}/transition` 单端点即时生效，无审核概念。用户已确认的六项决策见 `docs/superpowers/specs/2026-09-18-registration-process-design.md` §1；标准依据（Table 30、11179-6 §4.3/§4.4）见同文档 §2。

## Goals / Non-Goals

**Goals**

- 流程规则（迁移集、审核点、审核人模式、通过规则）成为按注册机构作用域的持久化数据，标准默认值以种子形式落地；
- 状态迁移经"执行"（无需审核）或"批准累积达 quorum 自动生效"（需审核）两条路径完成；
- 只读流程展示页向所有登录用户呈现标准流程与条文释义；
- 注册项列表提供状态过滤与逐迁移的操作面板。

**Non-Goals（本期明确排除）**

- 流程定义的编辑 UI 与自定义 Role（推迟，未来聚焦 administrative_status）；
- 驳回操作、发起审核动作（决策 3：审核只有"批准"）；
- 审批通知、每 RA 定制管理界面。

## Decisions

### D1：规则入库而非继续硬编码

`registration_transition_rule`（含 `registration_transition_rule_approver` 子表）按 `(registration_authority_id, from_status, to_status)` 唯一。标准默认规则由 `RegistrationProcessService.ensureSeed` 幂等播种（应用层而非迁移脚本，便于联邦新增 RA 时复用）。`RegistrationLifecycleService` 的白名单查询切换到规则表（带缓存）。

- 备选：代码常量 + 仅审批表 —— 否决，理由见设计文档 §3 方案 B（决策 4 的每 RA 结构将无处安放）。

### D2：审批为"累积批准、达额生效"单操作模型

`registration_transition_approval` 记录 (item, from, to, registrar) 唯一批准；达到 quorum 时在同一事务内完成状态迁移并清除该迁移的审批；item 状态变化时清除其全部未生效审批（防陈旧）。无驳回：不批准即不发生，与 11179-6 §4.3.3.1.5"未获批则保持原状态"相容。

- 备选：待审申请单（发起→待审→批准/驳回）——否决，超出决策 3 的交互定义。

### D3：默认审核点四处（标准三处 + 治理加严一处）

`RECORDED→QUALIFIED`、`QUALIFIED→STANDARD`、`STANDARD→PREFERRED_STANDARD` 默认 `requires_approval=true`（11179-6 §4.4 d) 的明确强制）；`CANDIDATE→RECORDED` 默认 `requires_approval=true`（2026-09-19 用户决策：本注册表治理加严，进入 Recorded 须经 Registrar 人工复核，超出标准最低要求）。其余迁移默认无需审核（决策 3："没有定义则默认没有审核需要"）。默认审核人 = 全部 Registrar、任一人通过。既有库由 `ensureSeed` 调和（字段漂移归位）收敛。

### D4：批准人资格 = 活动 Registrar 身份

批准操作要求当前账号的活动 Registrar 身份归属该 Administered Item 的 `registrationAuthority`，且在规则审核人集合内（`ALL_REGISTRARS` 时即该项 RA 下全部 Registrar）。复用既有 `ActiveRegistrarService` / `ContactAccountBindingService`，不引入新角色体系（决策 2）。

### D5：无需审核迁移的执行权限沿用现有矩阵

`execute` 路径保持现行 ADMIN/REGISTRAR 角色语义与 Recorded+ 强制属性校验，避免本期权限模型膨胀。

### D6：旧 transition 端点移除，编辑表单状态只读

`POST /administered-items/{id}/transition` 删除；`ItemAdministrationService` 更新路径拒绝状态字段变更（状态只能经 transitions/execute|approve 改变）；前端 `ItemRegistrationStateSection` 状态字段只读。这落实"状态迁移不再由下拉菜单直接设置"。

- 风险：既有 e2e/集成测试若经编辑表单改状态会失败 → tasks 中列排查与适配任务。

### D7：流程页用 Vue Flow 只读渲染

新增 `@vue-flow/core`（Vue ≥3.3 兼容，MIT）。节点悬停 Tooltip 的中文释义与英文标准原文放前端 i18n（`registrationProcess.*`），后端只下发结构与规则数据。

## Risks / Trade-offs

- [三方言迁移增加维护面] → DDL 保持纯 CREATE TABLE/索引的可移植语法，三目录同名同版；sqlite 侧遵循现有 blob 主键 + check 约束风格。
- [规则表缓存导致规则变更不即时] → 本期规则仅种子写入、无编辑入口，缓存在实现层可简单做启动时加载；未来编辑 UI 落地时加失效广播。
- [多 Registrar 并发批准同一迁移] → quorum 判定与状态迁移在同一事务内，唯一约束兜底重复批准。
- [Recorded+ 强制校验在批准生效时刻失败] → 生效事务内复用 `validateMandatoryAssociations`，失败则整个批准回滚并返回校验错误。
- [无归属注册机构的遗留条目] → 底线回退路径（无 RA 时回退标准静态迁移集）中，默认需审核迁移（RECORDED→QUALIFIED、QUALIFIED→STANDARD、STANDARD→PREFERRED_STANDARD，依 4.4 d)；CANDIDATE→RECORDED，治理加严）一律拒绝直推（1120），`availableTransitions` 对这几条迁移标记"需要审核"但不可执行亦不可批准。**有意偏差**：ISO/IEC 11179-6:2023 4.4 d) 要求推进到 Qualified 及以上须经注册机构批准——无 RA 即无批准主体，放行会绕过标准强制的审核点；非审核点迁移（如 RECORDED→RETIRED、PREFERRED_STANDARD→STANDARD）仍按底线放行。
- [并发批准撞唯一约束返回 500] → approve 对 `DataIntegrityViolationException` 映射为 1122（重复批准）：先查后写存在竞态，(item, from, to, registrar) 唯一约束兜底，冲突语义即"本 Registrar 已批准过"；ensureSeed 对唯一约束冲突按"已播种"幂等跳过（启动 Runner 与首请求并发场景，saveAndFlush 使冲突在方法内可捕获）。

## Migration Plan

1. 三方言迁移脚本建两张表；
2. 应用启动（及新 RA 创建）时幂等播种默认规则；
3. 存量 Administered Item 数据不变（状态历史链不受影响）；
4. 回滚：删除表与新端点即回到静态行为（无存量数据依赖新表）。

## Open Questions

- 审批进度是否需要在数据字典详情页同步露出（本期不做，观察使用反馈）。
