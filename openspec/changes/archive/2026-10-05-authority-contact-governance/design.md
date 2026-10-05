# Design: authority-contact-governance

详细设计决策（含现状代码定位与 ISO/IEC 11179-3:2023 原文依据）见 `docs/superpowers/specs/2026-10-05-authority-contact-governance-design.md`，本文件为实施导向的架构摘要。

## Context

- 部署版本 `DeploymentEdition`（PERSONAL/TEAM）持久化于 `system_setting.system.edition`；运行模式 SINGLE/MULTI 独立于版本。
- 机构创建/删除的既有守卫全部集中在 `RegistrationAuthorityService.create/delete`（按 SINGLE 模式限建 1 个/禁删，无引用检查）。
- `RegistrySpecification` 与 `RegistrationAuthority` 无外键；初始化 `setup` 当前顺序为 RA → Registrar → Namespace → Spec（`InitWizardService.setup` :237-336）；commit 后由 `RegistryContextBootstrapService` 取第一个 RA 建 Registry Context 并回填 `spec.context`（该机制不变）。
- 提交链路：`WorkspaceService.validateSubmissionInfo` 与 `ItemAdministrationService.resolveContactAndOrganization` 两处重复实现"联系人必须属于当前账号"校验；前端 `SubmissionRecordPicker.vue` 组织为只读（自动取联系人所属组织）。
- 身份删除守卫仅存在于 `ContactService.deleteMyContact`（SubmissionRecord/Stewardship/审批引用 → 409）。
- 平台约束：SQLite 单连接池禁嵌套事务（既有 500 根因）；基线表普遍无外键，级联必须应用层实现。

## Goals / Non-Goals

- Goals：版本化机构生命周期治理（Primary 标志、删除保护、级联删除+预览）、初始化顺序调整（规范先行）、提交组织可选+联系人属主校验、身份增删的版本/角色差异化。
- Non-Goals：管理端 `/api/v1/contacts`、`/api/v1/registrars` 无守卫删除端口的收口（既有缺口，本次不动）；runningMode 语义重设计；多注册规范行为；团队版机构"编辑"流程变化。

## Decisions

### D1 Primary 标志 = `registration_authority.is_primary` 布尔列

三方言迁移 `V20261005001__Registration_authority_primary_flag`（NOT NULL DEFAULT false）；仅初始化向导 `setup` 置 true；不进入任何 Response DTO。备选：system_setting 存 UUID（悬挂键）、派生"第一个 RA"（删除后语义漂移）——均放弃。历史已否决 default-context 的 ext 卫星表方案；本次直接落标准表新增列（与 `dimensionality.coordinate_indicator` 先例一致）。**存量库回填**：启动调和（`RegistryContextBootstrapService.backfillIfInitialized` 邻近的幂等入口）在无 Primary 行时把最早创建的 RA 补标 Primary——保证既有个人版库获得同等保护。

### D2 创建/删除守卫改为版本化判定

- `create`：PERSONAL → 允许（成功后为当前 SUPER_ADMIN 自动建 Registrar：复用 `RegistrarService.createRegistrarForUser`，individualName=null 复用账号既有 Individual、roleName 缺省"注册管理员"，绑定并确保默认身份）；TEAM → 维持 runningMode 规则（SINGLE 限 1）。
- `delete`：PERSONAL 且 is_primary → 拒绝；其余放行并级联。Controller `create/delete` 注入 `@AuthenticationUser`。
- SINGLE/MULTI 不再约束个人版。

### D3 级联删除服务 `RegistrationAuthorityDeletionService`

单事务内按序清理：AdministeredItem 及其 Item 图（designation/definition/naming/identification/registered_item/registration_state，遵循既有"先清 `submission_record_registered_item` join"的方言感知 JdbcTemplate 约定）→ 仅关联被删 item 的 SubmissionRecord → StewardshipRecord（org=RA）→ Registrar（解绑 ContactAccountBinding、清 `active_registrar_id`）→ registrar/contact/organization JOINED 行 → RA 自有 Namespace 及其从属（scoped_identifier、标识策略卫星配置）→ `sys:` 命名空间 naming_authority_id 置空保留 → registration_transition_rule(+_approver) → doc_language join 行 → RA 行。新增 `GET /{id}/deletion-preview` 返回各项计数。前端删除按钮改 `ElMessageBox.confirm` 展示计数与"不可恢复"警告。

### D4 初始化顺序

`InitWizardService.setup`：Spec → RA → Registrar → Namespace → sys 挂接（仅移动代码块；Registry Context 回填保持 commit 后独立事务）。`InitWizard.vue` 步骤：版本 → 数据库 → 注册规范 → 注册机构 → 命名空间 → 密码。`system-initialization` 主规范相应修订步骤描述。

### D5 提交组织/联系人

- 后端两处重复校验同步改：`organizationId` 给出时从当前账号绑定身份中解析该组织下的身份作为 contact，解析不到 → 422（提示先创建为该机构联系人）；contactId+organizationId 同给时增加从属校验；仅 contactId 路径保持兼容。
- 前端 `SubmissionRecordPicker.vue`：组织下拉（选项=本人身份所属组织去重，默认=活动 RA 身份组织/默认身份组织），联系人只读展示；打开时无任何身份 → 提示弹窗引导"我的 → 联系人身份"。`WorkspaceView` 三个提交入口统一走该预检。

### D6 身份增删版本/角色分支

- `createMyContact`：kind=REGISTRAR 且账号非 SUPER_ADMIN → 422（两版本一致）。前端非 Admin 隐藏"注册人员"单选。
- `deleteMyContact`：PERSONAL 且 Registrar 行 → 409（机构仍存在）；TEAM → 移除引用守卫全量可删（保留解绑/默认递补）。前端 MyProfile 按版本控制删除入口。

### D7 事务与方言

- 级联删除单事务；严禁 REQUIRES_NEW 嵌套（SQLite 单连接池死锁先例）。
- 三方言迁移脚本同版本号；SQLite 用 `ALTER TABLE ... ADD COLUMN`（加列免重建）。

## Risks / Trade-offs

- [级联删除涉及面广，易漏表] → 以"指向 RA 的全部列"枚举清单实现 + 集成测试（建 RA→造各类关联数据→删→全表断言空）；冒烟脚本覆盖。
- [团队版放开身份删除产生 SR.contact 悬挂] → 历史提交记录联系人列展示为空，需求明确优先删除自由，可接受。
- [个人版存量库无 Primary 标志] → 启动调和回填（D1），避免要求用户重新初始化。
- [既有"单注册表初始化"相关测试/文案大量依赖 SINGLE 禁删语义] → 相关测试随语义改写，逐条核对。

## Migration Plan

1. 三方言迁移 V20261005001 加列（既有行默认 false）。
2. 启动调和回填存量库 Primary（无 Primary 行时补标最早 RA）。
3. 部署回滚：迁移不可回滚（加列无损）；如功能回退仅需还原代码（列保留无害）。
