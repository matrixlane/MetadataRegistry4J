# 初始化阶段数据库依赖延后设计（init-phase-database-free）

## 1. 背景与问题

2026-10-25 15:29 发生一次真实故障：用户运行 `start.bat` 后后端启动即退出，日志深处只有一句
`SQLITE_ERROR: no such table: ext_language_identification`，界面表现是"应用无法启动"。

根因是三层叠加：

1. 用户配置 `~/.metadata-registry/application-local.yml` 沿用 SQL Server 时代的
   `spring.jpa.mapping-resources: orm/orm-team.xml`，而数据源早已是 SQLite。team 映射把 ext 卫星表指向
   `schema="ext"`，SQLite 无 schema 概念 → 4 张 ext 卫星表**从未建出**。
2. 该库"有表" → `FreshDatabaseInitializer` 判定非全新空库 → personal profile 的 `ddl-auto: none` 生效 →
   缺失的表不会被补建。
3. 通用基线脚本 `data/scripts/common/sqlite/00-common.sql` 要往 `ext_language_identification` 写语言名称 →
   脚本失败并按设计抛异常（"不完整基准比启动失败更危险"）→ 应用启动中止。

这次故障暴露的不是某个 bug，而是**一条被违反的不变量**：

> **在数据库配置完成之前，应用启动链路里不允许出现任何依赖数据库的行为。**

当前实现全面违反它：应用在用户还没选数据源之前，就已经建好连接池、连过库、跑过 Flyway、执行过 DDL 脚本。
其后果是双重的——数据库有问题时用户**打不开用来修数据库的那个页面**；而失败信息只有一条深处的 SQL 报错，
用户无从判断"是库坏了"还是"配置错了"。

### 1.1 用户已确认的决策（2026-10-25）

1. **初始化状态不来自数据库**：以 `~/.metadata-registry/` 下的状态文件记录为准，辅以数据库相关配置
   （及/或其它必要配置）是否存在。
2. **数据库配置完成后执行一次自动重启，在这次重启过程中完成数据库初始化。**
3. **失败语义**：报告错误原因，同时提示"初始化系统失败"，等待用户确认退出；相关问题留待用户离线自行解决。

## 2. 目标与非目标

### 2.1 目标

- G1 初始化未完成（数据库配置未确认）时，应用的启动与运行**零数据库行为**：不建连接、不查表、不跑迁移、
  不执行任何 DDL/DML。
- G2 "系统是否已初始化"由**本地状态文件 + 必要配置是否存在**判定，不再每次请求查库。
- G3 数据库配置完成后的那一次自动重启，承担"数据库初始化"（连接校验 → 建表 → 迁移 → 预置）。
- G4 引导失败时应用**不退出**：展示可读原因，等待用户确认退出或重试。
- G5 既有部署升级后不被迫重新初始化（不丢数据）。

### 2.2 非目标

- 不改变 ORM 映射、迁移脚本、基线数据的**内容**。
- 不重构与启动无关的业务逻辑。
- 不追求"向导相完全没有 DataSource/JPA bean"——上下文里可以存在未被调用的 DB bean，
  但**不得有任何代码路径真正访问数据库**（见 §5.3 的可观测判据）。

## 3. 现状：启动期数据库依赖全清单

| # | 环节 | 位置 | 依赖形态 | 失败后果 |
|---|---|---|---|---|
| 1 | 空库探测 | `FreshDatabaseInitializer.java:163` | 裸 JDBC 连库查 `sqlite_master` | 探测失败被容忍，但**依然连了库** |
| 2 | Flyway | 自动配置 | 连接 + 迁移/基线化 | 启动失败 |
| 3 | Hikari 连接池 | `spring.datasource.hikari` | `initialization-fail-timeout` 未配置 = 默认 1ms | 取不到连接立即抛异常 → 启动失败 |
| 4 | Hibernate 建表 | EMF 创建期 `ddl-auto=update` | 生成并执行 DDL | 启动失败 |
| 5 | Flyway 历史搬迁 | `FlywayHistoryRelocator`（`@PostConstruct`） | 改写历史表 | 启动失败 |
| 6 | 语言旧表修复 | `LegacyLanguageSchemaRepairer`（@Order 5） | DDL 修复 | 启动失败 |
| 7 | 通用基线 + 预置 | `StaticDataPresetRunner`（@Order 10）→ `presetAll()` | 执行 DML 脚本 | 启动失败（**本次事故点**） |
| 8 | 标识符策略装载 | `IdentifierStrategyLoader`（@Order 90） | 读策略表 | 启动失败 |
| 9 | 注册上下文回填 | `RegistryContextBackfillRunner`（@Order 101） | 写治理数据 | 启动失败 |

