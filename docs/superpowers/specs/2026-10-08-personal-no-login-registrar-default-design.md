# 个人版免登录 + 注册人员默认标志重构 设计稿

日期：2026-10-08
状态：待用户确认
变更名（OpenSpec）：`personal-no-login-registrar-default`（待确认设计后正式确定）

## 1. 背景与目标

用户三点需求：

1. **去除个人版的用户登录功能**。个人版仅本地应用可访问，对用户进行验证已无意义；账号登录原本用于"识别用户"，此前提已不成立。
2. **`active_registrar_id` 转变为 `ext.registrar` 上的默认标志**。标识"某个用户-个人关联的一系列 registrar 中的默认的那一个"。
3. **当前激活 registrar 状态保存在浏览器端**，不持久化到数据库。

经两轮澄清，数据模型已收敛为：

- `contact_account_binding` 表整个移除，账号归属改为 `ext.contact.account_id`（contact → account 多对一）；
- `ext.contact` **只放 `account_id`**，不设默认标志；
- 默认身份唯一标志落在 **`ext.registrar.is_default`**（该用户一系列 registrar 中的默认者）。

## 2. 现状要点（探索结论）

### 2.1 认证

- 无状态 JWT：`JwtAuthenticationFilter` 解析 `Authorization: Bearer` → `AuthenticatedUser`（account id、username、sysRole、force/emergency/temp 标志）+ `ROLE_SUPER_ADMIN / ROLE_REGISTRAR / ROLE_SUBMITTER`。
- `SecurityConfig`：`/api/v1/**` 认证后访问；写操作需 `SUPER_ADMIN/REGISTRAR/SUBMITTER`；初始化向导与登录端点匿名白名单。
- 个人版与团队版共用同一套登录：向导建 admin（`password_hash=NO_PASSWORD`）→ 首登强制改密；另有应急码机制（`AuthService.initEmergencyCode`，`emergency-code.txt`）。
- `ForcePasswordResetInterceptor`：principal `forceChangePassword=true` 时除白名单端点外一律 498。

### 2.2 active registrar

- `ext.account.active_registrar_id` 只是**同步缓存**：真实语义已是 activation-follows-default——激活恒随 `ext.contact_account_binding.is_default` 对应的 Registrar（缺失/非 Registrar 时回退首个有效身份）。
- `ActiveRegistrarService.resolveActiveRegistrar` 解析时写回缓存；`selectActiveRegistrar`（PUT）同时写缓存 + `markDefault` 绑定。
- 前端：AppLayout 顶栏下拉 → `GET/PUT /api/v1/active-registrar`；`activeRegistrarApi` 定义在 `src/api/resources.ts`。

### 2.3 contact_account_binding

- 表结构：`id PK, contact_id UNIQUE, account_id, is_default`；语义 = 一个 Contact 至多绑一个账号、一个账号可绑多个 Contact。
- 实体 `ContactAccountBinding`（ext 模块）**有意不建 JPA 关联**，读写经值键（同 `RegistrationAuthorityExtension` 范式）。
- 消费方：`ContactAccountBindingService`（全部反查/绑定/默认）、`ContactService`（我的身份页、提交默认上下文、删除重默认）、`ActiveRegistrarService`、`AuthService.isSubmitter/isRegistrar`、`RegistrationAuthorityService`（首身份标默认）、`InitWizardService`、`RegistrarService.createRegistrarForUser`、登录后 `updateMyIndividual` 回填。

### 2.4 GitNexus 影响分析（2026-10-08，索引落后 18 提交）

- `ActiveRegistrarService` 直接消费方 6 个：`ActiveRegistrarController`、`AdministeredItemController`、`WorkspaceController`、`NamespacePermissionAspect`、`MergeRegistrarGate`、`ActiveRegistrarResponse`，risk=MEDIUM，epistemic=exact。
- `account.active_registrar_id` 读写点：`Account` 实体、`ActiveRegistrarService`（写回+select）、`ContactService`（删除清理）、`RegistrationAuthorityDeletionService`（JDBC 置空）、若干测试。

## 3. 方案选择

### 3.1 个人版免登录：方案 A（推荐，已定）

