# Proposal: registration-process-designer

## Why

当前 Administered Item 的注册状态迁移通过下拉菜单直接设置，缺少 ISO/IEC 11179-6:2023 规定的审核环节（如推进到 Qualified 及以上"shall require … approval of the registration authority"），也没有向用户展示标准注册流程的渠道。需要把状态机规则从静态代码升级为持久化的流程定义，提供只读流程展示页（教学渠道），并在注册项操作中以"执行/批准"方式完成状态迁移。

## What Changes

- 新增**注册流程页**（`/registration-process`）：基于 Vue Flow 的只读流程图，展示 10 个 Registration_Status 状态与允许迁移；节点悬停显示该状态在 ISO/IEC 11179-3 Table 30 与 11179-6 §4.3.3 的完整释义（中文译文 + 标准英文原文）；边展示迁移规则（是否需审核、审核人模式、通过人数）。
- 新增**流程迁移规则**持久化模型（按注册机构作用域）：迁移白名单、是否需审核、审核人模式（全部 Registrar / 指定若干）、通过规则（任一人 / 至少 N 人）、操作命名；标准默认值作为种子数据（Recorded→Qualified、Qualified→Standard、Standard→Preferred Standard 默认需审核，依据 11179-6 §4.4 d)；Candidate→Recorded 默认需审核，2026-09-19 治理加严决策）。
- 新增**审批累积模型**：合格 Registrar 对需审核迁移执行"批准"（无驳回、无发起动作）；达到通过人数即事务内自动完成状态迁移；状态变化后清除陈旧审批。
- 注册项列表"状态迁移"下拉改为**推进流程面板**（按迁移逐条展示操作名/审批进度/执行或批准按钮）；列表新增注册状态过滤；编辑表单中的状态字段改为只读。
- 菜单：注册模块新增"注册项"（`/administered-items`）；"注册流程"改指 `/registration-process`。
- **BREAKING**：移除 `POST /api/v1/administered-items/{id}/transition` 端点，由 `POST …/transitions/execute` 与 `POST …/transitions/approve` 替代。
- **明确不做**（推迟）：自定义 Role 流程定制、流程定义的管理员编辑 UI、每 RA 定制的管理界面、审批通知。

## Capabilities

### New Capabilities

- `registration-process`：注册流程定义、展示、迁移执行与审批的完整行为规范。

### Modified Capabilities

- `standardize-menu-layout`：注册模块菜单新增"注册项"项，"注册流程"项指向新的流程展示页。

## Impact

- **后端**：新增实体 `RegistrationTransitionRule`（含审核人子表）、`RegistrationTransitionApproval`；新增 `RegistrationProcessService` 与 `RegistrationProcessController`；`RegistrationLifecycleService` 迁移白名单数据源由静态 Map 切换为规则表；`ItemAdministrationService` 编辑路径不再接受状态字段变更；`AdministeredItemController` 旧 transition 端点移除。
- **前端**：新增页面 `RegistrationProcessView.vue`；改造 `AdministeredItemList.vue`（推进流程面板 + 状态过滤）与 `ItemRegistrationStateSection.vue`（状态只读）；新增依赖 `@vue-flow/core`；菜单/路由/i18n 同步。
- **数据库**：三方言（sqlserver/sqlite/dm）迁移脚本各一份；规则种子数据由应用层幂等播种。
- **文档**：README 页面清单、`openspec/specs/standardize-menu-layout/spec.md`、架构文档按需重新生成。
- **设计依据**：`docs/superpowers/specs/2026-09-18-registration-process-design.md`（已经用户确认）。
