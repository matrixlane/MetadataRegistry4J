# Tasks: personal-no-login-registrar-default

## 1. 数据层：卫星实体与三方言迁移

- [x] 1.1 新增 `ContactExtension`（`ext.basicandcore`，contact_id PK + account_id）与 `RegistrarExtension`（`ext.registration`，registrar_id PK + is_default）实体；`orm-team.xml` 登记 `<table schema="ext"/>`、`orm-sqlite.xml` 覆盖物理名 `ext_contact`/`ext_registrar`。验证：`mvn -q compile` 通过，两个 orm XML 语法有效。
- [x] 1.2 三方言迁移 `V20261009001`：建 `ext.contact`（PK、account_id NOT NULL、INDEX(account_id)），从 `contact_account_binding` 整表搬数据，drop 旧表。验证：SQLite scratch 全新库与真实库副本、SQL Server 副本迁移成功；搬数据行数 = 旧表行数。
- [x] 1.3 三方言迁移 `V20261009002`：建 `ext.registrar`（PK、is_default NOT NULL DEFAULT false），三级回填（account.active_registrar_id → 旧默认绑定对应 registrar → 账号首个 registrar，每账号至多一默认）。验证：造含三态fixture的 SQLite 副本执行后断言每账号 ≤1 默认且优先级正确。
- [x] 1.4 三方言迁移 `V20261009003`：删 `account.active_registrar_id` 列（SQLite 用 `alter table account drop column`，先例 V20260930001）。验证：三方言副本迁移后列不存在（sqlite `PRAGMA table_info`、sqlserver COL_LENGTH 为 NULL）。
- [x] 1.5 三方言迁移 `V20261009004`：`PERSONAL` 库清零 `force_password_reset`/`temp_password_used`（WHERE EXISTS system.edition='PERSONAL'），团队库不动。验证：双版本 fixture 副本各执行一次，断言个人库清零、团队库保持原值。

## 2. 后端服务层重构

- [x] 2.1 `Account` 实体删除 `activeRegistrarId` 字段；`AccountRepository.findByActiveRegistrarId` 删除。验证：编译通过，全库无残留引用（Grep 确认）。
- [x] 2.2 `ContactAccountBindingService` 经 GitNexus rename 更名 `ContactAccountService`，存储改 `ContactExtension`：`accountIdOfContact/contactsOfAccount/registrarsOfAccount/hasAnyContact/bind/unbind` 语义保持不变；删除 `defaultBindingOf/bindAndSave/bindingsOfAccount/firstBindingOf/markDefault`（由 2.3 承接）。验证：现有 `ContactAccountBindingServiceDefaultTest` 改造后全绿。
- [x] 2.3 新增 RegistrarExtension 读写：`defaultRegistrarOf(accountId)`、`markDefaultRegistrar(accountId, registrarId)`（同事务清同账号其他默认）、`clearDefaultIfAny(registrarId)`、`ensureDefaultForFirstRegistrar(accountId, registrarId)`。验证：新增单测覆盖设默认清他默认、空账号无默认、幂等。
- [x] 2.4 `ActiveRegistrarService.resolveActiveRegistrar` 重写为纯读解析链：请求头（`RequestContextHolder` 或方法入参透传，属于实现细节）→ `ext.registrar.is_default` → 首个有效身份；删除写回缓存与 `selectActiveRegistrar`（连同 2.5 的 PUT 端点）。验证：`ActiveRegistrarServiceTest` 重写后全绿（含头非法忽略、默认优先、无默认回退首个、纯读不写库）。
- [x] 2.5 `ActiveRegistrarController`：GET 响应改 `ActiveRegistrarResponse(identities, defaultRegistrarId)`；删除 PUT 端点及 `ActiveRegistrarSelectRequest`。验证：控制器测试绿；`openapi.json` 第 5 步重生成后无 PUT。
- [x] 2.6 `ContactService` 默认语义改造：`resolveDefaultSubmissionContext` = 默认 registrar 的 contact + 其 RA 组织 → 首个绑定 contact + 其组织 → 空；`setDefaultMyContact` 仅接受 Registrar（普通 Contact 422）；`deleteMyContact` 删 Registrar 时删两卫星行并递补默认、删普通 Contact 不重默认；`createMyContact` 首个 REGISTRAR 自动默认；列表/编辑的 isDefault 改"是否默认注册人员"。验证：`ContactServiceMyContactsTest` 全量改造后绿 + 新增补递/拒绝用例。
- [x] 2.7 `RegistrationAuthorityService.create` 与 `RegistrarService.createRegistrarForUser` 的"首身份自动默认"从 `markDefault` 改写 `ext.registrar.is_default`（经 2.3）。验证：`RegistrationAuthorityServiceTest` 相应用例改造后绿。
- [x] 2.8 `RegistrationAuthorityDeletionService`：JDBC 清理 `contact_account_binding` 两段改为按 contact_id 删 `ext.contact`、按 registrar_id 删 `ext.registrar`（方言感知 qualify）；删除 `account.active_registrar_id` 置空段。验证：`RegistrationAuthorityDeletionServiceIntegrationTest` 绿（含卫星行清理断言）。
- [x] 2.9 `AuthService.isSubmitter/isRegistrar` 与 `updateMyIndividual` 改经 `ContactAccountService`（语义不变）。验证：`AuthServiceTest` 绿。
- [x] 2.10 `InitWizardService`：PERSONAL 分支 commit 无密码参数、不写密码哈希、不签发令牌（响应区分版本）；setup 建 admin registrar 时写 `ext.contact` + 首个默认 `ext.registrar`；`resetSuperAdminToNoPassword` 语义对个人版保持占位。验证：`InitWizardServiceIntegrationTest` 与 OrderTest 改造后绿。