新增 **`PersonalEditionAuthenticationFilter`**，置于 `JwtAuthenticationFilter` 之前：

- 条件：`system.edition=PERSONAL` 且请求**无** `Authorization` 头（或 token 解析失败）→ 装载系统唯一 `SUPER_ADMIN` 账号，直接构建 `Authentication`（principal=`AuthenticatedUser`，authorities 按 `AuthService.isRegistrar/isSubmitter` 同款逻辑装配）。
- 有合法 Bearer token 时 JWT 过滤器优先（团队版路径与个人版残留 token 均不受影响）。
- 未初始化（无 admin 业务数据）时装配失败则保持匿名——初始化向导端点本就在白名单，其余端点由 `InitializationWizardInterceptor` 返回 499，行为不变。
- 个人版隐式 principal 的 `forceChangePassword` 恒为 `false`——密码概念在个人版不复存在，`ForcePasswordResetInterceptor` 自然不触发；存量个人库 `force_password_reset=1` 由迁移清零（见 §6）。

**对比**：方案 B（保留 JWT、启动期自动签长期令牌）——认证名存实亡却保留全部 token 机制；方案 C（permitAll + 匿名主体）——"我的"资料/头像/联系人身份/工作空间徽标等 per-account 功能需全面匿名化，爆炸半径最大。A 使这些功能**零改动**落在唯一管理员账号上，团队版完全不动。

### 3.2 数据模型（用户已确认）

```
ext.contact    (contact_id PK  → mdr.contact.id, account_id NOT NULL, INDEX(account_id))
ext.registrar  (registrar_id PK → mdr.registrar.id, is_default NOT NULL DEFAULT false)
ext.account    删除列 active_registrar_id
（contact_account_binding 表删除）
```

- 两个卫星实体均**有意不建 JPA 关联**，读写经值键，完全复刻 `RegistrationAuthorityExtension` 范式：
  - 实体 `ContactExtension`（`ext.basicandcore`）、`RegistrarExtension`（`ext.registration`），`@Table(name="contact") / "registrar"` 不写 schema；
  - `orm-team.xml` 登记 `<table schema="ext"/>`；`orm-sqlite.xml` 覆盖物理名 `ext_contact` / `ext_registrar`（SQLite 与 mdr 同名冲突加前缀）。
- 应用层不变式：同一账号绑定的 registrar 中**至多一个** `is_default=true`（设默认时同事务清其他）；无默认时激活回退首个有效身份。
- `ext.contact.account_id` 语义 = 原 binding 表（contact 唯一、account 多）；未绑定的 Contact（团队版他人身份）无卫星行。

### 3.3 激活状态浏览器化（推荐，待确认）

- 前端 localStorage 存 `activeRegistrarId`；`request.ts` 拦截器自动附加 `X-Active-Registrar-Id` 头。
- 后端解析链（`ActiveRegistrarService.resolveActiveRegistrar`，**纯读、不再写回**）：
  1. 请求头 registrar（校验属于当前账号且挂有 RA，非法/缺失则忽略）；
  2. `ext.registrar.is_default=true` 的 registrar；
  3. 首个有效身份（contact_id 升序，沿用现行回退）。
- `GET /api/v1/active-registrar` 响应改为 `{ identities, defaultRegistrarId }`（active 由前端计算）；**`PUT` 端点删除**——顶栏切换 = 纯客户端动作（写 localStorage + `mdregistry:active-registrar-changed` 事件）。
- 默认 registrar 设置入口：「我的」联系人身份页"设为默认"（`setDefaultMyContact` 扩展：仅 Registrar 身份可设，落 `ext.registrar.is_default`）；账号首个 registrar 创建时自动标默认（现行 `bindAndSave(first)` 同款语义）。

## 4. 后端设计

### 4.1 新增/改造清单

