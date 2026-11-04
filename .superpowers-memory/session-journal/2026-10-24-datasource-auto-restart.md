# 2026-10-24 数据源自动重启（datasource-auto-restart）

## 目标

用户在恢复出厂设置后重新初始化时把 SQL Server 改为 SQLite，系统却只提示“请手工重启”。用户明确要求：自动重启、面向非技术用户、能看到进度。首版范围（用户确认）：Windows 单机个人版 + 项目自带启动器 + 仅初始化向导。

## 关键决策

1. **常驻启动器托管**（用户确认的方案 A）：`scripts/backend-supervisor.ps1` 持有自己创建的后端进程，负责启动、超时、核对与回滚；后端只负责写配置、备份、核对与触发退出。否决“后端临时助手”“Spring 上下文热切换”。
2. **页面展示真实阶段而非百分比**：保存设置 / 重启应用 / 准备数据库 / 核对设置 / 恢复；用原生无值 `<progress>` 不确定态 + `role="status"` 礼貌宣读。
3. **成功判据是“双确认”**：既等 `/api/v1/startup/status` 完整就绪，又核对实际方言与 JDBC 目标；端口可连不算成功。
4. **失败自动回滚**：目标启动失败/超时/配置不匹配 → 恢复旧配置并重启，失败目标库文件不删除。

## 实现

- 后端：`DatasourceRestartService`（事务、备份、幂等指纹、并发锁、状态核对）、`ApplicationRestartExecutor`（延迟退出）、4 个 DTO、3 个新端点（能力查询 / 保存并应用 / 状态查询），既有 `PUT /datasource/config` 契约不变。
- 启动器：812 行 PowerShell，`start.bat`/`stop.bat` 改为托管方式；控制目录 `~/.metadata-registry/restart/`。
- 前端：`api/datasourceRestart.ts`、`utils/datasourceRestart.ts`（UUID、会话标记、静默轮询状态机）、`DatasourceRestartProgress.vue`、`InitWizard.vue` 接线（能力降级、刷新恢复、离开守卫例外、终态处理）。

## 过程中发现并修复的真实缺陷

1. **切换方言未同步 ORM 映射**：本机 SQL Server 配置显式指定 `spring.jpa.mapping-resources: orm/orm-team.xml`，切 SQLite 若沿用会在 SQLite 上创建 `ext.` 模式表并失败。修复：`saveConfig` 按方言写 `orm/orm-sqlite.xml` / `orm/orm-team.xml`。
2. **未初始化时就绪探针被 499 拦截**：`/api/v1/startup/status` 在未初始化系统返回 403（`InitializationWizardInterceptor`），启动器永远等不到就绪。修复：把该探针加入白名单（同时修正 `startup-readiness` 规范“匿名可访问”的既有偏差）。
3. **PowerShell 脚本编码**：Windows PowerShell 5.1 按本地 ANSI 读取 UTF-8 无 BOM 脚本，中文注释导致引号误解析、脚本无法运行。修复：启动器脚本保持纯 ASCII 注释（`check-syntax.ps1` 解析校验）。
4. **测试 JAR 组装**：Spring Boot 嵌套依赖必须以 STORE 方式存储，否则类加载失败；测试用隔离 fat jar 需同步各模块 `target/classes`。

## 验证证据（真实进程，非模拟）

| 场景 | 结果 |
|---|---|
| SQL Server → SQLite（用户原始场景，隔离 SQL Server 临时库） | `SUCCEEDED` + `MATCHED`，PID 更替，新库建成，配置 `mapping-resources` 同步为 sqlite |
| SQLite → SQLite 成功路径 | `SUCCEEDED` + `MATCHED`，请求文件清理、终态保留 |
| 配置被改写导致核对不匹配 | `ROLLED_BACK`（`CONFIG_MISMATCH`），旧配置自动恢复 |
| 目标库损坏（可连接但无法启动） | `ROLLED_BACK`（`STARTUP_EXITED`），目标文件保留、备份清理 |
| 启动超时（缩短到 3 秒） | `ROLLED_BACK`（`STARTUP_TIMEOUT`） |
| 启动器崩溃遗留非终态事务 | 再次运行自动恢复（`INCOMPLETE_RESTART`），旧配置可用 |
| 用户主动停止 | 停止信号优先，端口释放，不再拉起 |
| 非受管直接 `java -jar` | 能力 `supported=false`（`SUPERVISOR_REQUIRED`），自动应用被拒且不改配置 |
| 真实浏览器 | 进度界面实时显示阶段与等待时间；完成后显示“新数据库设置已生效”并回到可继续的向导步骤；重启中刷新无“放弃初始化”弹窗且自动续上完成 |

