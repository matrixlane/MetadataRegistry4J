# 2026-09-30 初始化与联系人身份链路修正（Individual 收敛 / system 组织移除 / 身份卡片与删除）

用户驱动的一连串修正，根因都指向"同一个人裂成多行"与"系统内部对象泄漏到用户界面"。

## 修复清单

### 1. 初始化不再兜底创建 Individual（admin-individual 分裂源头之一）
- `InitWizardService.setup()`：删除 PERSONAL 版"姓名为空兜底'注册管理员'"逻辑——初始化只创建/复用 Role("注册管理员")，姓名留空则不创建 Individual。
- spec 同步：`add-registrar-three-elements` 两份 spec 消除"姓名默认注册管理员"与"清空不创建"的矛盾条款。

### 2. Individual 单一化：一个 Individual 同时挂 Account 与 Registrar
- `RegistrarService.createRegistrarForUser`：向导填姓名创建的 Individual 同时挂到 Account（之前只挂 Registrar，"我的"因此显示空）。
- `AuthService.updateMyIndividual`：Account 无 Individual 时优先**复用**已绑定 Contact/Registrar 上的 Individual（就地更新），无才新建——存量分裂库在下次保存"我的"时自动收敛。
- `InitWizardService.resetSuperAdminToNoPassword`：解除 admin Individual 关联，防 setup 重试/abort 后外键悬挂。
- spec 同步：`openspec/specs/user-profile-individual` "首次保存必须新建"改为"优先复用"。

### 3. system 组织移除，sys: 命名空间 NamingAuthority 改挂注册机构
- `SystemDefaultNamespaceInitializer`：不再创建系统组织（固定 UUID …0001）；新增启动调和——存在 RA 时自动挂接；存量库历史系统组织行在无引用时自动删除（含 organization_name/email/phone 子表）。
- 事故记录：首版清理漏 organization_name 子表 FK → 启动即崩（DataIntegrityViolationException），已修复为先删子表行。
- `InitWizardService` 恢复 3.6 步 initializeSystemDefaultNamespace 调用（WIP 曾删除，向导同靴内挂接依赖它）。
- `OrganizationService`：列表/下拉过滤系统组织 + 删除保护（存量库兜底）。
- spec 同步：`openspec/specs/system-initialization`。

### 4. SQL Server 查询处理器错误 8624 换表绕过
- `mdr.individual` 的 DELETE 在该实例（16.0.1200.5）持续报内部 QP 错误（禁用 FK/重编译/统计信息/TOP/ROWCOUNT 均无效）。改用备份→建新表→删旧表→重命名→补 PK→重建 4 个 FK 的换表法完成孤儿 Individual 清理。备份表：`mdr._bak_individual_20261005`、`mdr._bak_contact_20261005`（用户确认后可删）。

### 5. "我的"联系人身份卡片与删除
- 卡片去重：标题恒为身份种类（注册人员/联系人，醒目位置）；meta 行为 组织+角色（角色在组织右侧，可空、可隐藏）；RA 仅在与组织不同时显示；角色图标 Briefcase→Postcard。
- 删除：`ContactService.deleteMyContact` 允许删除 Registrar 行与最后一个身份（解绑绑定 + 清除各账号 activeRegistrarId + 删实体），效果=初始化未设为注册人员；引用保护（SubmissionRecord/StewardshipRecord/RegistrationTransitionApproval，外键非空）保留为 409 友好提示。
- 删除确认文案：`确认删除{org}的{kind}身份？`（i18n zh/en，新增 contactIdentity.contact 键）。
- README "我的"行从"Registrar 只读可见"改为可编辑可删除。

## 数据操作记录（真实库 metadata_registry@localhost）
- 删除孤儿 Individual `B012508F…FFCFC`（注册管理员），Registrar 改挂汪汇 `7B1ADE3A…405F4`。
- 更新 `ext.account.individual_id` 指向向导创建的 Individual（QUOTED_IDENTIFIER 索引需 sqlcmd -I）。
- 启动调和自动移除历史 system 组织行，`sys:` NamingAuthority=默认注册机构。

## 验证
- 相关单测/集成测试全绿（InitWizard/AuthService/RegistrarService/OrganizationService/SystemDefaultNamespaceInitializer/ContactService 等）。
- 全量打包 + 前端构建多次，后端按 start.bat 等效方式重启，8081/5173 正常。
- 已知既有 WIP 遗留失败（RegistrarServiceTest applyRequest_* mock 缺陷、TEAM 角色校验类测试）与本次无关，未处理。

## 待办（用户决策）
- 变更未提交（工作区含大量其他会话 WIP）；提交前按 AGENTS.md 跑 `detect_changes`。
- 备份表 `mdr._bak_individual_20261005` / `mdr._bak_contact_20261005` 确认后可 DROP。
