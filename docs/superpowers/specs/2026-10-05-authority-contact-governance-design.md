# 设计：注册机构治理与提交联系人约束（个人版/团队版差异化）

- 日期：2026-10-05
- 工作流：superpowers-openspec-superpowers-workflow
- OpenSpec 变更名：`authority-contact-governance`

## 1. 背景与目标

本次需求围绕"注册机构（Registration_Authority）— 注册人员/联系人身份（Registrar/Contact）— 提交记录（Submission_Record）"的治理规则，按部署版本（个人版 PERSONAL / 团队版 TEAM）实施差异化行为，并调整初始化流程顺序与提交前置校验。

需求拆解（R1–R5）：

| 编号 | 需求 |
| --- | --- |
| R1 | 个人版：初始化创建的注册机构为"主要（Primary）"机构（后台隐式标志，不显示前端），不可删除；Admin 作为该机构注册人员的联系人身份不可删除。 |
| R2 | 个人版：Admin 可新建注册机构（区别于 Primary）；新建时 Admin 自动成为其注册人员；该注册人员身份不可删除，除非该机构被删除。 |
| R3 | 团队版：任何用户可删除其全部联系人身份；非 Admin 用户不能为自己新增注册人员身份；Admin 可增删改机构、可删除所有机构（含 Primary——团队版下 Primary 标志暂不发挥作用）；删除机构时级联删除其下全部 Administered_Item，并弹出警告提示。 |
| R4 | 注册规范（Registry_Specification）独立于注册机构、作用于系统全局；初始化流程先创建注册规范，再创建注册机构与注册人员；向导步骤同步调整。 |
| R5 | 提交元数据时：提交组织可选；提交联系人恒为提交用户本人（其在所选机构的联系人身份），且必须是当前所选机构的联系人，否则无法提交；用户不是当前所选机构联系人时，点击提交入口弹提示，要求先创建为该机构联系人。 |

## 2. 现状摘要（探索结论）

- 版本：`DeploymentEdition`（PERSONAL/TEAM）持久化于 `system_setting.system.edition`；个人版强制 SINGLE 治理策略、`RegistrationAuthorityService.create` 限建 1 个、`:78-86 delete` 仅按 SINGLE 禁删，无任何 Primary 概念（全库无 is_primary）。
- 注册机构：`RegistrationAuthority extends Organization`（JOINED），无标志位字段；删除无引用检查，删后遗留大量悬挂（administered_item.registration_authority_id、registrar、transition rule、namespace 等）。
- 联系人/身份：`Contact`（organization/individual/role 三要素）；`Registrar extends Contact` + `registration_authority_id`；`ContactAccountBinding`（contact 唯一、account 多）；`Account.activeRegistrarId`。
- 身份删除：`ContactService.deleteMyContact`（:294-332）有引用守卫（SubmissionRecord→409、Stewardship/审批→409）并做解绑/默认递补；管理端 `ContactService.delete`/`RegistrarService.delete` 无守卫且产生悬挂（既有缺口，本次不动）。
- 初始化向导：步骤 = 版本→数据库→注册机构→命名空间→注册规范→密码；`InitWizardService.setup`（:237-336）顺序 = RA→Registrar→Namespace→Spec→sys 挂接；commit 后 `RegistryContextBootstrapService` 取第一个 RA 建 Registry Context 并回填 spec.context。
- 注册规范：`RegistrySpecification extends Item`（11179-3:2023 §9.4.10，Table 28），与 RA 无外键；消费方全部 `findFirst()`（默认上下文/默认语言），事实全局。
- 提交：`WorkspaceService.submit/requestCandidate` → `ItemAdministrationService.resolveContactAndOrganization`（:607-637）+ `WorkspaceService.validateSubmissionInfo`（:405-420）；现有校验仅"联系人必须属于当前账号"，组织仅查存在性；前端 `SubmissionRecordPicker.vue` 组织为只读（自动取联系人所属组织），联系人为下拉。
- "当前选定的注册机构" = 活动 Registrar 身份（`AppLayout.vue` 顶栏下拉，`PUT /api/v1/active-registrar`）。

## 3. ISO/IEC 11179 依据（本地 PDF 逐字核对）

- **Registry_Specification**（ISO/IEC 11179-3:2023 §9.4.10.1）：*"Registry_Specification describes the environment in which a registry operates… In an environment with multiple registries, there should be one Registry_Specification per registry."* —— 规范描述的是**注册系统（registry）本身**的运行环境，与注册机构无关；本应用实例 = 一个 registry，故规范全局唯一生效。§9.4.10.2："No associations are specified in this metamodel region."
- **Submission_Record**（§9.4.9.1，Table 27）：`organization`（1）="information about the organization that is responsible for the submission"；`contact`（1）="information about the contact that is responsible for the submission… the contact for issues related to the submission"。NOTE 1 允许 contact 不直接受雇于提交组织（应用层规则可比标准更严格）。
- **Registration_Authority**（§9.4.6.2）："an organization responsible for maintaining a register"；标准**不存在** "primary registration authority" 概念，亦无注册机构删除生命周期规定——Primary 标志属应用内部扩展，与标准不冲突。
- 标准对 Administered_Item 删除的级联约束仅 §9.4.3.5（Attached_Item 随 Administered_Item 删除）——机构级联删除同样从属该约束。