## 回归

- 后端目标测试：`DatasourceRestartServiceTest` 12、`DatasourceConfigControllerTest` 4、`InitializationWizardInterceptorTest` 3、`DatasourceConfigServiceTest` 10 —— 全绿。
- Web 全量：1593 tests / 9F+4E，失败集合与本次改动前**完全一致**（Workspace、SegmentTemplate、RegistrySpecification、CommonBaselineGuard、ScopedIdentifierConcurrency），零新增失败。
- 前端：402 tests 全绿（42 文件），`npm run build` 成功；产物已同步到 `metadata-registry-web/src/main/resources/static/`。
- OpenSpec：`openspec validate datasource-auto-restart --strict` 通过。

## 遗留 / 待评审确认

1. `RECOVERY_FAILED`（旧配置本身也无法恢复）分支未做故障注入，仅由代码路径与状态机覆盖。
2. 向导 `setup`/`commit` 续接未在浏览器手工跑完（重启后回到可继续的数据库步骤已验证；续接逻辑由既有向导测试覆盖）。
3. 工作树含并行的 `baseline-data-init-rebuild` 未提交改动；静态资源刷新因此也包含该并行 UI 变更，提交时需按变更分组。`detect_changes(scope=all)` 的 critical 评级覆盖整个工作树（含并行改动），本变更自身新增符号为启动/重启链路。
4. `start.bat` 的启动分支未对真实 8081 实例执行（避免打断用户正在运行的应用）；启动器本体已用隔离端口完整验证。
5. **架构文档已重新生成**：Kimi 通道与 codex 通道共用同一份周配额（LLM 429），改用 minimax 通道（`https://api.minimaxi.com/v1`）成功生成 24 页；同步映射为 `overview.md → docs/architecture/ARCHITECTURE.md`、其余模块页 → `docs/architecture/modules/`（`docs.md` 沿用历次约定不纳入）。18 个模块页刷新、新增 5 页（openspec×4 + root.md，root.md 已记录 start.bat → backend-supervisor.ps1 的新启动流程）。仅 `metadata-registry-common-facilities-src.md` 本轮生成失败，保留上一轮版本。生成期间 CLI 覆写了 `~/.gitnexus/config.json`，已恢复为原 Kimi 配置（provider=openai / model=kimi-for-coding / apiKey 取当前 OAuth access_token）。

## 代码评审补强轮（2026-10-25）

两份独立只读评审（后端/前端、启动器）共报 4 高危 + 11 中危 + 若干低危，全部核实处置。要点：

1. **环境变量覆盖**：数据源配置以 `${DB_URL:默认值}` 落盘，机器上已有 `DB_URL` 等变量时会覆盖新设置，表现为"未能应用并已恢复原设置"这种无法定位的提示。改为受理前比对，冲突即拒并指明变量名。
2. **响应未写完就退出**：服务层自行退出进程会截断 202 响应，浏览器侧无法区分"响应丢失"与"真实失败"。改为退出过滤器在过滤器链结束后 flush 再退出。
3. **自称受管但启动器已死**：进程内环境变量不反映启动器存活，改为校验 `supervisor.pid.json` 的 PID/进程名/可执行路径。
4. **失败会让系统停摆**：事务无效/恢复失败/启动器自身异常时原本直接退出且 `finally` 停掉后端。改为写入终态后以磁盘配置重新拉起并继续托管。
5. **前端误判已初始化**：`checkInitialized` 失败时降级返回 `true` 且保留旧缓存，用它的返回值做分支会把用户误送到首页。新增 `refreshInitialized()`（取不到新值返回 `null` → 失败态）。
6. **失败态丢失事务**：失败时清除会话标记会让用户再次提交撞 409，改为保留，仅事务不存在时清除并回到可编辑表单。

### 真实进程验证中暴露的两个缺陷

- **非属主实例误删属主记录**：未获得互斥体的启动器在 `finally` 里仍会执行 `Stop-ManagedBackend`，连带删除属主的 `backend.pid.json`（属主进程仍在跑，记录却没了 → 后续孤儿清理失效）。改为只有获得互斥体的实例才触碰控制目录中的记录。
- **`DateTime` 与 `DateTimeOffset` 比较抛异常**：PowerShell 中两者直接比较会抛类型转换异常，被内层 `catch` 静默吞掉，导致超期备份清理从未真正执行。改为统一用 UTC `DateTime` 比较。

### 验证结果

