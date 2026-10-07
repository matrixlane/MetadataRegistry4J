# 实施计划：personal-no-login-registrar-default

日期：2026-10-08
设计稿：`docs/superpowers/specs/2026-10-08-personal-no-login-registrar-default-design.md`
OpenSpec：`openspec/changes/personal-no-login-registrar-default/`（tasks.md 30 项）

## 执行环境

- 隔离 worktree：`.worktrees/personal-no-login`，分支 `feat/personal-no-login-registrar-default`（自 master 32c3bde7）。
- 主树 54 项并行 WIP 不触碰；多模块验证一律 `mvn -am`（~/.m2 jar 会被并行流程改写）。
- 顺序：数据层 → 服务层 → 安全层 → 前端 → 迁移实测 → 全量验证。

## 阶段 1：数据层（tasks 1.1–1.5）

1. `ContactExtension`（ext.basicandcore）+ `RegistrarExtension`（ext.registration）；orm-team.xml 加两行登记；orm-sqlite.xml 加两个物理名覆盖。
2. 迁移 SQL 先写 SQLite 方言并 scratch 实测，再写 SQL Server / dm（同号 V20261009001~0004）：
   - 0001 建 ext.contact + 搬 binding + drop 旧表
   - 0002 建 ext.registrar + 三级回填（per-account 至多一默认： correlated subquery 取优先级最高者）
   - 0003 drop account.active_registrar_id
   - 0004 个人库密码标志清零
3. 数据层测试：造 fixture 副本断言回填优先级。

## 阶段 2：服务层（tasks 2.1–2.10，TDD）

顺序与依赖：
- 2.1 Account 删字段（编译驱动，连带删 AccountRepository finder）。
- 2.2/2.3 ContactAccountService 重构 + RegistrarExtension 读写（先写测试：设默认清他默认、首个自动默认、解绑语义）。
- 2.4 ActiveRegistrarService 解析链（TDD：头合法/头非法/默认优先/回退首个/纯读断言）。
  - 请求头读取：通过 `RequestContextHolder.getRequestAttributes()` 非侵入获取，避免改所有调用方签名；无法获取（非 Web 线程）则跳过头部步骤。
- 2.5 Controller GET 改造 + 删 PUT（连带 DTO/Request 删除）。
- 2.6 ContactService 默认语义（TDD：提交默认上下文三态、普通 Contact 设默认 422、删除递补）。
- 2.7 RA/RegistrarService markDefault 迁移（测试改造）。
- 2.8 DeletionService JDBC 清理（集成测试）。
- 2.9 AuthService 改经新服务。
- 2.10 InitWizardService 个人版 commit 无密码不发 token。

## 阶段 3：安全层（tasks 3.1–3.3）

- `PersonalEditionAuthenticationFilter`：`SystemSettingService.getEdition()==PERSONAL` 且 `SecurityContextHolder` 无认证 → 装载唯一 SUPER_ADMIN（`AccountRepository` 按 sysRole 查）→ `AuthenticatedUser`（forceChangePassword 恒 false）+ 角色装配（SUPER_ADMIN 恒 ROLE_SUPER_ADMIN+ROLE_REGISTRAR；绑定 Registrar → ROLE_REGISTRAR；绑定任一 Contact → ROLE_SUBMITTER）。
- SecurityConfig `addFilterBefore`（JWT 过滤器之前）。
- 安全测试：个人版无 token → 业务接口 200/principal=admin；团队版无 token → 401；个人版未初始化 → 业务接口 499 链路不受影响。

## 阶段 4：前端（tasks 4.1–4.6）

- `useActiveRegistrar` composable + spec；request.ts 头注入 + spec。
- types/resources API 改造 → vue-tsc 抓全部调用点（AppLayout/WorkspaceView/ItemRegistrationSection 等）。
- 我的身份页默认徽标 + 设默认按钮条件渲染。
- auth store/router 个人版免登录 + 入口隐藏（edition==='PERSONAL' 条件）。
- InitWizard 个人版去密码步骤。

## 阶段 5：验证（tasks 5.1–5.6）

- web 全量 `mvn -q test`（worktree 内 `-am`）；vitest + build；openapi 重生成；三方言迁移实测（SQLite scratch + SQL Server 副本；dm 语法审阅）；冒烟（个人版全流程 + 团队版回归）；detect_changes。

## 风险记录

- 主树并行 WIP 与本变更在 ContactService/MyProfile/resources.ts 等文件重叠 —— 合并期按会话顺序 rebase 解决（项目惯例）。
- SQLite 64 表 join 上限：DeletionService 集成测试若触及既有上限，按 KNOWN_FAILURES 惯例记录。
