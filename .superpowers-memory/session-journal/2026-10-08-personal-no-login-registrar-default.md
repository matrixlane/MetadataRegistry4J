# 2026-10-08 personal-no-login-registrar-default

## 需求（用户三点，经两轮澄清定稿）

1. 去除个人版用户登录（本地应用可访问，验证无意义）。
2. `active_registrar_id` 转变为 **ext.registrar 上的默认标志**（用户-个人关联的一系列 registrar 中的默认者）。
3. 当前激活 registrar 状态保存在**浏览器端**，不持久化到数据库。

澄清中用户进一步裁定：`contact_account_binding` 也不需要——账号归属直接放 `ext.contact.account_id`；`is_default` 只落 `ext.registrar`（ext.contact 不设默认标志，"落实在 ext.registrar 上更合适"）。

## 方案（均经用户确认）

- **个人版免登录 = 方案 A**：新增 `PersonalEditionAuthenticationFilter`（JWT 过滤器之前）：PERSONAL 且无 Bearer 头时装载唯一 SUPER_ADMIN 构建认证主体（SUPER_ADMIN 恒 ROLE_SUPER_ADMIN+ROLE_REGISTRAR；绑定任一 Contact 追加 ROLE_SUBMITTER；forceChangePassword 恒 false）。依赖走 ObjectProvider——@WebMvcTest 切片缺合作 bean 时安全 no-op（避免改动 30+ 切片测试；LoggingActuatorSecurityTest 走 @Import 真实过滤器类）。
- **数据模型**：`ext.contact(contact_id PK, account_id)`（binding 整表迁入后删表）+ `ext.registrar(registrar_id PK, is_default)`；`account.active_registrar_id` 删除；`contact_account_binding` 表删除。orm-team.xml 显式 name（同 RegistrationAuthorityExtension 教训）；orm-sqlite.xml 物理名 ext_contact/ext_registrar。
- **激活浏览器化**：localStorage `activeRegistrarId` + `X-Active-Registrar-Id` 头；后端解析链 头（校验归属+挂 RA）→ ext.registrar.is_default → 首个有效身份，纯读。`PUT /active-registrar` 删除；GET 改 `{defaultRegistrarId, identities}`。前端 `useActiveRegistrar` 模块级单例 composable（在途去重、load 重读 localStorage、失败 reject 以保留消费方失败态语义）。
- **默认推导**：提交默认上下文 = 默认 registrar 的 contact+RA → 首个绑定 contact；设默认仅 Registrar（普通 Contact 422）；删默认 registrar 递补剩余首个；首 registrar 自动默认（ensureDefaultRegistrarWhenFirst）。
- **迁移 V20261009001~0004**（三方言同号；**删除动作调整到 0003/0004**——0002 回填仍需读旧表，tasks 里"0001 删表"的原始拆分在实施中修正）：建 ext.contact 搬迁 → 建 ext.registrar 三级回填（active_registrar_id→默认绑定 registrar→账号首个，账号级互斥）→ 删列 → 删表+个人库密码标志清零。
- **向导**：PERSONAL 去设密步骤，commit 无密码不发令牌（setup 响应 data=null）；TEAM 不变。
- **UI 裁剪**：个人版隐藏登录/注册（路由守卫 bypassLogin）、用户管理（SystemLayout 菜单项）、顶栏登出按钮；我的身份页"设为默认"仅 Registrar 行。

## 验证（最终结果）

- worktree `.worktrees/personal-no-login`，分支 `feat/personal-no-login-registrar-default`（自 master 32c3bde7）。
- 目标测试类首轮 65/65 绿（ActiveRegistrarService 19 用例重写含请求头三分支、ContactAccountService 13、过滤器 6、MyContacts 26 等）。
- 前端 vitest 313/313（新增 useActiveRegistrar 7 用例；AppLayout/ItemRegistrationSection/WorkspaceView/SubmissionRecordPicker/SystemLayout/MyProfile/request spec 适配；单例 reset 进各 spec beforeEach）；vue-tsc 干净；vite build 过、dist→static。
- **HEAD 既有失败（基线 worktree 对跑确认，非本变更引入）**：WorkspaceServiceIntegrationTest 9、DatasourceConfigServiceTest 1、SegmentTemplateStrategyIntegrationTest 1。FlywayHistory* 3 个测试类在 master HEAD 本就编译不过（主类为并行会话未提交 WIP），验证时临时挪开、提交前恢复。
- **后端全量（对跑基线）**：分支失败集与 master HEAD 基线完全一致（WorkspaceServiceIntegrationTest 9、DatasourceConfigServiceTest 1、SegmentTemplateStrategyIntegrationTest 1、ScopedIdentifierConcurrencyIntegrationTest 1——后两者单测对跑证实基线同样失败/不稳定，属既有环境性失败）；**零新增回归**。
- **SQLite 冒烟（scratch/personal-no-login-smoke/smoke.sh）**：副本源 flyway-history-ext-smoke（结构恰在 V20260930001 前期）——真实应用启动路径上 4 个迁移全部应用成功：ext_contact=2 行搬迁正确、contact_account_binding 与 active_registrar_id 已删、每账号≤1 默认。应用启动止于 RegistryContextBackfillRunner 触发**既有** SQLite 64 表 join 上限（KNOWN_FAILURES 已载，与本变更无关）。
- 冒烟抓出并修复一个真 bug：V20261009004 三方言的 system_setting 列名误写 id/value（实为 setting_key/setting_value），在真实库上会失败。
- detect_changes compare master：65 文件/243 符号/93 流程，risk=critical=预期爆炸半径（先例：authority-contact-governance），无 partial/truncated；openspec validate --all 66/66。