| 单元 | 动作 |
|---|---|
| `ContactExtension`（新，ext 模块） | contact_id PK + account_id |
| `RegistrarExtension`（新，ext 模块） | registrar_id PK + is_default |
| `ContactAccountBindingService` | 重构：以 `ContactExtension` 为存储，`bind/unbind/accountIdOfContact/contactsOfAccount/registrarsOfAccount/hasAnyContact` 语义不变；`defaultBindingOf/markDefault/bindAndSave/bindingsOfAccount/firstBindingOf` 删除或改由 RegistrarExtension 语义替代。类更名 `ContactAccountService`（经 GitNexus rename） |
| `ActiveRegistrarService` | `resolveActiveRegistrar` 改解析链（header→默认→首个），纯读；删除写回缓存与 `selectActiveRegistrar`；`listIdentities` 不变 |
| `ActiveRegistrarController` | GET 响应改 `{identities, defaultRegistrarId}`；删 PUT |
| `ContactService` | 见 4.2 |
| `RegistrarService` / `RegistrationAuthorityService` | 首身份自动默认改为写 `ext.registrar.is_default`；`markDefault` 调用点迁移 |
| `RegistrationAuthorityDeletionService` | JDBC 清理：`contact_account_binding` 两行删除改为 `ext.contact` 按 contact_id 删除 + 新增 `ext.registrar` 按 registrar_id 删除；`account.active_registrar_id` 置空段整段删除（列已不存在） |
| `AuthService` | `isSubmitter/isRegistrar` 改经新服务（语义不变）；`login`/应急码/用户管理保留（团队版继续用） |
| `InitWizardService` | 个人版：建 admin registrar 后写 `ext.contact` + 首默认 `ext.registrar`；commit 不再发 token（见 4.3） |
| `PersonalEditionAuthenticationFilter`（新） + `SecurityConfig` 注册 | 见 3.1 |
| `Account` 实体 | 删 `activeRegistrarId` 字段 |
| `AccountRepository.findByActiveRegistrarId` | 删除 |

### 4.2 ContactService 默认语义改造（单标志推导）

- `resolveDefaultSubmissionContext`：默认 registrar 的 contact + 其 RA 组织；无 registrar → 首个绑定 contact（contact_id 升序）+ 其组织；无绑定 → 空。
- `listMyContacts` / `updateMyContact` 的 `isDefault` 展示语义 = "该身份是默认 registrar"。
- `setDefaultMyContact`：仅接受 Registrar 身份（普通 Contact 返回 422 业务错误）；落 `ext.registrar.is_default`（同事务清同账号其他默认）。
- `deleteMyContact`：删除 Registrar 时同步删 `ext.registrar`/`ext.contact` 卫星行；被删者是默认 registrar 时，将账号剩余首个 registrar 标默认；普通 Contact 删除不再触发重默认。
- `createMyContact`：首个身份且为 REGISTRAR 时标默认（现行"首个身份自动默认"的等价迁移）。

### 4.3 初始化向导与个人版流程

- 个人版向导**去除设密步骤**（step 5）：commit 后不再调用 `establishSessionAfterInitialization`、不返回 token；admin 保持 `NO_PASSWORD` 占位（密码概念在个人版废弃）。
- 团队版向导不变（设密 + token + 首登强改密）。
- `InitWizardStatusResponse` 结构不变（前端据 `edition` 决定流程）。

### 4.4 个人版 UI 裁剪（前端）

- 路由守卫：`edition=PERSONAL && initialized` → 免登录态，`/login` 访问即跳 `/`。
- `request.ts`：个人版无 token 自然不带 `Authorization`（现状逻辑已满足，无需改）。
- 隐藏入口：用户管理、自助注册、修改密码、应急码提示（均按 `auth.edition==='PERSONAL'` 条件渲染）；AppLayout 用户标签保留（显示管理员姓名）。
- 「我的」身份页：普通 Contact 不再显示"设为默认"按钮；默认徽标仅在默认 registrar 上。

## 5. 前端 active registrar 设计

- 新 composable `useActiveRegistrar`：
  - `load()`：GET `/active-registrar` → `identities` + `defaultRegistrarId`；
  - `active` = localStorage 值（在 identities 中有效）?? `defaultRegistrarId` ?? 首个；
  - `switch(id)`：写 localStorage + 广播事件，**不发请求**。
