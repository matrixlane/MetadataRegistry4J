# 工作空间与管理状态（Administrative Status）设计

日期：2026-09-23
状态：待用户确认
关联变更：（待定，OpenSpec change 名建议 `add-workspace-administrative-status`）

## 1. 背景与目标

新增一级菜单"工作空间"，提交者（Submitter）与注册人员（Registrar）在其中集中处理"需要关注"的元数据对象：

- 被本人作为提交人新增或编辑过的管理项；
- 注册人员需要集中审核的管理项（Provisionally Qualified 待批准/拒绝、待审迁移等）。

核心机制：为 `Registration_State.administrative_status`（11179-3 表 22，0..1 String）建立应用层枚举 `AdministrativeStatus`——**持久化仍为 String，内存中为枚举**。当用户新建或更改 Item，管理状态为 Draft；工作空间支持批量提交（提交后 Draft 清除：Incomplete 落 `Initial`，其余注册状态清空为 null）。

## 2. 标准依据（Milvus 原文核实）

- **ISO/IEC 11179-3:2023 表 22**：`administrative_status 0..1 String`，"注册机构对本次注册的管理项在行政管理流程中的状态的指称"；"该属性的取值及其含义由每个注册机构自行决定"。→ String 持久化 + RA 定义枚举取值完全符合标准。
- **ISO/IEC 11179-6:2023 4.3.1**：管理状态是 RA 处理注册请求行政过程中的状态；允许值很可能依赖当前注册状态；由 RA 细化、公布、实施。
- **4.4 h)**：RA 可指派管理状态以跟踪管理项的临时状态（interim state）。
- **4.3.3.1.3**：Candidate 最低文档 = Incomplete 最低文档 + 主管组织名、主管联系人名、主管联系人信息；Candidate 起管理项处于版本控制之下。
- **附录 C**（提交者职责）："An administered item may be recorded as 'Incomplete' or 'Candidate' registration status, **as the submitter deems appropriate**"；图 C.1 即 Registration functional activities。→ 提交人点击"请求成为候选"即成 Candidate 的依据。
- **附录 D.1.2 拒绝准则**：缺强制信息/信息虚假/未响应澄清/与注册要求不一致/技术评审未通过；正式拒绝前有补救期。→ Rejected 与"修订→新 Draft"循环的依据。
- **附录 D.2.2/D.2.3/D.2.4**：Incomplete/Candidate/Recorded 的推进步骤；Recorded→Qualified 由主管确认质量后提出、Registrar 复审。
- **附录 D.3 Table D.1**（非规范示例）：每个注册状态类别有"稳定值"（Initial / Approved as X）与"推进值"（Provisionally Y）两类管理状态。
- **附录 F.5**：管理状态应反映"向下一个注册状态的推进"，当前注册状态永远可信。→ Draft/Rejected 不改变注册状态；注册状态变更后管理状态清空（无待办行政流程）。

## 3. 用户已确认的决策

| # | 决策 | 结论 |
|---|---|---|
| 1 | 枚举取值 | 收录 Table D.1 词表（16 个常量，见 §4.1，剔除 "Approved as Candidate"）；Incomplete 创建保存后 = Draft，提交后 = Initial，之后照 Table D.1 推进。每次提交须提示手动设置 Submission_Record（可改提交组织） |
| 3 | 历史版本存储 | 全量 DTO 存既有 `version_history.snapshot_json`，无新表无迁移（见 §4.6） |
| 4 | Request Candidate 后管理状态 | **清空（null），不再是 Draft**。用户裁定：Candidate 前无审批（附录 C 提交人自决），不使用 "Approved as Candidate"（Table D.1 为非规范示例，4.3.1 允许 RA 自定取值，剔除合规） |

沿用已推荐、待确认时一并否决即可的决策：2（后端聚合接口）、5（提交仅校验归属）、6（主管 = `StewardshipRecord.contact`）。

**清空约定**（由决策 4 推衍的全局落点规则）：提交、Registrar 批准、回退、Request Candidate 之后，管理状态一律**清空（null）**；唯一例外是 Incomplete 的提交落 `Initial`（决策 1 明确）。"清空"表示该管理项当前无进行中的行政流程。

## 4. 总体设计

### 4.1 AdministrativeStatus 枚举与持久化映射

新增（`metadata-registry-common-facilities` registration 包）：

- `AdministrativeStatus.java`——16 个常量，落库值为标准原文小写串：

