# Proposal: personal-no-login-registrar-default

## Why

个人版（PERSONAL）仅本地应用可访问，"账号登录以识别用户"的前提已不成立，登录/密码/应急码对个人版是纯负担。同时现行 `account.active_registrar_id` 只是一个同步缓存（真实语义为 activation-follows-default），冗余且易悬挂；"当前激活哪个注册机构"是会话级偏好，持久化到数据库使跨浏览器切换互相干扰。应用户要求收敛数据模型：`contact_account_binding` 表由 `ext.contact` 卫星表（account_id）取代，默认身份唯一标志落在 `ext.registrar.is_default`。

## What Changes

- **个人版免登录（方案 A：隐式管理员自动认证）**：新增过滤器——`PERSONAL` 已初始化且请求无有效 Bearer 令牌时，自动装载唯一 SUPER_ADMIN 构建认证主体（角色按现行 isRegistrar/isSubmitter 逻辑装配）。前端跳过登录页/token 流程；所有"我的"功能落在管理员账号上零改动。**BREAKING（行为）**：个人版任何本地访问者获得管理员能力（这正是"本地应用可访问"部署前提的语义化）；存量个人库迁移清零 `force_password_reset`/`temp_password_used`。
- **初始化向导个人版去设密**：`PERSONAL` 版向导去除密码设置步骤，`commit` 无密码参数、不写密码哈希、不签发令牌；`TEAM` 版不变。
- **`contact_account_binding` 表移除**：新建 `ext.contact` 卫星表（`contact_id` PK、`account_id NOT NULL`、INDEX），整表搬迁后删除旧表；实体 `ContactAccountBinding` 及 Repository 删除，服务以 `ContactExtension` 为存储重构。
- **默认身份标志落位 `ext.registrar.is_default`**（新卫星表，1:1 按 registrar id）：应用层保证同账号至多一个默认；账号首个 registrar 创建时自动标默认；设置入口为"我的"身份页（仅 Registrar 身份可设，普通 Contact 返回业务校验错误）。**BREAKING**："默认联系人"概念消失，原默认绑定为普通 Contact 的账号迁移后激活回退首个 registrar。
- **`account.active_registrar_id` 删除**：`ActiveRegistrarService` 解析改纯读链：`X-Active-Registrar-Id` 请求头（校验归属当前账号且挂有 RA，非法忽略）→ `ext.registrar.is_default` → 首个有效身份；不再写回缓存。
- **激活状态浏览器化**：`PUT /api/v1/active-registrar` 删除；`GET` 响应改为 `{ identities, defaultRegistrarId }`（active 由前端计算）；前端 localStorage 存选择并自动附加请求头，顶栏切换=纯客户端动作。**BREAKING（API）**：PUT 端点删除、GET 响应结构变化；openapi.json 重新生成。
- **默认上下文推导改造**：提交默认上下文 = 默认 registrar 的 contact + 其 RA 组织；无 registrar 回退首个绑定 contact；删除默认 registrar 自动递补剩余首个。
- **前端**：新 `useActiveRegistrar` composable；AppLayout 顶栏/工作空间提交预填/我的身份页（普通 Contact 不再显示"设为默认"）适配；个人版隐藏登录/注册/用户管理/改密入口。
- **迁移（三方言同号 V20261009001~V20261009004）**：建 `ext.contact` 搬数据删旧表 → 建 `ext.registrar` 三级回填（active_registrar_id → 旧默认绑定 registrar → 账号首个 registrar）→ 删 `account.active_registrar_id` 列 → 个人库密码标志清零。

## Capabilities

### New Capabilities

（无。个人版免登录与激活浏览器化分别落入既有 `deployment-edition` 与 `active-registrar-ownership` 能力。）

### Modified Capabilities

- `active-registrar-ownership`：激活语义重写（浏览器端状态 + 请求头传递 + 默认标志回退）；默认注册人员标志需求新增；身份解析/切换/展示相关需求改写；PUT 端点删除。
- `contact-identity-management`：默认身份概念从"默认联系人"收窄为"默认注册人员"（ext.registrar.is_default）；设置默认仅接受 Registrar；删除递补规则改造；账号绑定存储改为 ext.contact 卫星表。
- `deployment-edition`：新增"个人版免登录访问"需求；个人版功能约束补充登录/用户管理入口不存在语义。
- `system-initialization`：提交语义按版本差异化（个人版无密码步骤、不签发令牌）；个人版自动成为 Registrar 补充"首个 registrar 自动默认"语义。
- `admin-emergency-login`：登录类机制（首登免密改密/应急码/临时密码）明确仅团队版适用，个人版无登录流程故不适用。
- `user-self-registration`：注册入口仅团队版提供，个人版 MUST NOT 存在登录页与注册入口。
- `workspace`：批量提交"联系人与组织默认预填"推导改为默认注册人员（回退首个绑定联系人）。

## Impact

- **代码**：`metadata-registry-web` 主类约 14 个（新增 3：PersonalEditionAuthenticationFilter、ContactExtension、RegistrarExtension；删除 2：ContactAccountBinding/Repository；重构 6+：ActiveRegistrarService、ContactService、ContactAccountBindingService（更名 ContactAccountService）、RegistrationAuthorityDeletionService、InitWizardService、ActiveRegistrarController；Account 实体删字段）；`metadata-registry-ui` 约 8 个文件（auth store、request.ts、useActiveRegistrar 新、AppLayout、WorkspaceView、ItemRegistrationSection、MyProfile/身份页、InitWizard、Login/router）。
- **数据**：三方言（SQL Server / SQLite / 达梦）各 4 个 Flyway 迁移（V20261009001~V20261009004）；存量账号的 `active_registrar_id`、默认绑定语义由迁移回填至 `ext.registrar.is_default`。
- **API**：`PUT /api/v1/active-registrar` 删除；`GET /api/v1/active-registrar` 响应结构变更；其余端点契约不变。
- **依赖**：无新增依赖。
- **兼容**：团队版除激活传递机制与默认标志存储外行为不变；个人版升级后即免登录（无需用户操作）。
