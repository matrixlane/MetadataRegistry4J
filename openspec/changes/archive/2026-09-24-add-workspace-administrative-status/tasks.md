# Tasks：工作空间与管理状态（add-workspace-administrative-status）

实现契约见 `specs/`（administrative-status、workspace、registration-process、submission-record、standardize-menu-layout），技术方案见 `design.md` 与 `docs/superpowers/specs/2026-09-23-workspace-administrative-status-design.md`。

## 1. 管理状态枚举与持久化（common-facilities）

- [x] 1.1 新增 `AdministrativeStatus` 枚举（16 常量：DRAFT/INITIAL/PROVISIONALLY_CANDIDATE/PROVISIONALLY_RECORDED/APPROVED_AS_RECORDED/PROVISIONALLY_QUALIFIED/APPROVED_AS_QUALIFIED/PROVISIONALLY_STANDARD/APPROVED_AS_STANDARD/PROVISIONALLY_PREFERRED_STANDARD/APPROVED_AS_PREFERRED_STANDARD/PROVISIONALLY_SUPERSEDED/APPROVED_AS_SUPERSEDED/PROVISIONALLY_RETIRED/WITHDRAWN/REJECTED，MUST NOT 含 APPROVED_AS_CANDIDATE）与 `AdministrativeStatusConverter`（autoApply；写库小写标准串；读库大小写不敏感容忍标准书写形式；未知值抛枚举解析错误；NULL 合法）。验证：common-facilities 新增单元测试覆盖写入/读取往返、Title Case 读取、未知值抛错、NULL 映射，全部通过。
- [x] 1.2 `RegistrationState.administrativeStatus` 由 `String` 改为 `AdministrativeStatus` 类型；确认 DB 列 `varchar(255)` 可空无 CHECK 无需迁移。验证：`mvn -pl metadata-registry-common-facilities,metadata-registry-web compile` 通过；web 模块既有测试基线无失败。

## 2. 管理状态核心服务（metadata-registry-web）

- [x] 2.1 新增 `AdministrativeStatusService`：管理状态写入统一入口（合法性校验 + 落点规则：注册状态→稳定值映射，唯一例外 INCOMPLETE→INITIAL，其余落点一律清空 null）。验证：新增单元测试覆盖映射表全分支与异常入参。
- [x] 2.2 `ItemAdministrationService` 编辑→Draft 钩子：创建/更新管理项保存时置 `DRAFT`（经 2.1）；REJECTED 修订保存→新 DRAFT；不触碰注册状态。验证：TDD 单测——新建保存后为 DRAFT、Recorded 编辑后为 DRAFT 且注册状态不变、Rejected 修订后为 DRAFT。
- [x] 2.3 `RegistrationLifecycleService` 迁移钩子：任何注册状态迁移生效后（writeTransition/applyTransition 路径），管理状态经 2.1 清空。验证：TDD 单测——INCOMPLETE→CANDIDATE、QUALIFIED→STANDARD 等迁移后管理状态为 null；previous_state 链不变。
- [x] 2.4 `RegistrationProcessService.approve` 生效副作用：quorum 达成迁移生效时经 2.1 清空管理状态。验证：TDD 单测——RECORDED→QUALIFIED 生效后管理状态 null。

## 3. 版本快照与回退

- [x] 3.1 `VersionHistoryService` 快照全量 DTO 化：快照 JSON 外包 `meta{kind, registrationStatus, administrativeStatus}`，item 为完整 Response DTO（无环序列化）。验证：TDD 单测——快照含全部可编辑字段与 meta；既有 listVersions/diff 接口不破。
- [x] 3.2 BASELINE 捕获：管理项从非草稿进入草稿的编辑前一刻写 `BASELINE` 全量快照（ItemAdministrationService 更新路径，17 个受审计 Item 类型的公共调用点）。验证：TDD 单测——首次草稿编辑前生成 BASELINE，二次草稿编辑不覆盖原基线。
- [x] 3.3 回退服务：注册状态 ∈ {RECORDED, QUALIFIED, STANDARD, PREFERRED_STANDARD, SUPERSEDED, RETIRED} 且管理状态 ∈ {DRAFT, REJECTED} 时可回退——恢复最近 BASELINE 内容（内部 restore 路径绕过"编辑→Draft"副作用）、写 `ROLLED_BACK` 快照、管理状态清空；无 BASELINE 且 Rejected 内容无变更时降级为仅清状态；其余无基线 422。验证：TDD 单测覆盖三个场景与拒绝场景。
- [x] 3.4 三方言索引迁移 V20260924001：`registration_state(administrative_status)`、`administered_item(stewardship_record_id)`（如缺）。验证：sqlite 全新库重建 + SQL Server 真实库应用成功（既有迁移模式）。

## 4. 审批流扩展