此外，**向导自身也依赖数据库**：

- `InitWizardService.getStatus()` 是 `@Transactional(readOnly=true)`，靠 `systemSettingService.isInitialized()` 判定状态；
- `InitializationWizardInterceptor` 在**每个请求**上调用 `systemSettingService.isInitialized()` 决定是否跳转向导。

即"用来修数据库的页面"本身依赖数据库——这是本次故障最核心的结构性问题。

## 4. 方案比较

### 方案 A：单上下文 + 显式可捕获的引导阶段（推荐）

保持一个 Spring 上下文与一次构建，把所有数据库动作从"隐式的自动配置/Runner"改造为"**显式、有序、可捕获**"的引导步骤：

- 上下文永远能起来：Hikari 不快速失败（`initialization-fail-timeout=-1`）、EMF 固定 `ddl-auto=none`、
  关闭 Flyway 自动配置 → 数据库坏掉不会拖垮上下文。
- 引导步骤在 `@Order(1)` 的 Runner 中按序执行（连接校验 → 建表 → 迁移 → 预置），任一步失败即记录原因并跳过后续。
- 向导相：整个引导阶段连同其余 DB Runner 一并跳过，**一次数据库连接都不发生**。

代价：需要把"ORM 建表"从 EMF 创建期移到引导期（用 EMF 元数据导出 DDL 再执行），这是一条新执行路径，需三方言验证。

### 方案 B：双上下文（向导应用 / 完整应用）

`main()` 按相位选择两个不同的 `@SpringBootApplication`：向导应用做窄扫描（不加载 JPA/Flyway），完整应用照旧。

- 优点：向导相彻底没有 DB bean，隔离最干净，语义最直白。
- 代价：向导相**不能复用现有 `InitWizardController`**（它注入仓储），要另起一套 API 并改前端；
  窄扫描还要复刻认证、安全、静态资源、布局等横切配置。工作量与回归面都很大。

### 方案 C：最小改动（只把预置挪走 + 失败降级）

只做"关闭 Flyway 自动配置改程序化执行 + 预置 Runner 捕获异常 + 连接池不快速失败"。

- 优点：改动最小。
- 代价：向导相仍会创建 EMF 与连接池，并**仍会做空库探测**（连库）→ 不满足 G1 的不变量。

### 推荐

**方案 A**。理由：

1. 它满足 G1 的**可观测**判据（§5.3）：向导相零连接、零查询、零脚本执行；
2. 它同时解决 G4：上下文不再因数据库失败而消失，失败原因可展示、可重试；
3. 相比 B，它不引入第二套 API 与前端分叉，回归面可控；
4. B 的"窄扫描复刻横切配置"在本项目里代价过高（认证过滤器、初始化拦截器、静态资源、布局文件都要复刻）。

## 5. 相位模型

### 5.1 状态文件

新增 `~/.metadata-registry/initialization-state.yml`（与 `application-local.yml` 同级）：

```yaml
schemaVersion: 1
datasourceConfigured: true      # 用户已完成数据库配置
datasourceFingerprint: <sha256> # 有效数据源配置的指纹
initialized: false              # 向导 commit 完成
updatedAt: '2026-10-25T08:00:00Z'
```

**指纹**取有效数据源相关配置的 SHA-256（vendor / jdbcUrl / 方言 / `mapping-resources` / Flyway locations 与 schemas）。
指纹的作用是识别"配置被手工改动"——手工改动一律使相位退回向导相，而不是带着未知配置直接进完整相。