- `request.ts` 请求拦截器附加 `X-Active-Registrar-Id: <localStorage 值>`（无则不加）。
- AppLayout 顶栏改用该 composable；`WorkspaceView`/`ItemRegistrationSection` 等 `active-registrar-changed` 事件监听方不变。

## 6. 迁移设计（三方言同号）

| 版本 | 内容 |
|---|---|
| `V20261009001` | 建 `ext.contact`（team：`ext.contact`；sqlite：`ext_contact`；dm：`EXT.CONTACT`）：PK contact_id、account_id NOT NULL、INDEX(account_id)；从 `contact_account_binding` 搬数据（contact_id, account_id）；**drop `contact_account_binding`** |
| `V20261009002` | 建 `ext.registrar`（同上命名规则）：PK registrar_id、is_default NOT NULL DEFAULT false；回填：① `account.active_registrar_id` 命中者标 true（多命中取账号维度最小 registrar，保持"至多一默认"）；② 其余账号取默认绑定对应的 registrar；③ 再缺取账号首个 registrar（contact_id 升序）。仅绑定到该账号的 registrar 参与 |
| `V20261009003` | `ext.account` 删列 `active_registrar_id`（SQLite 直接 `alter table account drop column`，先例 V20260930001） |
| `V20261009004` | 存量个人库清零密码标志：`UPDATE account SET force_password_reset=0, temp_password_used=0 WHERE EXISTS(SELECT 1 FROM system_setting WHERE id='system.edition' AND value='PERSONAL')`（团队库不动；NO_PASSWORD 哈希保留无妨） |

说明：`contact`/`registrar` 在 SQLite 与 mdr 表同名，卫星表必须 `ext_` 前缀（`orm-sqlite.xml` 登记）；SQL Server 语句全限定 `ext.` 并带 COL_LENGTH 守卫的项目惯例沿用既有迁移范式。

## 7. 兼容、回滚与风险

- **团队版行为**：除激活状态改由请求头传递、默认落 `ext.registrar` 外，登录/权限/向导流程全部不变。
- **API 兼容**：`PUT /api/v1/active-registrar` 删除为破坏性变更（前端同步改；openapi.json 重新生成）。`GET` 响应结构变化（`active` 字段 → `defaultRegistrarId`）。
- **个人版存量库**：迁移后无需任何登录即获得管理员能力——这是本设计的目的而非漏洞；前提是"个人版本地访问"部署前提成立。
- **回滚**：迁移按 Flyway 惯例不回滚；代码回滚 = revert 提交 + 手动恢复列/表（在 tasks 中给出回滚 SQL 清单）。
- **风险**：`ActiveRegistrarService` 6 个消费方 + 前端 4 处监听/引用；MyContacts/向导/删除机构链路测试需全量回归。`X-Active-Registrar-Id` 头跨浏览器标签页不共享（localStorage 按浏览器），不同浏览器各持激活态——符合"状态保存在浏览器端"的需求定义。

## 8. 验证计划

1. 后端：目标测试类（ActiveRegistrarServiceTest 重写、ContactAccountServiceTest 新、ContactServiceMyContactsTest、RegistrationAuthorityDeletionServiceIntegrationTest、InitWizard 集成、新增 PersonalEditionAuthenticationFilter 安全测试）+ web 全量测试。
2. 前端：vitest（AppLayout.spec、request.spec、新增 useActiveRegistrar.spec、MyProfile.spec）+ build。
3. 三方言迁移实测：SQLite scratch 全新库 + 真实库副本；SQL Server 真实库副本；dm 语法走审（沿用"待实测"惯例记录）。
4. 冒烟：个人版免登录全流程（启动→向导→直进应用→注册条目归属 RA→顶栏切换 registrar→X-Active-Registrar-Id 生效→刷新保持）；团队版登录回归。
5. `openapi.json` 重新生成；`openspec validate --all`；`detect_changes` 无 partial/truncated。

## 9. 待确认项

1. §3.3 激活浏览器化语义（切换=纯客户端、PUT 删除、默认经"我的"身份页设置）。
2. §4.4 个人版 UI 裁剪范围（用户管理/注册/密码入口隐藏；账号体系保留作隐式身份）。
3. §3.1 方案 A（隐式管理员自动认证）。