| 项 | 结果 |
|---|---|
| 后端目标测试 | 37 例全绿（`DatasourceRestartServiceTest` 17、`DatasourceConfigControllerTest` 4、`DatasourceConfigServiceTest` 10、`DatasourceRestartExitFilterTest` 3、`InitializationWizardInterceptorTest` 3） |
| Web 全量 | 1601 tests / 9F+4E，失败集合与基线完全一致，零新增 |
| 前端 | 405 tests 全绿（42 文件），`npm run build` 成功，产物已同步到 `metadata-registry-web/src/main/resources/static/` |
| 启动器真实进程 | 9 项断言全通过：正常启动、互斥标记、属主存活、属主后端不受影响、孤儿存活、孤儿被替换、旧孤儿终止、无效事务后后端仍存活且写入 `RECOVERY_FAILED`、超期备份删除 |
| OpenSpec | `openspec validate datasource-auto-restart --strict` 通过；三份规范已补本轮语义 |

### 过程中的环境处置

- `mvn package` 在 8081 实例运行期间会因 JAR 被占用而无法 repackage，plain jar 覆盖了 target 中的 fat jar。已用本地仓库的 fat jar 为底重新组装（当前 `target/classes` + 当前各模块 jar），冒烟验证可正常启动（17.59s，接口 200），并覆盖回 target 路径；8081 实例全程未中断。
- 验证脚本自身踩过三个坑（桩 JAR 缺 `Main-Class`、含空格的 JDK 路径未加引号、上一轮遗留启动器持有互斥体），均属测试脚手架问题，已修正为按宿主进程断言与等待条件而非固定睡眠。

## 事故：应用无法启动（2026-10-25 15:29）

**现象**：用户运行 `start.bat` 后后端启动即退出，日志报 `SQLITE_ERROR: no such table: ext_language_identification`。

**根因链**：

1. 用户配置 `~/.metadata-registry/application-local.yml` 一直沿用 SQL Server 时代的 `spring.jpa.mapping-resources: orm/orm-team.xml`，而数据源早已改为 SQLite。team 映射把 ext 实体指向 `schema="ext"`，SQLite 无 schema 概念 → 4 张 ext 卫星表（`ext_language_identification`/`ext_contact`/`ext_registrar`/`ext_registration_authority`）**从未建出**；库里只剩 83 张标准表，且无任何业务数据。
2. 该库"有表" → `FreshDatabaseInitializer` 判定非全新空库 → personal profile 的 `ddl-auto: none` 生效 → 缺失的 ext 表不会被补建。
3. 并行改动 `baseline-data-init-rebuild` 把语言名称数据统一到通用基线脚本 `data/scripts/common/sqlite/00-common.sql`，该脚本 INSERT `ext_language_identification` → 表不存在 → 脚本失败并按设计抛出（不完整基准比启动失败更危险）→ 应用启动中止。

**处置**：

- 配置改为 `mapping-resources: orm/orm-sqlite.xml`（SQLite 的 ext 实体用 `ext_` 物理名前缀，与 `db/migration/sqlite` 一致）；原文件备份为 `application-local.yml.bak-extmapping-20261025-153259`。
- 旧库仅有 1 行 flyway 记录（零业务数据），改名为 `metadata-registry.db.pre-ext-fix-20261025-153305` 留档（可随时还原）→ 下次启动按"全新空库"路径由 ORM 建全部表（含 ext 卫星表）+ Flyway 基线化。
- 结果：新库 112 张表，4 张 ext 卫星表齐备，`ext_language_identification` 22 行（通用基线导入成功），后端 16.9 秒启动完成，`/api/v1/init-wizard/status` 返回 `initialized:false`（正处于用户预期的重新初始化状态）。

**教训**：

1. `spring.jpa.mapping-resources` 与数据源方言不一致是**静默**的：标准表照建，ext 卫星表悄悄缺失，直到有脚本依赖它才暴露，报错信息还指向"缺表"而非"映射错了"。
2. 用户配置位于 `~/.metadata-registry/application-local.yml`，由 `spring.config.import` 加载并**覆盖** profile 里的 `orm/orm-sqlite.xml`；profile 的正确值救不了被覆盖的配置。
3. 初始化向导的保存路径已按方言写入正确映射（`DatasourceConfigService.saveConfig`），手工沿用旧配置是本次的绕行入口。
4. 该失败模式对用户极不友好（"应用无法启动" + 深处一条 SQL 报错）。建议后续在启动期增加"方言与 ORM 映射一致性"检查，冲突时直接给出可读提示，而不是让脚本在缺表处炸掉。
