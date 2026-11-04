# 2026-10-25 初始化阶段数据库依赖延后（init-phase-database-free）

## 目标

2026-10-25 15:29 真实事故：用户配置沿用 SQL Server 时代的 `orm/orm-team.xml` 配着 SQLite 数据源，
ext 卫星表从未建出，通用基线脚本失败，应用启动即退出，只剩一句 `no such table: ext_language_identification`。
暴露的不变量被违反：**数据库配置完成之前，应用启动链路里不允许有任何依赖数据库的行为**（当时有 9 处：
空库探测裸连库、Flyway、Hikari 快速失败、Hibernate 建表、历史搬迁、修复、预置、策略装载、治理回填；
连 `/init-wizard/status`、初始化拦截器、个人版认证过滤器都查库）。

## 用户拍板的决策

1. 初始化状态不来自数据库：`~/.metadata-registry/initialization-state.yml`（datasourceConfigured /
   datasourceFingerprint / initialized / edition / updatedAt）+ 数据库配置是否存在。手工改配置 → 指纹失配 →
   退回向导相重新确认。既有部署仅在状态文件缺失时**一次性**读库补写（防被推回向导丢数据），之后不再读库判定。
2. 数据库配置完成后经**一次自动重启**完成初始化（复用 datasource-auto-restart 的机制）。
3. 失败语义：报告原因、提示"初始化系统失败"、**等待用户确认退出**（重试初始化 / 退出应用两动作），留待离线处理。

## 方案（用户确认设计后 continue-dev）

方案 A：单上下文 + 显式可捕获引导阶段。否决双上下文（向导 API 要另起一套）与最小改动（向导相仍连库）。
总开关 `app.bootstrap.enabled`（application.yml 默认 false；application-personal.yml 显式 true）——
集成测试（test profile）完全不受影响，既有 1601 例零改动。

## 实现要点

- 章1：`DatasourceFingerprint`（6 项输入：vendor/url/dialect/mapping-resources/flyway.locations/
  hibernate.default_schema——全部取自 saveConfig 对任何方言都落盘的键，保存时与启动时计算必然一致）；
  `InitializationStateStore`（原子写 + mtime 缓存 + 容错读）；`BootstrapPhaseResolver`（相位矩阵 +
  legacy 一次性引导，连库接缝可覆写）；`BootstrapPhaseInitializer`（main() 注入：
  flyway.enabled=false、ddl-auto=none、连接池不快速失败，向导相 minimum-idle=0）。
- 章2：`FreshDatabaseInitializer` 向导相零探测；`InitializationStatusProvider`（状态文件为权威来源）；
  切换初始化拦截器、getStatus（去掉 @Transactional——否则进方法即取连接）、数据源控制器守卫；
  `PersonalEditionAuthenticationFilter` 版本判定改走状态提供者（这是请求路径上第 10 处隐藏 DB 依赖，
  真实进程验证时抓到：它每请求查 system_setting 判版本）。
- 章3：`DatabaseBootstrapService`（连接→建表→迁移→修复→预置→策略→回填，synchronized 幂等可重入，
  委托步骤异常统一包装为阶段化 BootstrapException 短路——单测曾抓到原始异常直接逸出的缺陷）；
  全新库建表 = EMF 创建期经 JPA 标准脚本生成写 DDL 文件（Hibernate 6.6 已无 SchemaExport 公共类，
  SessionFactory 也不暴露 Metadata），引导期执行；Flyway 程序化 + 历史搬迁纳入迁移步骤；
  各 Runner 加 BootstrapMode 门控（开启时跳过，引导统一执行）。
- 章4：失败落盘 restart/bootstrap-failure.json；`startup/status` 扩展 failure；
  `POST /bootstrap/retry`（复用同一入口）与 `POST /bootstrap/exit`（复用延迟退出执行器）；
  前端启动屏失败面板（原因 + 重试/退出，vanilla DOM）。
- 章5：saveConfig 末尾"保存即确认"（写状态记录 + 指纹）；commit 与恢复出厂同步写状态记录；
  `POST /datasource/config/confirm-current`（"保持当前数据源"：完整相直接放行，向导相走 apply 重启）。
- 前端：`backendStartup` 失败态；向导 keep 路径经 confirm-current 由后端判定（测试桩同步更新）。

## 真实进程验证（scratch 隔离端口 18084，全部通过）