## 4. 方案对比与决策

### D1：Primary 标志的存储（R1/R2/R3）

| 方案 | 说明 | 取舍 |
| --- | --- | --- |
| **A（推荐）** | `registration_authority` 新增 `is_primary` BOOLEAN NOT NULL DEFAULT false（三方言迁移 V20261005001）；仅初始化向导置 true；不进 DTO/前端 | 显式、可测试、随实体生命周期；删除保护逻辑直白 |
| B | `system_setting` 存主机构 UUID | 多一个悬挂键维护点；与版本/模式设置混杂 |
| C | 派生（"第一个 RA"） | 团队版删除首个机构后语义漂移；不可靠 |

**决策：A**。历史决策提示：用户曾否决 default-context 的 ext 卫星表 + is_default 方案；本次直接落在标准表的新增列上（表 24 无该属性，属应用扩展列，与近期 `dimensionality.coordinate_indicator` 先例一致）。

### D2：机构创建/删除的治理规则（R1/R2/R3）

现状规则（SINGLE 限建 1 个、禁删）被个人版多机构需求取代：

- **create**：`PERSONAL` → 允许任意数量（创建成功后为当前 SUPER_ADMIN 自动建立 Registrar 身份，见 D6）；`TEAM` → 沿用 runningMode 规则（SINGLE 限 1 个，MULTI 不限）。
- **delete**：`PERSONAL` 且 `is_primary=true` → 拒绝（业务校验错误）；其余（个人版非 Primary、团队版全部含 Primary）→ 允许并执行级联删除（D3）。
- runningMode（SINGLE/MULTI）语义不变，但不再约束个人版的机构数量/删除。

### D3：删除机构的级联范围（R3）

新增 `RegistrationAuthorityDeletionService`（单事务；注意 SQLite 单连接池禁嵌套事务的既有平台约束）：

1. 该 RA 下全部 `AdministeredItem`：逐项删除其 Item 图（`item` 及从属 designation/definition/naming、`identification`(scoped_identifier)、`registered_item`、`administered_item`、registration_state），遵循既有"删有提交记录的 item 前先清 join 表"的方言感知约定（`DatabaseEnvironment.qualify` + JdbcTemplate）。
2. `submission_record_registered_item` join 行 + 仅关联被删 item 的 `SubmissionRecord`。
3. `StewardshipRecord`（organization_id = RA）。
4. `Registrar`（registration_authority_id = RA）→ 解绑 `ContactAccountBinding`、清理各账号 `active_registrar_id` → 删 registrar/contact/organization（JOINED）行。
5. RA 自有 `Namespace`（registration_authority_id = RA）及其从属（scoped_identifier、namespace 标识策略卫星配置、designation 等）；`sys:` 系统命名空间的 `naming_authority_id` 指向该 RA 时置空（系统命名空间保留）。
6. `registration_transition_rule`（含 `_approver` 子表，RA 创建即播种）。
7. `registration_authority_doc_language` join 行。
8. 最后删 `registration_authority` 行与 `organization` 行。

新增端点 `GET /api/v1/registration-authorities/{id}/deletion-preview`（返回各项计数：受管项数、注册人员数、命名空间数、提交记录数等）供前端删除警告框展示；`DELETE /{id}` 执行级联。前端 `RegistrationAuthorityList.vue` 删除前弹 `ElMessageBox.confirm` 警告（显示计数与"不可恢复"提示）。

### D4：初始化顺序——注册规范先行（R4）

- 后端 `InitWizardService.setup`：顺序调整为 **Spec → RA → Registrar → Namespace → sys 挂接**（spec 创建本不引用 RA，仅移动代码块；post-commit 的 Registry Context 回填机制保持不变——Context 归属命名空间是 11179-3 的模型要求）。
- 前端 `InitWizard.vue`：步骤顺序调整为 版本→数据库→**注册规范**→**注册机构**→命名空间→密码；步骤序号、`displaySteps`、载荷组装与 e2e 同步。
- 语义明确化：注册规范设置页/向导不新增"机构"维度；消费方继续 `findFirst()` 全局语义（既有实现已如此，本变更仅锁定创建顺序与文档表述）。

### D5：提交组织可选 + 提交联系人=本人（R5）

