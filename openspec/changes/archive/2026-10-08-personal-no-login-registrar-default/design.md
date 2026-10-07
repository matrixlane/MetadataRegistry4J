# Design: personal-no-login-registrar-default

详细设计已先行评审定稿于 `docs/superpowers/specs/2026-10-08-personal-no-login-registrar-default-design.md`（含探索结论、方案对比、影响面与验证计划）。本文档为 OpenSpec 实现向摘要。

## 1. 个人版免登录（方案 A：隐式管理员自动认证）

- 新增 `PersonalEditionAuthenticationFilter`，置于 `JwtAuthenticationFilter` 之前：
  - 命中条件：`SystemSettingService.getEdition()==PERSONAL` 且请求无 `Authorization` 头（或 token 解析失败导致无认证主体）。
  - 行为：装载系统唯一 `SUPER_ADMIN` 账号，构建 `UsernamePasswordAuthenticationToken`（principal=`AuthenticatedUser`，authorities 按 `AuthService.isRegistrar/isSubmitter` 同款逻辑：SUPER_ADMIN 恒 REGISTRAR，绑定 Registrar 行 → REGISTRAR，绑定任一 Contact → SUBMITTER）。
  - 隐式 principal 的 `forceChangePassword` 恒为 `false`（个人版无密码概念，`ForcePasswordResetInterceptor` 自然不触发）。
  - 未初始化（找不到 admin 或库为空）时保持匿名：向导端点本就在白名单，其余由 `InitializationWizardInterceptor` 返回 499。
- 有合法 Bearer 令牌时 JWT 过滤器优先（团队版与残留 token 不受影响）。
- 前端：`edition==='PERSONAL' && initialized` 时路由守卫视为已登录，`/login` 重定向到 `/`；`request.ts` 无 token 自然不带 Authorization（现状逻辑已满足）。

## 2. 数据模型

```
ext.contact    (contact_id PK → mdr.contact.id, account_id NOT NULL, INDEX(account_id))
ext.registrar  (registrar_id PK → mdr.registrar.id, is_default NOT NULL DEFAULT false)
ext.account    删除列 active_registrar_id
删除表 contact_account_binding
```

- 两个卫星实体（`ContactExtension`、`RegistrarExtension`）复刻 `RegistrationAuthorityExtension` 范式：ext 模块、`@Table` 不写 schema、不建 JPA 关联、读写经值键。
- orm 登记：`orm-team.xml` 加 `<table schema="ext"/>`；`orm-sqlite.xml` 覆盖物理名 `ext_contact` / `ext_registrar`（SQLite 与 mdr 同名冲突加前缀）。
- 不变式：同账号绑定的 registrar 至多一个 `is_default=true`（设默认时同事务清除其他）；无默认时激活回退首个有效身份。

## 3. 激活状态浏览器化

- 前端 localStorage `activeRegistrarId`；`request.ts` 请求拦截器附加 `X-Active-Registrar-Id` 头（无值不加）。
- `ActiveRegistrarService.resolveActiveRegistrar` 纯读解析链：
  1. 请求头 registrar（属于当前账号且挂有 RA，否则忽略）；
  2. `ext.registrar.is_default=true`；
  3. 首个有效身份（contact_id 升序）。
- `GET /api/v1/active-registrar` → `{ identities, defaultRegistrarId }`；`PUT` 端点删除。
- 前端 `useActiveRegistrar` composable：`active = localStorage 值（须在 identities 中）?? defaultRegistrarId ?? 首个`；`switch(id)` 仅写 localStorage + 广播 `mdregistry:active-registrar-changed`。

## 4. 服务改造要点

- `ContactAccountBindingService` → 更名 `ContactAccountService`，以 `ContactExtension` 为存储：`accountIdOfContact/contactsOfAccount/registrarsOfAccount/hasAnyContact/bind/unbind` 语义不变（经 GitNexus rename 保调用方）。
- 新增 RegistrarExtension 读写（可并入 `ContactAccountService` 或独立 `RegistrarExtensionService`）：`defaultRegistrarOf(accountId)`、`markDefaultRegistrar(accountId, registrarId)`（同事务清同账号其他默认）、`clearDefaultIfAny(registrarId)`。
- `ContactService`：
  - `resolveDefaultSubmissionContext` = 默认 registrar 的 contact + 其 RA 组织 → 首个绑定 contact + 其组织 → 空。
  - `setDefaultMyContact` 仅接受 Registrar（普通 Contact 422）；`deleteMyContact` 删 Registrar 时同步删两卫星行，被删者为默认时递补剩余首个 registrar；普通 Contact 删除不再触发重默认。
  - `createMyContact`：首个身份且为 REGISTRAR 时标默认。
- `RegistrationAuthorityService.create` / `RegistrarService.createRegistrarForUser` / `InitWizardService`：首个身份自动默认改写 `ext.registrar.is_default`。
- `RegistrationAuthorityDeletionService`：JDBC 清理 `contact_account_binding` 两段改为按 contact_id 删 `ext.contact` + 按 registrar_id 删 `ext.registrar`；`account.active_registrar_id` 置空段整段删除。
- `AuthService.isSubmitter/isRegistrar` 改经新服务（语义不变）。
- `InitWizardService.commit`：PERSONAL 分支不写密码、不调 `establishSessionAfterInitialization`、响应不含 token；admin 保持 `NO_PASSWORD` 占位。
- `Account` 实体删 `activeRegistrarId`；`AccountRepository.findByActiveRegistrarId` 删除。

## 5. 迁移（三方言同号）

| 版本 | 内容 |
|---|---|
| V20261009001 | 建 `ext.contact`（PK contact_id、account_id NOT NULL、INDEX(account_id)）；从 `contact_account_binding` 搬数据；drop `contact_account_binding` |
| V20261009002 | 建 `ext.registrar`（PK registrar_id、is_default NOT NULL DEFAULT false）；三级回填：① `account.active_registrar_id` 命中者；② 默认绑定对应的 registrar；③ 账号首个 registrar（contact_id 升序）；每账号至多一默认 |
| V20261009003 | `ext.account` 删列 `active_registrar_id`（SQLite `alter table account drop column`，先例 V20260930001） |
| V20261009004 | 存量个人库清零：`UPDATE account SET force_password_reset=0, temp_password_used=0 WHERE EXISTS(SELECT 1 FROM system_setting WHERE id='system.edition' AND value='PERSONAL')`（团队库不动） |

方言差异：SQL Server 全限定 `ext.` 并沿用既有迁移的守卫范式；SQLite 裸名（卫星表 `ext_` 前缀）；达梦全限定 `EXT.`。

## 6. 个人版 UI 裁剪

- 隐藏入口（按 `auth.edition==='PERSONAL'`）：用户管理、自助注册、修改密码、应急码提示；登录页路由已初始化时直跳 `/`。
- 「我的」身份页：普通 Contact 不显示"设为默认"；默认徽标=默认 registrar。
- AppLayout 用户标签保留（显示管理员姓名）；初始化向导个人版去掉密码步骤。

## 7. 风险与回滚

- 个人版升级后本地访问者即获管理员能力——本设计目的，前提是"个人版本地访问"部署假设。
- API 破坏面：`PUT /api/v1/active-registrar` 删除、GET 响应结构变化（前端同步改；openapi.json 重生成）。
- 回滚：代码 revert + 手工恢复 SQL（列/表重建 + ext 两表数据回搬，tasks 附清单）。