## 教训 / 关键决策

- `@WebMvcTest` 会把应用里的 `Filter` bean 纳入切片 → 新过滤器一上线 30+ 切片测试上下文全崩；ObjectProvider 注入是最小侵入解法（LoggingActuatorSecurityTest 这类 @Import(SecurityConfig) 的 @SpringBootTest 仍需显式给过滤器——真实 import 利用 ObjectProvider 空降级）。
- composable 模块级单例在测试间泄漏状态 → 每个消费 spec 的 beforeEach 必须 `useActiveRegistrar().reset()` + 清 localStorage；load() 需做在途去重（同一挂载树多个消费方共享一次请求，否则 mockRejectedValueOnce 竞态）。
- composable 的 load() 不能吞掉异常——ItemRegistrationSection 的"查询失败"提示依赖 reject 语义。
- 多模块验证必须 `-am`（~/.m2 的 ext jar 被并行流程改写，漏 -am 报新实体找不到）。
- 同名卫星表（contact/registrar）加入 DatabaseEnvironment 同名表路由锁定集合，裸键保留给标准表（级联删除经 qualifyAuto 引用），SQLite 物理名 ext_ 前缀入 EXT_TABLES。
- 实施修正：tasks 中迁移 0001"删 binding 表"调整为 0004 删除（0002 回填依赖旧表 is_default 与 active_registrar_id）。

## 评审与修复（2026-10-08 双代理评审：后端+前端/规格各一名独立只读评审员）

**BLOCKER（已修）**：① sqlserver V20261009002 对 UNIQUEIDENTIFIER 用 MIN() 非法（T-SQL 该类型无比较运算符）→ 改 ROW_NUMBER() OVER (PARTITION BY ... ORDER BY contact_id)；② 前端个人版向导完成后被弹回登录页——`auth.edition` 全仓仅 checkInitialized 写入，向导个人版分支只 setInitialized → 守卫 bypassLogin 恒 false；修复：auth store 新增 setEdition，向导 callSetup/handleCommit 个人版分支写入。
**MAJOR（已修）**：③ sqlite 迁移改 Java 迁移（V20260922001 范式，pragma_table_info/sqlite_master 全守卫——个人版存量库可能从未有 active_registrar_id 列、全新库默认档无 binding 表）；④ dm V20261009004 守卫列名 ID/VALUE 错（SETTING_KEY/SETTING_VALUE），达梦个人版清零被 WHEN OTHERS 静默吞掉；⑤ ContactAccountService.unbind 残留 registrar 卫星默认行 + bind 改绑产生双默认 → unbind 连带 deleteRegistrarExtension、bind 检测迁入默认与目标账号既有默认冲突时清除迁入标志；类级 @Transactional；⑥ 前端登出/换账号不重置 useActiveRegistrar 模块单例（跨账号脏读）→ AppLayout.handleLogout/Login.vue 登录成功重置；⑦ load() 失败也置 loaded=true 致永不重试 → 仅成功置位、失败可重试、移除死代码 reload()；⑧ orm-sqlite 未在默认档加载导致卫星映射落 mdr 表——**不改**（与 language_identification/registration_authority 同名卫星的既有配置坑同类，超出本变更范围，记入遗留）。
**测试补口（已补）**：ActiveRegistrarControllerTest（纯单元级——切片中 principal 透传无既有范式且 springSecurityFilterChain 行为不可控；契约含 GET 新结构与 PUT 删除）、InitWizardControllerTest 个人版分支（setup 空会话/commit 无体）、ContactAccountServiceTest bind/unbind 不变式与有序返回、auth.spec.ts（bypassLogin/setEdition/logout 清理）、SystemLayout.spec 个人版菜单隐藏、MyProfile.spec 普通 Contact 无设默认按钮、AppLayout.spec 全局单例重置。
**评审明确指出不修的 MINOR**（记录）：default 档 orm 映射坑（见 ⑧）、/system/users 深链个人版仍可访问（菜单已隐藏，后端角色守卫仍在）、迁移 min() 字节序 vs UUID 有符号序注释、CORS 已核实无碍（CorsConfig allowedHeaders 通配回显）。
**修复后验证**：目标后端测试全绿（含新增 20+ 用例）；前端 vitest 320/320 + vue-tsc；后端全量独占复跑失败集==基线（仅时间戳行差异 + 已证基线同挂的 ScopedIdentifier 并发 flake），零新增回归；Java 迁移真实启动路径端到端验证通过（单一版本 20261009001、ext_contact 搬迁、旧表旧列清除；应用自身仍止于既有 64 表 join 上限 Runner——KNOWN_FAILURES 平台限制，与本变更无关）。
**教训**：同一 worktree 绝不并发跑两个 maven 命令（clean/package 与 test 互相污染 target——反复踩坑两次）；mvn 多模块必须 -am；maven 目标顺序是 clean 在前 package 在后（写反会先打包后清靶）；JSON 尾引号错误会让整个 SPRING_APPLICATION_JSON 被静默忽略（外部配置顶上，表象诡异）；SPRING_APPLICATION_JSON 里 List 属性不能用空串覆盖（须显式数组）。

## 遗留

- SQL Server/达梦迁移脚本语法按既有范式编写、经审阅，真实库实测待部署（项目惯例：dm 待实测）。
- 回滚 SQL 清单见任务执行记录（重建 contact_account_binding 与 active_registrar_id 列、从 ext 表回搬）。
- 与并行会话的合并：ContactService/MyProfile/resources.ts 等文件主树有 WIP，合并期按会话顺序 rebase。