### 5.2 相位判定（`main()` 中、上下文刷新之前）

| 条件 | 相位 | 行为 |
|---|---|---|
| 状态文件不存在 **且** 数据库配置不存在 | `wizard` | 零数据库行为；向导第 1-2 步可用 |
| 状态文件不存在 **且** 数据库配置存在（既有部署升级） | `bootstrap-legacy` | **一次性**向数据库确认已初始化状态并写入状态文件（§5.5 的唯一例外），之后按判定结果进入对应相位 |
| `datasourceConfigured=false` 或指纹失配 | `wizard` | 零数据库行为；向导提示"数据源配置已被手工修改，请重新确认" |
| `datasourceConfigured=true` 且指纹匹配 | `full` | 执行数据库初始化引导 |
| `full` 的引导失败 | `degraded` | 应用保持运行，展示原因，等待用户确认退出或重试 |

### 5.3 向导相的"零数据库行为"判据（可验证）

向导相必须同时满足：

- 没有任何到数据库的 TCP 连接（Hikari `minimum-idle=0` + 池不校验 → 空闲池不建连接）；
- 没有任何针对数据库的查询/脚本执行（含空库探测本身也不做）；
- `/init-wizard/status` 与初始化拦截器的"是否已初始化"判定**只读状态文件与配置**；
- 预置/修复/回填/策略装载/Flyway 历史搬迁等 DB Runner 全部不执行。

### 5.4 完整相的引导步骤（`@Order(1)` 的引导 Runner）

1. **连接校验**：受控超时内取一个连接；失败 → 记录 `CONNECTION_FAILED`。
2. **全新库判定 + 建表**：库无用户表时，用 EMF 元数据导出 DDL（`SchemaExport` → 脚本）并执行；
   非全新库跳过（结构由 Flyway 负责）。
3. **迁移/基线化**：程序化执行 Flyway（全新库以共同基线版本基线化；既有库按迁移链推进）。
4. **预置与修复**：通用基线脚本 → 语言旧表修复 → 标识符策略装载 → 注册上下文回填。
5. 全部成功 → `StartupReadinessIndicator` 置 ready；任一步失败 → 记录原因与失败阶段，**跳过后续步骤**，
   置"初始化失败"状态并等待用户。

### 5.5 既有部署的一次性引导（唯一例外）

既有部署没有状态文件，而"是否已初始化"这一事实此刻只存在于数据库里。若直接按"文件缺失 → 未初始化"处理，
用户会被推回向导，而向导的 setup 会清除既有业务数据——这是不可接受的数据损失。

因此保留**唯一一次**数据库读取：状态文件缺失且数据库配置存在时，向数据库确认一次已初始化状态并写入状态文件，
此后不再以数据库作为该判据。此例外必须是一次性的、可审计的（日志明确记录），并在状态文件写入后永久消失。

### 5.6 相位切换与自动重启的衔接

```
向导相 ──用户完成数据库配置──▶ 写配置 + 写状态文件(datasourceConfigured=true, 指纹)
                                  │
                                  └─▶ 触发自动重启（已有机制）
                                            │
                                            ▼
                                   完整相：连接 → 建表 → 迁移 → 预置
                                            │
                                            ▼
                                   向导继续（setup → commit → initialized=true 写回状态文件）
```

"保持当前数据源并继续"（跳过数据库步骤）等价于"确认默认数据源"，同样写状态文件并触发那一次重启——
否则默认库永远不会建表与预置。

## 6. 失败语义（对应用户决策 3）

- 失败信息落盘：`~/.metadata-registry/restart/bootstrap-failure.json`（阶段、原因码、可读描述、时间戳），
  同时经 `/api/v1/startup/status` 暴露（`ready=false` + `failure{stage,code,message}`）。
- 前端在初始化界面显示：**"初始化系统失败"** + 原因 + 两个动作：
  - **重试初始化**：重新执行引导步骤（不必重启应用）；
  - **退出应用**：后端退出 → 启动器随之结束托管 → 应用完全停止。
