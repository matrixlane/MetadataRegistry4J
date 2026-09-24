# Session Journal — add-submitter-contact-identity（2026-09-22）

## 需求
更正"只有 Registrar 才能提交元数据"的前提：Submitter = 任何已登记为 Contact 的注册用户。五条需求：首登提示完善联系人（可跳过）、Admin 成为 Registrar 则不提示、多联系人身份+默认身份（提交时预填 SubmissionRecord 可改）、"我的"入口管理身份、无 Contact 无 Registrar 用户只读。

## 方案（方案 A，已确认）
- Submitter 能力 = JWT `submitter` claim（账号绑定 ≥1 Contact）→ `ROLE_SUBMITTER`；SecurityConfig 写操作放行 SUPER_ADMIN/REGISTRAR/SUBMITTER；`/api/v1/my/**` 任何登录用户（服务层校验归属）。
- 归属推导集中在 `resolveOwnershipForCreate`：活动 Registrar → 其 RA（不变）；否则 Submitter → 系统唯一 RA（单注册表隐式推导，永不为空）。
- 修改/删除通道：`ActiveRegistrarService.assertActiveIdentityOf` + `NamespacePermissionEvaluator.hasWritePermission`（注册机构自有命名空间 + 系统唯一 RA + 已登记 Contact 三者齐备放行）。生命周期状态治理不放开（ROLE_SUBMITTER → AppUserRole.VIEWER）。
- SubmissionRecord 补链：`ItemAdministrationService.recordSubmission(item, req)`（create/update 后调用，已传播 17 个 Item Service）；DTO `submission:{contactId,organizationId}` 可选，缺省预填默认联系人+其组织，contactId 必须本人绑定。
- 默认联系人：`contact_account_binding.is_default`（应用层唯一性）；SQLite 迁移是 **Java 迁移**（`db.migration.sqlite.V20260922001__ContactAccountBindingDefaultFlag`）——因为 FlywayAfterJpaConfig 使 Hibernate ddl-auto 先于 Flyway，既有库列可能已存在，纯 SQL 无法表达条件 DDL；SQL Server/dm 仍用带守卫的 SQL。

## 教训（重要）
- **本项目新迁移必须幂等**：ddl-auto=update 先于 Flyway 执行，ADD COLUMN 类迁移在既有库必然撞 duplicate column。SQLite 侧用 Java 迁移 + pragma_table_info 守卫。
- `-pl metadata-registry-web` 单独构建会用本地仓库旧 ext jar——改 ext/common-facilities 后必须 `-am` 或先 `install`。
- 网关 403 "无权访问此资源" 也覆盖下游 Aspect 抛的 AccessDeniedException——排查时先解码 JWT claims 区分网关拒绝与下游拒绝。
- SQLite 64 表 join 上限使已归属条目 update/delete 必 500（已录入 KNOWN_FAILURES），冒烟只能覆盖创建路径。

## 验证
- web 全量 1233 tests 0 failures；vitest 227/227；前端 build 通过；openapi.json 重生成（含 /my/contacts）。
- 冒烟 `scratch/submitter-smoke/smoke.sh`（副本库+专用测试账号，16/16 PASS）：profilePrompt 标志、无身份读 200/写 403、联系人 CRUD+默认递补、Submitter 建条目归属系统唯一 RA、SubmissionRecord 生成、代填他人联系人 422。
- detect_changes 无 partial/truncated（risk=critical 系工作区历史未提交变更所致）。
- 遗留：SQL Server/达梦迁移未实测（带幂等守卫，随实例重启自动应用）；条目修订路径受 SQLite join 上限阻断（单测覆盖）。

## 待办
待 `continue-review` → `continue-archive`。

## 评审与修复（2026-09-22 晚，双评审代理）
- 后端评审：2 Blocker——①recordSubmission 在种子/系统路径（无认证）抛错会阻断启动 → 改为 accountId==null 直接跳过；②普通用户可经 MyContactRequest.roleId 自选「注册管理员」角色自提 Registrar → createMyContact/updateMyContact 拒绝该角色。另有 Major：MyContactRequest.organizationId 加 @NotNull；SecurityConfig 显式收紧 `/api/v1/administered-items/*/transitions/**` 仅 SUPER_ADMIN/REGISTRAR（HTTP 层）；Minor：createMyContact 回填账号 Individual、删联系人被 SubmissionRecord 引用时 409（existsByContactId）、toMyResponse 组织名 NPE 守卫。旧 token 无 submitter claim 需重新登录（已告知用户）。
- 前端评审：MyProfile 联系人模块硬编码中文+双 toast → 全量 i18n 化、错误提示交给拦截器；ItemFormDialog `（默认）`硬编码、submission 静默丢失修复、冗余非空断言清理；AppLayout v-model→:model-value + localStorage try/catch；spec describe 嵌套修复 + 补 2 个缺口测试。
- 修复后验证：web 全量 1237 tests 0 failures、vitest 229/229、build、lint 0 error、冒烟 16/16。

## 后续修正（2026-09-23）
- Registrar 身份判定从「roleName == 注册管理员」魔法字符串改为**类型判定**（绑定的 Contact 中是否存在 Registrar 子类实例），涉及 AuthService.isRegistrar / RegistrationLifecycleService.resolveCurrentRole / NamespacePermissionEvaluator.isRegistrar|isAnyRegistrarOrAdmin；Role 恢复为纯描述性元数据，「我的」自建联系人不再拒绝选该角色（无任何权限效应）。依据：ISO/IEC 11179-3 Registrar 是 Contact 子类，身份由类型确定；active-registrar-ownership 主 spec 本就如此描述。
- bind() 首个身份自动默认扩展到全部创建路径（Registrar 指派/初始化向导/批量配置）；新增迁移 V20260923001 补标单身份存量账号（真实 SQL Server 库已应用并核实）。
- 处置 8081 真实库 Flyway 校验和不匹配（3 条未入库迁移文件被改动过）：flyway repair（mdr,ext schema + classpath 迁移定位）对齐。
- 验证：web 全量 1240 tests 0 failures；8081 重启 0 ERROR。

## Registrar Role 置空（2026-09-23，用户裁定）
- createRegistrarForUser 不再自动盖「注册管理员」Role（移除 RoleRepository 依赖）：身份由 Registrar 子类实例确定，Role 纯描述性，由用户后续经「角色」页自建、经「我的」自关联。
- updateMyContact 对 Registrar 行放开**仅 Role 关联**的编辑（组织等其余字段不变；删除仍 409）；前端 Registrar 行显示编辑入口、编辑时组织选择器禁用；spec delta 同步（contact-identity-management 需求1、user-profile-individual 场景）。
- 验证：web 全量 1240 tests 0 failures、vitest 230/230、build、8081 重启 0 ERROR。

## 初始化向导版本差异化（2026-09-23）
- 个人版：向导不再展示"Admin 是否成为 Registrar"开关（原开关对两版都显示），改为"自动"标识；edition watcher 切到 PERSONAL 时强制 form.selfAsRegistrar=true（后端本就强制）。团队版仍提供开关。spec 新增 system-initialization delta「个人版自动成为 Registrar」（2 场景）。
- 顺手修复 MyProfile.spec 类型断言（findComponent CSS 选择器返回 WrapperLike 无 props 类型，改 findAllComponents(ElSelect)）。
- 验证：vitest 230/230、build、8081 重启 0 ERROR。