隔离要点：`metadata.registry.config.file` 指到临时目录 + **空工作目录**启动（否则
`migrateLegacyConfigIfNeeded` 会从仓库根捡 application-local.yml 迁进来）+
`--app.baseline-scripts-dir` 指向仓库 data/scripts（否则预置脚本按设计跳过）。

1. **向导相零数据库行为**：`相位=WIZARD(drift=false)`，ready=true，initialized=false，
   `wizard.db` 未被创建，`/datasource/config` 展示真实 URL（非内存库）。中途抓到并修复
   三个真实缺陷：① `PersonalEditionAuthenticationFilter` 为判版本每请求查库（第 10 处
   隐藏 DB 依赖）；② Hibernate 6 的 `PersistentTableStrategy` 在 EMF 构建期为 bulk 变更
   探测临时表**必取一次物理连接**——真实库路径不可写时向导相直接启动失败，改为向导相把
   连接池引到一次性内存库（`hikari.jdbc-url=jdbc:sqlite::memory:`，真实 URL 仍在环境中）；
   ③ `allow_jdbc_metadata_access` 是 Hibernate 6.6 生效键（旧的
   `use_jdbc_metadata_defaults` 已废弃）。
2. **保存 → 重启 → 完整相引导**：向导相经 API 保存配置 → 状态文件写入
   （datasourceConfigured + 指纹 196a3c22）→ 重启 `相位=FULL(drift=false)` →
   引导完成：112 张表（含 4 张 ext 卫星表）、语言 22+22、全局默认策略 1、
   flyway 基线 1 行、ready=true。中途抓到并修复三个真实缺陷：① 全新库基线版本被
   application.yml 默认（20260918002）覆盖 → 重放不可重放的历史迁移，改为全新库强制
   共同基线；② Hibernate 写 DDL 脚本是**追加**语义 → 多次启动脚本重复（table already
   exists），注入脚本属性前先删旧文件；③ 连接池 `auto-commit=false` 下建表 DDL 不提交
   → 连接关闭整体回滚 → Flyway 看空库从头重放，建表时临时开自动提交。
3. **事故复现**（1 张业务表、无 ext 卫星表的既有库）：`MIGRATION_FAILED`（可读原因：
   `no such table: language_identification`），ready=false，**应用保持存活**
   （init-wizard 200），重试入口复跑同一引导，失败落盘 `restart/bootstrap-failure.json`，
   退出端点返回 202 并使进程与端口释放。与 2026-10-25 事故（应用静默死亡）对比，目标
   语义完全达成。

SQL Server/达梦全新库引导仅有单测与代码路径覆盖（本机无达梦环境），列为遗留。

## 遗留

- SQL Server/达梦的全新库引导仅有单测与代码路径覆盖，未做真实库实测（本机无达梦；SQL Server 临时库可补）。
- 已初始化系统运行期数据库故障不属于本变更范围（启动期失败才降级向导相）。
- `@Scheduled` 后台任务在向导相会对空库报错误日志（不阻断、不属请求路径），未做门控。

## 连环事故：切 SQL Server 失败 + 测试污染状态文件（2026-10-27 12:26）

用户在向导里把默认 SQLite 切到 SQL Server（真实库 metadata_registry），自动重启回滚。
根因：`DatasourceConfigService.saveConfig` 只给 SQLite 写 `flyway.schemas`，切服务端
方言时**沿用旧配置的 `main`** → Flyway 在 SQL Server 的 [main] schema 找不到历史表
（真实历史在 ext）→ 从头重放不可重放的老迁移 → `registration_state 无效` → 启动失败
回滚。事故在真实库 [main] 残留 flyway_schema_history 一张表（已 DROP TABLE + DROP
SCHEMA 清理；mdr/ext 真实数据未受影响）。

修复：saveConfig 按方言写 flyway schemas——SQLite=main，SQL Server/达梦=mdr,ext +
default-schema=mdr（与 team profile 一致）；补单测（spy 桩掉 testConnection，验证
从 SQLite 切 SQL Server 时 main 被替换为 mdr,ext）。端到端验证：一次性库
mdr_e2e_verify 走全新库引导（schema 预建 → 建表 → 基线脚本 → ready=true）。
部署：备份旧 jar（scratch/user-jar-backup-20261027.jar）→ 当前类重建 → 用户环境
重启，initialized=false 向导恢复。

