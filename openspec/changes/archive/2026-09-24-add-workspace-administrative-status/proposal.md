# 提案：工作空间与管理状态（add-workspace-administrative-status）

## Why

提交者与注册人员目前没有一个集中的入口处理"需要关注"的元数据对象：本人新增/编辑过的管理项散落在各模块列表中，注册人员审核也只能逐条进入注册项列表操作。同时 `Registration_State.administrative_status`（ISO/IEC 11179-3:2023 表 22，0..1 String）长期处于闲置的无约束文本状态，注册机构无法跟踪管理项在行政流程中的进行态（草稿、待审、已拒绝）。工作空间以管理状态为轴心，让"编辑→草稿→提交→审核→拒绝/回退"形成闭环。

## What Changes

- 新增 `AdministrativeStatus` 枚举（16 个常量，收录 ISO/IEC 11179-6:2023 附录 D.3 Table D.1 词表 + Draft/Rejected，剔除 "Approved as Candidate"），经 JPA AttributeConverter 与 `registration_state.administrative_status`（String 列）映射：内存枚举、持久化字符串，**零数据库迁移**。
- 管理状态生命周期：新建/编辑保存 → Draft；工作空间提交（单个/批量，弹窗手动设置 Submission_Record）→ 清空（Incomplete 落 `Initial`，用户裁定）；Request Candidate 成功后注册状态直推 Candidate、管理状态清空（附录 C 提交人自决，无审批）；主管申请合格 → Provisionally Qualified（复用 RECORDED→QUALIFIED 审核点）；Registrar 批准 → 注册状态 Qualified、管理状态清空；Registrar **拒绝（新驳回操作）** → Rejected；Rejected 修订保存 → 新 Draft。
- 新增一级菜单"工作空间"：草稿/待审核/已拒绝三视图，按归属规则（提交记录联系人或主管记录联系人 = 本用户）聚合；批量提交、Request Candidate、申请合格、批准/拒绝、修订、回退行操作。
- 版本回退：Recorded 及以上且管理状态为 Draft/Rejected 的 Item 可回退到上一提交版本（丢弃草稿/被拒绝版本），全量 DTO 快照存既有 `version_history.snapshot_json`，无新表无迁移。
- 提交人放行 `INCOMPLETE→CANDIDATE` 直推（仅限归属本用户的 Item，附录 C）。

## Capabilities

### New Capabilities

- `administrative-status`: AdministrativeStatus 枚举与 String 持久化映射；管理状态推进规则（Draft/Initial/Provisionally Qualified/Rejected/清空约定）；Recorded+ Draft/Rejected 的版本回退（全量快照 BASELINE）。
- `workspace`: 工作空间三视图聚合查询与计数；批量提交（Submission_Record 手动选择）；Request Candidate 端点与按钮可见性规则（Candidate 最小需求 + 归属，与管理状态无关）；行级操作权限标志。

### Modified Capabilities

- `registration-process`: 新增 Registrar 驳回操作（拒绝 RECORDED→QUALIFIED 申请，置管理状态 Rejected）——修订现有"系统 MUST NOT 提供驳回操作"需求；`INCOMPLETE→CANDIDATE` 对归属本用户的 Submitter 放行直推。
- `submission-record`: 工作空间提交（单个/批量）统一要求显式确认/设置 Submission_Record（联系人+组织），默认预填可修改。
- `standardize-menu-layout`: 一级菜单新增"工作空间"，位于概览之后、数据字典之前。

## Impact

- 后端（metadata-registry-web / common-facilities）：`AdministrativeStatus` + Converter、`AdministrativeStatusService`、`WorkspaceController/Service`、`RegistrationProcessService`（approve 清空 + reject）、`RegistrationLifecycleService`（迁移后清空 + Submitter 白名单）、`ItemAdministrationService`（编辑→Draft 钩子、BASELINE 捕获、提交服务）、`VersionHistoryService`（全量 DTO 快照 + kind）、17 个 Item Service 快照调用点、`AdministeredItemController`、`SecurityConfig`、`ApiCodes`、三方言索引迁移 V20260924001。
- 前端（metadata-registry-ui）：`AppLayout.vue`、`router/index.ts`、i18n×2、`resources.ts`（workspaceApi）、`WorkspaceView.vue` + `SubmissionRecordPicker.vue`、`e2e/menu.spec.ts`。
- 规范/文档：`standardize-menu-layout` 主 spec delta、README 页面清单、ARCHITECTURE.md（GitNexus 重生成）。
- 无Breaking变更：DB 列不变更；既有审批/迁移语义不变，仅新增驳回与清空副作用。