- [x] 4.1 新增 `POST /administered-items/{id}/transitions/reject`：具备该 Item 所属注册机构活动 Registrar 身份且在审核人集合内的用户可驳回 RECORDED→QUALIFIED 申请——置 `REJECTED`、作废该迁移全部未生效批准；注册状态不变。验证：TDD 单测——驳回后状态与批准记录；无资格用户 403。
- [x] 4.2 新增 `POST /administered-items/{id}/request-qualification`：主管记录联系人为本用户且注册状态 RECORDED、管理状态非 PROVISIONALLY_QUALIFIED 时可申请合格——置 `PROVISIONALLY_QUALIFIED` 并发起既有 RECORDED→QUALIFIED 审核流。验证：TDD 单测——置状态、审核流已发起、非主管 403。
- [x] 4.3 Submitter 白名单：`INCOMPLETE→CANDIDATE` 放行归属本用户（提交记录或主管记录联系人）的 Submitter 直推，前置 Candidate 最小需求（stewardshipRecord 非空）；仅限此迁移。验证：TDD 单测——归属提交者执行成功、非归属 403、缺主管记录 422、其他迁移不受影响。

## 5. 工作空间后端

- [x] 5.1 `WorkspaceController` + `WorkspaceService` + DTO：`GET /workspace/summary`（三视图计数）、`GET /workspace/items?view=DRAFTS|PENDING|REJECTED`（分页 + 行内操作标志 + `canRequestCandidate`），归属过滤（提交记录联系人或主管记录联系人 ∈ 当前账号绑定联系人；PENDING 限本机构活动 Registrar）。查询不遍历 previous_state 链、不触发联合子类整图 join。验证：TDD 单测覆盖三视图可见性/不可见性（对应 workspace spec 场景）。
- [x] 5.2 `POST /workspace/submit`：批量提交——全部须归属本用户且管理状态 DRAFT；显式 Submission_Record（联系人 MUST 为本账号绑定，组织缺省推导）；每条生成新 SubmissionRecord；管理状态按 2.1 落点；逐条返回结果。验证：TDD 单测——成功落点（Incomplete→INITIAL、Recorded→null）、部分失败逐条结果、代填他人联系人 422、非草稿项失败。
- [x] 5.3 `POST /workspace/request-candidate/{itemId}`：Incomplete + stewardshipRecord 非空 + 归属本用户；直推 CANDIDATE + 管理状态清空 + 生成 SubmissionRecord。验证：TDD 单测——成功路径、缺主管记录 422、非归属 403。
- [x] 5.4 `POST /workspace/rollback/{itemId}`：接入 3.3 回退服务，归属校验。验证：TDD 单测——成功回退、非归属 403、不可回退情形 422。
- [x] 5.5 `SecurityConfig` 放行新端点（SUBMITTER/REGISTRAR 相应规则）与 `ApiCodes` 新错误码（无版本可回退、非草稿不可提交等）。验证：web 模块编译 + 安全规则单测。

## 6. 前端（metadata-registry-ui）

- [x] 6.1 一级菜单"工作空间"：`AppLayout.vue`（概览之后）、`router/index.ts`（`/workspace` 懒加载 + NavigationRouteTable 注册）、`zh-CN/en-US` 词条、`e2e/menu.spec.ts` 断言（位置 + 导航）。验证：`pnpm vitest` 通过；menu.spec 逻辑同步。
- [x] 6.2 `resources.ts` 新增 `workspaceApi`（summary/items/submit/request-candidate/rollback）。验证：类型检查通过。
- [x] 6.3 `WorkspaceView.vue`：三 Tab（草稿/待审核/已拒绝）+ 计数徽标；表格复用 `DataTablePage` 多选；行操作按后端标志渲染（提交/请求候选/申请合格/批准/拒绝/修订/回退）。验证：新增 vitest 组件测试——标志渲染与 Tab 切换。
- [x] 6.4 `SubmissionRecordPicker.vue`：提交弹窗（联系人+组织选择，默认预填本账号默认身份，数据源 `/api/v1/my/contacts`），单个/批量提交与 Request Candidate 复用。验证：vitest 组件测试——预填、可改、校验提示。
- [x] 6.5 管理状态列标准文案词条（忠实还原标准原文，不引用条款号；区分标识版本状态与管理状态）。验证：i18n 词条完备、vitest 通过。

## 7. 端到端验证与交付

- [x] 7.1 后端全量测试 + openapi.json 再生成：web/item-mapping 等模块全量测试 0 failures。验证：`mvn test` + openapi 产物 diff 合理。
- [x] 7.2 前端构建 + 全量 vitest。验证：`pnpm build`、`pnpm vitest` 全绿。
- [x] 7.3 冒烟（scratch 副本库模式，自建 Admin 测试账户）：工作空间全流程——新建→草稿→批量提交（Incomplete→Initial）→编辑 Recorded→草稿→申请合格→Registrar 批准（清空）/拒绝（Rejected）→修订→回退（内容恢复）。验证：冒烟脚本全 PASS。
- [x] 7.4 `node .gitnexus/run.cjs detect-changes --scope all --repo .` 无 partial/truncated。
- [x] 7.5 文档同步：README 页面清单/默认账号说明、ARCHITECTURE.md（GitNexus 重生成或最小同步）、AGENTS.md 无需变更核对。验证：文档与实现一致。