**次生事故**：部署后发现用户状态文件被写成 initialized=true/TEAM——
`InitWizardService.markInitializedInStateRecord` 与 `FactoryResetService` 的状态
写回经**静态** `DatasourceConfigService.localConfigPath()` 解析，@SpringBootTest
未设该系统属性 → 提交/出厂重置类测试把状态写进真实用户目录。处置：删除用户被污染的
状态文件（应用重新一次性引导恢复正常）；根治：surefire 增加
`systemPropertyVariables metadata.registry.config.file=target/test-user-profile/...`，
全部 @SpringBootTest 与真实用户目录隔离（验证：测试后用户状态文件未被触碰，隔离目录
落了测试产物）。

## 第二次切换失败与根治（2026-10-27 13:14）

第一次修复（saveConfig 写 mdr,ext）后用户重试：事务 ROLLED_BACK(STARTUP_TIMEOUT)。
根因链：该真实库**有两张历史表**——ext 里是 9 月 19 日的真历史（34 行至 20261023001），
而 `schemas: mdr,ext`（无 default-schema 时历史落首 schema）让 Flyway 去 mdr 找历史 →
没有 → 从头重放 20260919001 起的迁移 → 180 秒超时。重放全部命中幂等守卫，**零数据损伤**，
只在 mdr 残留 8 行污染历史。

根治：DROP mdr.flyway_schema_history → SELECT INTO 把 ext 的 34 行真历史并入 mdr →
DROP ext 副本（历史表落点统一为 default-schema=mdr，与迁移脚本的非限定名引用
（session default=mdr）及 FlywayHistoryRelocator 的约定一致）。随后经 apply 端点
端到端复跑切换：**STARTING→VERIFYING→SUCCEEDED 仅 21 秒**，应用就绪、
initialized=false、配置 vendor=sqlserver。

后续裁定（2026-10-27，用户）：mdr 只放 ISO/IEC 11179 标准内容，标准以外一律
ext（含 flyway_schema_history，历史表必须落 ext）。已按此修正：AGENTS.md 写入该
分层规则（含"迁移脚本表引用 MUST 全限定 mdr./ext."）、saveConfig 与 team profile
的 default-schema 均为 ext、用户真实库的历史表已迁回 ext、切换复验通过。
system-initialization 规范"历史表位于 ext"一条与此一致，无需修改。

---

## 2026-10-27 事故：向导中途重启后误升完整相 + 向导相内存池被执行方言 SQL

**现象**：用户向导第 2 步保存 SQL Server 数据源并自动重启后，页面永远停在「正在重启应用」（已等待 13 分+ / 5 分+ 无进展）。

**根因（两个叠加）**：
1. `BootstrapPhaseResolver` 已有状态记录分支只判 `datasourceConfigured` + 指纹，**漏判 `initialized`**：`initialized=false` 且指纹匹配时误升 FULL 相。未初始化部署无任何账号，FULL 相安全规则立即把向导锁死在登录墙外（401）。同一缺陷还在"既有部署一次性引导"分支（legacy `initialized=false` 也直通 FULL）。
2. 相位修正为 WIZARD 后启动即炸：`SchemaBootstrapConfig.mdrSchemaInitializer` 按真实配置判定方言=SQL Server，却把 `IF NOT EXISTS...EXEC('CREATE SCHEMA mdr')` 发给向导相被故意引到 `jdbc:sqlite::memory:` 的连接池（`SQLiteException: near "IF"`）。向导相是零数据库行为相位（`DatabaseBootstrapRunner` 只跑 FULL 相），任何启动期 bean 都不得向该池发方言 SQL。

**附带发现**：supervisor 进程一度死亡（后端成孤儿，重启事务无人执行）；vite dev server（5173）假死（监听但不响应，前端轮询全部"临时错误"无限等待）。

**修复**：
- `BootstrapPhaseResolver`：`initialized=false` 一律留向导相（指纹漂移仅作提示）；legacy 分支按 `legacy.initialized()` 决定相位。回归测试 `已确认数据源但初始化未完成且指纹匹配时仍留向导相`。
- `SchemaBootstrapConfig`：引导总开关开启且 `app.bootstrap.phase=wizard` 时跳过 schema 预建（真实库 schema 由完整相引导创建）；开关关闭（测试环境）行为不变。新增 `SchemaBootstrapConfigTest` 3 例。
- 部署：fatjar-stage 同步 + `jar cfm0` 重组 target jar；supervisor 默认参数重启；vite 以日志重定向方式拉起（避免管道背压假死）。