- 用户既不重试也不退出时，应用保持在该状态页，等待用户处理（符合"留待用户离线自行解决"）。
- 日志中不得出现数据库口令或完整连接串。

## 7. 变更影响面

| 层 | 改动 |
|---|---|
| 后端 | 新增 `BootstrapPhaseResolver`（相位判定）、`InitializationStateStore`（状态文件）、`DatabaseBootstrapService`（引导步骤）、`BootstrapFailureController`（失败态接口）；改造 `FreshDatabaseInitializer`（向导相不探测）、`InitWizardService.getStatus`、`InitializationWizardInterceptor`（改用状态文件）、`StartupReadinessIndicator`（ready 由引导结果决定）、`DatasourceConfigService`（写配置时同步写状态文件与指纹）；关闭 Flyway 自动配置并程序化执行；Hikari 参数按相位调整 |
| 前端 | 初始化界面增加"初始化失败"状态与"重试初始化/退出应用"动作；`/init-wizard/status` 契约扩展（失败态字段） |
| 脚本 | `start.bat` 无需改动；启动器在引导失败时保持后端运行（现有语义已满足） |
| 规范 | 新增 OpenSpec 变更 `init-phase-database-free`，spec 覆盖 `system-initialization`（相位与状态判定）、`datasource-configuration`（配置确认与指纹）、`application-restart-supervision`（引导失败时的托管语义） |

## 8. 风险与缓解

| 风险 | 缓解 |
|---|---|
| R1 建表路径从 EMF 创建期移到引导期（`SchemaExport` + 脚本执行）属新路径，三方言行为可能不一致 | 以三方言各自的全新库实测为验收项；SQLite 已有 `ddl-auto=update` 的等价 DDL 产出，可用同一 Hibernate 方言生成器对齐 |
| R2 EMF 在 `ddl-auto=none` 下是否完全不连库 | 已有 `use_jdbc_metadata_defaults: false` + 显式方言；补一条"上下文启动期间零连接"的集成断言 |
| R3 两处状态来源（状态文件 vs 数据库）不一致 | 以状态文件为判据；提供"重新确认数据源"动作；`bootstrap-legacy` 例外一次性对齐 |
| R4 Hikari 后台补连接造成向导相仍有连接尝试 | 向导相强制 `minimum-idle=0` 且不校验连接 |
| R5 状态文件被手工删/改 | 缺失时按 `bootstrap-legacy` 处理（读一次库补写），不误判为未初始化 |
| R6 引导失败后应用常驻，用户不知情 | 失败态页面 + 控制目录落盘 + 启动器事件日志三处可查 |

## 9. 测试策略

- **单元**：相位判定矩阵（状态文件/配置/指纹的组合）、状态文件读写与原子替换、指纹计算与失配检测、
  引导步骤的失败短路与原因映射。
- **切片**：向导相下 `/init-wizard/status` 不触库（用 mock 仓储断言零调用）；失败态接口契约。
- **集成（三方言）**：全新库 → 引导完成且表结构与预置齐全；既有库 → 迁移链正常；数据库不可达 →
  应用仍起来且展示原因；ext 卫星表缺失 → 引导失败并给出可读原因（复现本次事故）。
- **真实进程**：隔离端口启动"引导失败"场景，确认应用可访问、错误可读、退出按钮可结束进程。
- **回归**：web 全量与前端全量对基线，零新增失败。

## 10. 验收标准

1. 未配置数据库时启动应用：日志与抓包均无任何数据库连接，向导第 1-2 步可用。
2. 配置完成后自动重启：数据库初始化（建表/迁移/预置）在该次重启中完成，向导自动续上，无需手工重启。
3. 数据库不可达或结构不兼容时：应用可打开，显示"初始化系统失败"与可读原因，提供重试与退出，等待用户确认。
4. 既有部署升级后：不被推回向导，业务数据不丢失。
5. 既有"未初始化"语义（`initialized`）与新的状态文件判定一致，且不破坏既有的工厂重置/重新初始化流程。
6. 全部真实验证在隔离环境完成；后端与前端回归相对基线零新增失败。