| 常量 | 落库值 | 来源 |
|---|---|---|
| `DRAFT` | `draft` | 附录 F.5（RA 扩充值） |
| `INITIAL` | `initial` | Table D.1 Incomplete |
| `PROVISIONALLY_CANDIDATE` | `provisionally candidate` | Table D.1 |
| `PROVISIONALLY_RECORDED` | `provisionally recorded` | Table D.1 |
| `APPROVED_AS_RECORDED` | `approved as recorded` | Table D.1 |
| `PROVISIONALLY_QUALIFIED` | `provisionally qualified` | Table D.1 / 4.3.4 |
| `APPROVED_AS_QUALIFIED` | `approved as qualified` | Table D.1 |
| `PROVISIONALLY_STANDARD` | `provisionally standard` | Table D.1 / 4.3.4 |
| `APPROVED_AS_STANDARD` | `approved as standard` | Table D.1 |
| `PROVISIONALLY_PREFERRED_STANDARD` | `provisionally preferred standard` | Table D.1 / 4.3.4 |
| `APPROVED_AS_PREFERRED_STANDARD` | `approved as preferred standard` | Table D.1 |
| `PROVISIONALLY_SUPERSEDED` | `provisionally superseded` | Table D.1 |
| `APPROVED_AS_SUPERSEDED` | `approved as superseded` | Table D.1 |
| `PROVISIONALLY_RETIRED` | `provisionally retired` | Table D.1 / D.2.9 |
| `WITHDRAWN` | `withdrawn` | Table D.1 |
| `REJECTED` | `rejected` | D.1.2（RA 扩充值） |

> 已按用户决策剔除 `APPROVED_AS_CANDIDATE`（"approved as candidate"）：Candidate 准入为"被提议推进"（Table 1），准入前无审批行为，故不使用该取值。其余 `APPROVED_AS_*` 常量保留为注册机构公布词表（本迭代操作不落这些值，落点按 §3 清空约定为 null）。

- `AdministrativeStatusConverter.java`——`@Converter(autoApply = true)`，仿 `MappingDegreeConverter`：写库转标准小写串，读库大小写不敏感、空白容忍、未知值抛错（响亮失败）。
- `RegistrationState.administrativeStatus` 类型 `String` → `AdministrativeStatus`。DB 列保持 `varchar(255)` 可空无 CHECK——**零迁移**。

### 4.2 管理状态推进规则

| 事件 | 管理状态变化 | 注册状态变化 |
|---|---|---|
| 新建保存 | → `DRAFT` | 不变（Incomplete，既有 1125 初始状态规则） |
| 编辑保存（任意注册状态；含 Rejected 修订） | → `DRAFT` | 不变 |
| 工作空间提交（单个/批量，带 Submission_Record 选择） | `DRAFT` → 清空（null）；例外：Incomplete → `INITIAL` | 不变 |
| Request Candidate（满足 Candidate 最小需求 + 归属人本用户） | **清空（null），不再是 Draft** | Incomplete → Candidate（直推，附录 C） |
| 申请合格（Recorded + 主管=本用户） | → `PROVISIONALLY_QUALIFIED` | 不变（走既有 RECORDED→QUALIFIED 审核点） |
| Registrar 批准（quorum 达成） | → 清空（null）（"管理状态则清除"） | Recorded → Qualified |
| Registrar 拒绝 | → `REJECTED` | 不变（D.1.2 拒绝+补救期） |
| 回退（Recorded+ 且为 Draft/Rejected，见 §4.6） | → 清空（null） | 不变 |
| 其余注册状态迁移（既有迁移集） | → 清空（null） | 按 `RegistrationLifecycleService` |

实现要点：管理状态变更只改写当前 `Registration_State` 行的 `administrative_status`（0..1 属性就地更新，不新建状态行、不动 `previous_state` 链）；注册状态变更仍走 `writeTransition` 新行+链接。所有管理状态写入集中在新的 `AdministrativeStatusService`（校验合法性 + 落库），供各服务调用。

### 4.3 工作空间后端

新增 `WorkspaceController`（`/api/v1/workspace/**`）：

| 端点 | 说明 |
|---|---|
| `GET /workspace/summary` | 三个视图计数（草稿数/待审核数/已拒绝数） |
| `GET /workspace/items?view=DRAFTS\|PENDING\|REJECTED&page&size` | 分页列表，行内携带：注册状态、管理状态、是否有回退基线、可用操作 |
| `POST /workspace/submit` `{itemIds[], submission:{contactId, organizationId}}` | 批量提交：校验全部为本用户归属且管理状态=Draft；按所选 Submission_Record 记录提交（既有 `recordSubmission` 校验本用户联系人绑定）；管理状态清空（Incomplete → `INITIAL`） |
| `POST /workspace/request-candidate/{itemId}` | Candidate 最小需求校验（Incomplete 最低集 + `stewardshipRecord` 非空，对应 4.3.3.1.3）+ 归属校验（提交记录联系人或主管联系人为本用户）；直推 Candidate + 管理状态清空（附录 C） |
| `POST /workspace/rollback/{itemId}` | 回退到上一提交版本（§4.6） |
| `POST /administered-items/{id}/request-qualification` | 主管申请合格：置 `PROVISIONALLY_QUALIFIED`，发起既有 RECORDED→QUALIFIED 审核流 |
| `POST /administered-items/{id}/transitions/reject` | Registrar 拒绝：置 `REJECTED`，作废该迁移的未生效审批 |