## 3. 个人版免登录

- [x] 3.1 新增 `PersonalEditionAuthenticationFilter`（置于 JWT 过滤器之前）：edition=PERSONAL 且无有效认证时装载唯一 SUPER_ADMIN 构建 Authentication（角色按 isRegistrar/isSubmitter 逻辑装配，forceChangePassword 恒 false）；`SecurityConfig` 注册。验证：新增安全集成测试（个人版无 token 访问业务接口 200 且 principal=admin；团队版无 token 仍 401）。
- [x] 3.2 `ForcePasswordResetInterceptor` 对个人版隐式会话不触发（principal force 标志恒 false 已保证）；补充存量个人库迁移后无 498 的回归断言（随 1.5）。验证：3.1 的安全测试覆盖 498 不出现。
- [x] 3.3 个人版未初始化时不自动认证（匿名 → 向导白名单 → 其余 499）。验证：安全测试覆盖未初始化场景。

## 4. 前端改造

- [x] 4.1 新增 `useActiveRegistrar` composable：load（GET 新响应）、active 计算（localStorage ?? defaultRegistrarId ?? 首个）、switch（仅写 localStorage + 广播事件）；`request.ts` 请求拦截器附加 `X-Active-Registrar-Id` 头。验证：`useActiveRegistrar.spec.ts` 新用例绿；`request.spec.ts` 补头断言绿。
- [x] 4.2 `types/common.ts` 的 `ActiveRegistrarResponse` 改 `{identities, defaultRegistrarId}`；`api/resources.ts` 删 `activeRegistrarApi.select`。验证：`vue-tsc` 编译通过。
- [x] 4.3 AppLayout 顶栏改用 `useActiveRegistrar`（含 spec 用例改造）。验证：`AppLayout.spec.ts` 绿。
- [x] 4.4 「我的」身份页：普通 Contact 不显示"设为默认"，默认徽标=默认注册人员（`MyProfile.vue` 及 spec 适配）。验证：`MyProfile.spec.ts` 绿。
- [x] 4.5 auth store / router：PERSONAL 已初始化免登录（路由守卫、`/login` 重定向）；`edition==='PERSONAL'` 时隐藏用户管理/自助注册/改密入口（含 AppLayout 用户区与系统菜单）；向导个人版分支写入 edition（bypassLogin 依赖）。验证：`auth.spec.ts` 新增 bypassLogin/setEdition/logout 清理用例绿；SystemLayout.spec 补个人版隐藏用户管理菜单用例。
- [x] 4.6 InitWizard 个人版去除密码步骤：setup 响应按版本区分（TEAM 携带向导会话令牌，PERSONAL 为空），commit 无密码体。验证：向导 spec 用例改造绿 + InitWizardControllerTest 个人版分支（setup 空会话/commit 无体）。

## 5. 全量验证与收尾

- [x] 5.1 web 全量测试通过（含 2.x/3.x 改造的目标类）；`mvn -q test`（多模块需 `-am`）。验证：测试报告零新增失败。
- [x] 5.2 前端 `vitest` 全量 + `npm run build`。验证：全绿、构建产物更新到 `web/src/main/resources/static`。
- [x] 5.3 `openapi.json` 重新生成。验证：无 `PUT /active-registrar`，GET 响应结构为新契约。
- [x] 5.4 三方言迁移实测：SQLite scratch 全新库 + 真实库副本、SQL Server 副本（达梦走语法审阅并记录"待实测"）。验证：迁移日志 + 抽查 ext 表数据。
- [x] 5.5 冒烟：个人版全流程（启动→向导→直进应用→建条目归属 RA→顶栏切换 registrar→请求头生效→刷新保持）；团队版登录/切换/注册回归。验证：scratch 冒烟脚本 PASS。
- [x] 5.6 `openspec validate --all` + GitNexus `detect_changes --scope all` 无 partial/truncated。验证：命令输出干净。

## 6. 回滚预案（随交付记录，不执行）

- [x] 6.1 在任务执行记录中附回滚 SQL 清单（重建 `contact_account_binding`、`account.active_registrar_id` 列、从 ext 两表回搬数据），供必要时手工执行。