**教训**：相位判定器必须以 `initialized` 为最终闸门；向导相"零数据库行为"意味着所有启动期 SQL 执行者（DataSourceInitializer/sql.init/探针）都必须相位感知或经相位守卫。

### 续：第三层根因（同日修复）——退出过滤器漏 confirm-current 路径

相位与内存池问题修复后，向导「保持当前数据源并继续」仍卡「重启应用」：该端点 `/api/v1/datasource/config/confirm-current` 内部复用 apply 事务（写 restart-request.json + PENDING 状态、返回 202），但 `DatasourceRestartExitFilter` 只匹配 `/config/apply` 路径触发退出——后端永不自杀，supervisor 的 Wait-ForOrdinaryExit 等不到退出信号，事务永远 PENDING，前端轮询非终态无限等待。

**修复**：过滤器对 apply 与 confirm-current 两条 202 路径都 `scheduleExit()`；测试补 `schedulesExitAfterAcceptedConfirmCurrentResponse`。

**处置**：清理 control dir 里遗留的 PENDING 事务文件（否则 supervisor 启动时走 Invoke-Recovery 回滚语义，给用户呈现误导性的"已恢复原设置"）；端到端模拟 confirm-current 验证：202 → 后端退出 → supervisor 接棒 → SUCCEEDED/MATCHED，重启后相位仍=WIZARD。

**教训**：同一业务流程的多入口（apply / confirm-current）共享下游机制（退出过滤器）时，入口枚举必须显式对齐；为该耦合补了事故回归测试。

### 续：第四层缺陷（同日）——向导相"已确认"仍每次要求重启，第 2 步死循环

现象：第 2 步保存数据源并自动重启成功（绿色"已生效"横幅）后，再点「下一步」又触发一轮重启、永远回到第 2 步。根因：`DatasourceConfigController.confirmCurrent` 仅在 `phase==FULL` 时返回 `restartRequired=false`，向导相已确认态（状态记录 datasourceConfigured=true + 指纹匹配）也返回 true → 每次点击都创建新重启事务。

修复：`InitializationStatusProvider` 新增 `isDatasourceConfirmed()`（状态记录确认标志 + 指纹与当前生效配置比对，指纹键集与 `BootstrapPhaseResolver.currentFingerprint` 一致）；controller 改以此为放行条件。指纹失配（漂移）仍走重启重新确认；开关关闭环境恒 false 维持旧行为。测试 +5（InitializationStatusProviderTest）。生产验证：POST confirm-current → restartRequired=false，无事务文件产生。

教训："保持当前数据源并继续"这类幂等入口，后端必须基于持久化确认状态做幂等判定，不能只按相位粗分；否则用户每次操作都会重复触发副作用。

### 续：第五层设计缺口（同日）——向导相"两态"应"三态"，步骤 3-6 需要真实库

现象：向导走到第 5 步（命名空间）点下一步，POST /init-wizard/setup 500。日志：JPA 查询 `metadata_registry.ext.account` 被发往 SQLite 内存库（`near "." syntax error`）。

根因：向导步骤 3-6 需要写业务数据（注册规范/机构/命名空间/管理员），但 init-phase-database-free 把"向导相"整体视为零数据库段（连接池恒引流 :memory:、引导 Runner 只在完整相执行）。"向导"实际是两个阶段：配置数据库之前（零数据库）与之后（已确认数据源，需要真实库 + 建表/迁移/预置）。

修复（两态→三态）：新增 `app.bootstrap.datasource-ready` 属性（BootstrapPhaseInitializer 发布；完整相恒 true；向导相=状态记录已确认且指纹一致）。下游统一消费：内存池引流仅在未就绪时；DatabaseBootstrapRunner 就绪即运行（幂等）；FreshDatabaseInitializer 未就绪不探测；SchemaBootstrapConfig 未就绪跳过；StartupReadinessIndicator 就绪时以引导结论为准。测试同步改为就绪语义并补"向导已确认不引流"用例。

验证：全量回归与既有失败基线一致（无新增）；生产重启后向导相日志 `数据库初始化引导完成`（真实 SQL Server 库），startup ready=true，匿名数据库接口（languages）200。

教训：画相位图时把"用户流程阶段"（向导 6 步）与"运行时相位"（零数据库/就绪）混为一谈；向导内部存在数据库需求拐点（第 2 步保存配置并重启之后），运行时行为必须以持久化确认状态为拐点，而非以向导这个大阶段为单位。