归属规则（各操作共用）：当前账号绑定联系人 ∈ {Item 提交记录联系人（最近一条 SubmissionRecord.contact），Item 主管记录联系人（`stewardshipRecord.contact`）}。Registrar 审核操作另要求活动 Registrar 身份且属于该 Item 所属注册机构（既有 `RegistrationProcessService` 批准资格校验）。

视图定义：
- DRAFTS：管理状态 = Draft 且归属本用户；
- PENDING（Registrar）：本机构下管理状态 = Provisionally Qualified 的 Item + 既有待批准迁移（复用 `RegistrationTransitionApproval` 查询）；
- REJECTED：管理状态 = Rejected 且归属本用户（修订/回退入口）。

### 4.4 提交与 Submission_Record 交互（用户决策 1）

- 前端提交弹窗：Submission_Record 选择器（联系人 + 组织），默认账号默认联系人/组织，可改为本账号绑定的任意联系人及其组织（沿用 `ContactService.resolveDefaultSubmissionContext` 与 `recordSubmission` 的归属校验，拒绝代填 422）。
- 每次提交（单个/批量）均弹出该选择器并生成新的 `SubmissionRecord`（11179-6 4.4 a) Submission of items）。
- Request Candidate 同属提交行为，同一弹窗复用。

### 4.5 Request Candidate 按钮可见性（示例 1）

**审核依据（用户问答确认）**：附录 C 明确"as the submitter deems appropriate"——Incomplete→Candidate 无需任何人批准，点击即生效符合标准；附录 D.2.2 Step 3/4 的主管/注册人员评审为协同评审（review/determine，非审批门），其意图已由 Candidate 最低需求（stewardshipRecord 齐备）+ 归属规则承载。D.2.3 主管评审属 Candidate→Recorded 阶段，亦非审批门。后续可选增强：Request Candidate 后在 PENDING 视图向主管/注册人员生成待办提醒（本迭代不实现）。

行内按钮显示条件（与管理状态无关）：
1. 注册状态 = Incomplete；
2. Candidate 最小需求满足：`stewardshipRecord` 非空（组织+联系人，即 4.3.3.1.3 的 stewardship 三要素载体）；
3. 归属本用户（提交记录联系人或主管记录联系人 = 本用户）。

后端 `GET /workspace/items` 行内返回 `canRequestCandidate` 标志；后端 `request-candidate` 端点重复校验（不信任前端）。当前 `ROLE_PERMISSIONS` 中 INCOMPLETE→CANDIDATE 仅 ADMIN/REGISTRAR，需为归属本用户的 Submitter 放行该迁移（在 `RegistrationProcessService.execute` 的归属路径上加 Submitter 白名单，仅允许此一条迁移）。

### 4.6 版本模型：全量快照、回退与列表性能（用户决策 3）

**存储**：既有 `version_history` 表，`snapshot_json` 扩展为全量 DTO：

```json
{
  "meta": { "itemType": "DataElement", "registrationStatus": "RECORDED",
            "administrativeStatus": null, "kind": "SUBMITTED|BASELINE|DRAFT_EDIT|ROLLED_BACK" },
  "item": { /* 完整 Item Response DTO（无环，直接序列化） */ }
}
```

- **零 schema 变更**：`snapshot_json` 列与 `(item_id, version_number)` 唯一键已存在。
- 快照类别 `kind`：
  - `BASELINE`：Item 从非 Draft 进入 Draft 的编辑前一刻的完整状态（**回退目标**）；
  - `DRAFT_EDIT`：草稿期编辑（现状快照的完整化，供版本历史 UI）；
  - `SUBMITTED`：提交/创建/批准后的已提交状态；
  - `ROLLED_BACK`：回退操作生成。