- 前端 `SubmissionRecordPicker.vue`：联系人选型改为**组织下拉**（选项 = 当前账号全部联系人身份所属组织去重）；选中组织后提交联系人自动解析为"本人在该组织的身份"（界面只读展示联系人姓名）。默认选中 = 活动 Registrar 所属 RA（本人在该机构有身份时），否则 = 默认身份的组织。
- 前置提示：打开提交入口（单条提交/批量提交/请求候选）时若当前账号在**当前所选机构**无任何联系人身份 → 不打开表单，弹提示"在提交元数据前，请先创建为该注册机构的联系人"并给出"我的 → 联系人身份"入口指引。组织下拉中已天然只列出有身份的组织；所选机构下无身份由后端兜底校验。
- 后端（`WorkspaceService.validateSubmissionInfo` + `ItemAdministrationService.resolveContactAndOrganization` 两处重复逻辑同步改）：
  - `organizationId` 显式给出时：从当前账号绑定身份中解析 `organization.id = organizationId` 的身份作为 contact；解析不到 → 422（"提交组织必须是当前账号具有联系人身份的组织，请先创建为该机构联系人"）。
  - `organizationId` 缺省：沿用既有默认推导（默认身份 + 其组织）。
  - `contactId` 与 `organizationId` 同时给出时：增加从属校验（contact.organization 必须等于所选组织），替换现有"仅查存在性"。
- 兼容：旧客户端仅传 `contactId` 的路径行为不变。

### D6：联系人身份新增/删除的版本差异化（R1/R2/R3）

- **新增**（`ContactService.createMyContact` + `MyProfile.vue`）：`kind=REGISTRAR` 仅允许 SUPER_ADMIN（两版本一致，覆盖"团队版非 Admin 不能自建注册人员身份"）；前端非 Admin 隐藏"注册人员"单选项。
- **删除**（`ContactService.deleteMyContact` + `MyProfile.vue`）：
  - `PERSONAL`：Registrar 行 → 拒绝（"该注册人员身份所属注册机构仍存在，不可删除；如确需删除请先删除该注册机构"）；Contact 行沿用既有引用守卫。
  - `TEAM`：移除 SubmissionRecord/Stewardship/审批引用守卫，任何身份均可删除（保留解绑、`active_registrar_id` 清理、默认身份递补）。行为后果：历史提交记录的联系人字段悬空，列表展示为空（可接受，需求优先删除自由）。
- 管理端 `ContactService.delete` / `RegistrarService.delete`（无守卫、产生悬挂）为既有缺口，本次按"只修报告的"原则不动，列入遗留。

### D7：个人版 Admin 新建机构自动成为注册人员（R2）

- `RegistrationAuthorityService.create`：PERSONAL 版本成功创建 RA 后，复用 `RegistrarService.createRegistrarForUser`（individualName=null 复用账号既有 Individual、roleName 缺省"注册管理员"、organizationId=ra.id）为当前账号建立 Registrar 并绑定；确保绑定为账号默认身份（首个身份时）。
- Controller `create` 增加 `@AuthenticationPrincipal` 传入 accountId。
- 前端 `RegistrationAuthorityList.vue`：个人版恢复新增入口（现状隐藏）；对话框加提示文案"创建后您将自动成为该机构的注册人员"。

## 5. 影响面

- **后端**：`RegistrationAuthority`（+列）、`RegistrationAuthorityService`、`RegistrationAuthorityController`、新增 `RegistrationAuthorityDeletionService`、`ContactService`（create/delete 版本分支）、`WorkspaceService`、`ItemAdministrationService`、`InitWizardService`、`SystemSettingService`（无改动，读 edition）、迁移（sqlite/sqlserver/dm 三分支 V20261005001）。
- **前端**：`RegistrationAuthorityList.vue`、`SubmissionRecordPicker.vue`、`WorkspaceView.vue`（入口预检）、`MyProfile.vue`、`InitWizard.vue`、`types/common.ts`、i18n（zh-CN/en-US）、e2e。
- **主 specs**（归档时合并）：`deployment-edition`（个人版约束修订）、`system-initialization`（创建顺序 + Primary 标志）、`registry-specification`（全局性/顺序）、`contact-identity-management`（身份增删规则）、`submission-record`（提交组织/联系人校验）；新增 capability `registration-authority-lifecycle`（创建/删除保护/级联）。
- **文档**：`README.md`/`ARCHITECTURE.md` 视最终影响同步；`openapi.json` 再生成。

## 6. 验收标准

1. 个人版：Primary 机构不可删（后端 409/422 + 前端隐藏/禁用删除入口）；Admin 新建机构后自动成为其注册人员且该身份不可删；新建机构可删（级联+警告）。
2. 团队版：非 Admin 自建注册人员身份被后端拒绝、前端隐藏；Admin 可删任一机构（含 Primary，级联+警告）；任一用户可删全部自身身份。
3. 初始化：spec 先于 RA/Registrar 创建（向导步骤与后端顺序一致）；既有库（已初始化）不受影响。
4. 提交：组织下拉仅列出本人有身份的组织；无身份机构上提交被后端 422 拦截；入口预检提示出现。
5. 全量后端测试 + vitest 绿、三方言迁移实测、冒烟脚本 PASS、`openspec validate --all` 通过、`detect_changes` 无 partial/truncated。

## 7. 非目标（边界外）

- 管理端 Contact/Registrar 删除端口的守卫收口（遗留）。
- runningMode（SINGLE/MULTI）语义的重新设计（本次仅解除其对个人版的约束）。
- 多注册规范行为定义（维持 findFirst 现状）。
- 达梦方言真实库实测（沿用既有"待实测"惯例，脚本照写）。