- **回退执行**：取该 Item 最近一条 `BASELINE`（无则 422 无可回退版本）；经 Item Service 内部 restore 路径写回（复用字段应用逻辑但绕过"编辑→Draft"副作用，直接落库）；写 `ROLLED_BACK` 快照；管理状态清空（null）。仅注册状态 ∈ {Candidate, Recorded, Qualified, Standard, PreferredStandard, Superseded, Retired} 且管理状态 ∈ {Draft, Rejected} 时可回退（与 11179-6 4.3.3.1.3 / 4.3.3.1.4 字面对齐：Candidate 起即受版本控制，故回退窗口按标准字面取 Candidate 起；详见 `openspec/specs/version-control` 的"回退窗口"需求）。
- **Rejected 特例**：无内容变更的拒绝（未改内容即申请合格）回退 = 仅清管理状态（清空）（无 BASELINE 时降级为此行为，语义同"丢弃被拒绝版本"）。

**列表仅显示当前版本 + 性能**：
- 实体表单行即"当前版本"（既有架构：编辑就地更新 + 快照留痕），工作空间与各列表页直查实体表，**天然只显示当前版本**，无需自关联去重；
- 工作空间查询只 join 当前 `registration_state`（`administered_item.registration_state_id` 直联，不遍历 `previous_state` 链）；
- 新增索引（三方言迁移 V20260924001）：`registration_state(administrative_status)`、`version_history(item_id, version_number)` 唯一键已覆盖点查、`administered_item(stewardship_record_id)`（如无）。

### 4.7 前端

- 新一级菜单"工作空间"（`AppLayout.vue` 概览之后、`src/router/index.ts` 注册 `/workspace`、`zh-CN/en-US` 词条、`e2e/menu.spec.ts` 断言）；`standardize-menu-layout` 主 spec delta。
- `src/views/workspace/WorkspaceView.vue`：三 Tab（草稿/待审核/已拒绝）+ 计数徽标；表格复用 `DataTablePage` 选择能力；批量提交弹窗（`SubmissionRecordPicker` 组件，复用"我的"联系人身份数据 `/api/v1/my/contacts`）；行操作按视图与后端标志渲染（提交/请求候选/申请合格/批准/拒绝/修订/回退）。
- 管理状态列展示标准原文文案（AGENTS.md 规则：忠实还原标准文字，不引用条款号；词条入 i18n）。

### 4.8 改造点清单（文件级）

后端：
- common-facilities：`AdministrativeStatus`、`AdministrativeStatusConverter`、`RegistrationState.administrativeStatus` 换型；
- web：`AdministrativeStatusService`（新）、`WorkspaceController/Service/DTO`（新）、`RegistrationLifecycleService`（迁移后管理状态清空、Submitter 放行 INCOMPLETE→CANDIDATE）、`RegistrationProcessService`（approve 清空、reject 动作）、`ItemAdministrationService`（编辑→Draft 钩子、BASELINE 捕获、提交服务、归属查询）、`VersionHistoryService`（全量 DTO 快照 + kind）、17 个 Item Service 的 `recordSnapshot` 调用点（全量快照接管）、`AdministeredItemController`（reject/request-qualification）、`SecurityConfig`（新端点放行 SUBMITTER/REGISTRAR）、`ApiCodes`（新错误码）、索引迁移（sqlite/sqlserver/dm V20260924001）；
- 测试：上述服务 TDD + workspace 冒烟（scratch 副本库模式）。

前端：`AppLayout.vue`、`router/index.ts`、`i18n×2`、`resources.ts`（`workspaceApi`）、`WorkspaceView.vue` + `SubmissionRecordPicker.vue`、`e2e/menu.spec.ts`、`DataTablePage` 操作列扩展（如有必要）。

文档/规范：`openspec/specs/standardize-menu-layout` delta、README 页面清单、ARCHITECTURE.md（GitNexus 重生成）。

## 5. 边界与异常

- 批量提交部分失败：逐条结果返回（成功/失败+原因），成功项已生效（前端刷新后重查）。
- 并发：同一 Item 被他人提交/迁移后，提交与回退按当前库内状态复核（乐观校验，无锁）。
- 既有 `ScopedIdentifierLifecycle.DRAFT/PUBLISHED`（标识版本）与本次 `AdministrativeStatus.DRAFT`（管理状态）是两个正交概念，命名相同但互不影响；UI 文案注意区分（标识版本状态 vs 管理状态）。
- SQLite 64 表 join 上限：工作空间查询避免 Item 联合子类整图 join，沿用 type() 探测 + getReference 代理的既有手法。
- 管理状态未知存量值：Converter 读入时未知值抛错（列现状为空，无存量值风险）。

## 6. 待确认事项

1. 决策 2/5/6 若无异议按推荐执行（后端聚合接口 / 提交仅校验归属 / 主管 = `StewardshipRecord.contact`）。
2. Provisionally Candidate / Provisionally Recorded / Provisionally Standard 等其余 Table D.1 推进值的操作（如"主管申请成为候选"）本迭代不实现，枚举仅收录取值——范围是否认可。
