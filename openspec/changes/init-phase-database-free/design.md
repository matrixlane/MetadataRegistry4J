## Context

本变更的前提与动机见 `proposal.md` 的 Why。这里只记录约束实现方案的现状事实：

- 应用的 Spring 上下文在 `main()` 中以 `SpringApplicationBuilder` 启动，并注册两个 `ApplicationContextInitializer`：`LocalConfigBootstrapInitializer`（配置层）与 `FreshDatabaseInitializer`（在上下文刷新前用裸 JDBC 判定 SQLite/SQL Server/达梦的空库状态并注入属性）。相位判定必须落在同一层——上下文刷新之后已经来不及决定"要不要建连接池以外的东西"。
- 数据库动作分散在两类位置：自动配置（Flyway、Hibernate 建表、Hikari 连接池）与应用自身的 Runner（`LegacyLanguageSchemaRepairer` @Order 5、`StaticDataPresetRunner` @Order 10、`IdentifierStrategyLoader` @Order 90、`RegistryContextBackfillRunner` @Order 101），另有 `FlywayHistoryRelocator` 挂在 `@PostConstruct`。
- Runner 均为 `public void run(...)`，`IdentifierStrategyLoader` 另有 `refreshRegistry()`；`StaticDataPresetService.presetAll()` 是被三处（启动期、向导 setup/abort、恢复出厂）复用的幂等入口。这决定了"引导步骤可被显式重复调用"是可行的。
- `application-personal.yml` 已固定 SQLite 方言并设置 `spring.jpa.properties.hibernate.temp.use_jdbc_metadata_defaults: false`（避免批量元数据读取），`ddl-auto: none`；`application.yml` 用 `${DDL_AUTO:update}`，全新库由 `FreshDatabaseInitializer` 注入 `update`。
- 用户目录 `~/.metadata-registry/` 已有 `application-local.yml` 与 `restart/` 控制目录，且 `application-local.yml` 通过 `spring.config.import` 加载并**覆盖** profile 里的同名键（这正是配置与方言不一致会静默生效的原因）。
- 前端初始化界面已有能力降级、刷新恢复、离开守卫例外与阶段进度（`DatasourceRestartProgress.vue`），本次失败态沿用同一套"非技术化文案 + 可执行动作"范式。

## Goals / Non-Goals

**Goals:**

- 相位判定在上下文刷新前完成，并以属性形式注入，**所有**数据库相关行为都能读到同一个相位结论。
- 数据库动作全部收敛到**一个可重复调用、可捕获失败**的服务，使"启动期执行"与"运行期重试"共用同一条代码路径。
- 上下文在任何数据库状况下都能起来：数据库不可达、结构不兼容、脚本失败时，应用仍然可打开并展示原因。
- 状态文件与指纹的写入具备原子性，且不包含口令等敏感信息。

**Non-Goals:**

- 不改变表结构、迁移脚本、基线数据内容与方言映射口径。
- 不引入第二套向导 API 或第二套前端页面（否决双上下文方案的关键理由）。
- 不在本次变更中重构与启动无关的业务逻辑。
- 不追求"向导相完全没有 DataSource/JPA bean"：允许存在未被调用的 DB bean，但不允许任何代码路径访问数据库。

## Decisions

### D1 单上下文 + 显式引导阶段（否决双上下文与最小改动）

**选择**：保持一个 Spring 应用与一次构建；把数据库动作从"隐式自动配置 + 分散 Runner"改造为"显式、有序、可捕获的引导服务"。

**否决的替代方案**：

- *双上下文（向导应用/完整应用）*：向导相若要真正无 DB bean，就得另起一个窄扫描的 `@SpringBootApplication`，于是现有 `InitWizardController`（注入仓储）不可复用，要另写一套数据源配置 API 并同步改前端；窄扫描还要复刻认证过滤器、初始化拦截器、静态资源与布局文件。收益（语义更干净）不足以抵消 API 分叉与回归面。
- *最小改动（只挪预置 + 失败降级）*：向导相仍会做空库探测（连库）、仍会创建连接池并触发后台补连接，不满足"零数据库行为"的可观测判据。

**代价**：建表路径从实体工厂创建期移到引导期（R1）。

### D2 初始化状态文件与配置指纹

**状态文件**：`~/.metadata-registry/initialization-state.yml`，与 `application-local.yml` 同级。

```yaml
schemaVersion: 1
datasourceConfigured: true
datasourceFingerprint: '<sha256>'
initialized: false
updatedAt: '<iso8601>'
```

- **写入原子性**：同目录临时文件 + 原子替换（与 `DatasourceConfigService` 现有写配置的做法一致），避免读到半截内容。
- **指纹定义**：对"有效数据源相关配置"的规范化序列化取 SHA-256，覆盖 vendor、JDBC URL、Hibernate 方言、`spring.jpa.mapping-resources`、Flyway locations、Hibernate default_schema（六项均为 saveConfig 对全部方言落盘的键）。规范化意味着键序与空白不影响结果。
- **指纹的用途**：识别"配置被手工改动"。配置与状态文件记录不一致（指纹失配）时系统退回向导相并提示重新确认，而不是带着未知配置进入完整相。**替代方案**是只比对配置是否存在（用户决策中的"必要配置是否存在"），但那样无法区分"用户确认过这份配置"与"有人手改过"——今天的故障正是手工配置漂移导致，故采用指纹。

### D3 相位判定矩阵与既有部署的一次性例外

判定在 `main()` 的初始化器中完成（与 `FreshDatabaseInitializer` 同一层）：

| 状态文件 | 数据库配置 | 指纹 | 相位 |
|---|---|---|---|
| 缺失 | 缺失 | — | `wizard` |
| 缺失 | 存在 | — | `legacy-bootstrap`：向数据库确认一次已初始化状态并写状态文件，然后按结论进入 `wizard` 或 `full` |
| 存在 | 缺失 | — | `wizard`（配置被删除 → 视为需要重新配置） |
| 存在 | 存在 | 失配 | `wizard`（提示"配置已被手工修改，请重新确认"） |
| 存在 | 存在 | 匹配 | `full` |
| `full` 引导失败 | — | — | `degraded` |

**一次性例外的理由**：既有部署没有状态文件，而"是否已初始化"此刻只存在于数据库里。若按"文件缺失 → 未初始化"处理，用户会被推回向导，而向导的 setup 会清除既有业务数据——不可接受的数据损失。因此保留唯一一次数据库读取用于补写状态文件；补写完成后该判据永久不再读库。例外必须可审计（明确日志）且只能发生一次。

### D4 引导服务：单一可重入入口

新增 `DatabaseBootstrapService`，方法 `bootstrap()` 幂等且可重复调用，按序执行：

1. **连接校验**：受控超时内取连接；失败 → 记录 `CONNECTION_FAILED` 并短路。
2. **建表（仅全新库）**：从实体工厂元数据导出 DDL 并执行（见 D5）。
3. **迁移/基线化**：程序化执行 Flyway（全新库以共同基线版本基线化，既有库按迁移链推进）。**Flyway 自动配置关闭**，改由本步骤显式调用——自动配置在上下文刷新期执行，失败会直接摧毁上下文，无法满足"报告原因并等待用户确认退出"。
4. **旧表修复**：`LegacyLanguageSchemaRepairer.run(...)`。
5. **通用基线与静态预置**：`StaticDataPresetService.presetAll()`。
6. **标识符策略装载**：`IdentifierStrategyLoader.run(...)`（幂等）。
7. **治理数据回填**：`RegistryContextBackfillRunner.run(...)`。

任一步失败 → 记录阶段与原因 → **跳过后续步骤** → 置失败态。启动期由一个 `@Order(1)` 的 Runner 调用它；运行期"重试初始化"调用同一个方法——因此启动与重试**共用一条代码路径**，不存在"启动能过、重试不能过"的差异。

**原各 Runner 的处置**：保留类与 `@Order`，但改为在引导服务内部被调用（或改为检查引导结论后跳过），避免同一动作被执行两次。

### D5 建表路径：从实体工厂创建期移到引导期

**问题**：当前靠向实体工厂注入 `ddl-auto=update` 建表。实体工厂在上下文刷新期创建，若建表失败（方言不支持、权限、锁）则**上下文整体失败**，用户看到的就是"应用无法启动"——正是要消除的失败模式。

**选择**：实体工厂恒为 `ddl-auto=none`（不连库、不建表）；全新库的建表由引导步骤 2 完成：从实体工厂的元数据导出 DDL（`SchemaExport` → 脚本文件）再用 `ScriptUtils` 对数据源执行。产出的 DDL 与今天的 `ddl-auto=update` 同源（Hibernate 同一方言生成器），差别只在"由谁执行"。

**否决的替代方案**：

- *保留 `ddl-auto=update` 并在失败时降级*：建表失败仍然摧毁上下文，降级不成立。
- *在 `main()` 里另起一个仅建表的无头上下文*：能捕获失败，但会引入第二个上下文（实体扫描、排除项、启动耗时都翻倍），且失败后仍需"应用起来展示原因"，而那个上下文并不提供该能力——等于把问题推后。

**代价**：R1——新执行路径需三方言实测。SQLite 的 DDL 由 Hibernate 产出为标准语句，`ScriptUtils` 按分号切分执行，与既有预置脚本的执行方式一致。

### D6 上下文存活策略（保证"数据库坏掉也起得来"）

- `spring.datasource.hikari.initialization-fail-timeout=-1`：连接池不再因取不到连接而快速失败（项目此前未显式配置，用的是 Hikari 默认值 1ms）。
- 向导相强制 `spring.datasource.hikari.minimum-idle=0`：空闲池不建连接，杜绝后台补连接行为。
- `spring.jpa.hibernate.ddl-auto=none` 恒定；方言显式给出且 `use_jdbc_metadata_defaults=false`，保证实体工厂构建期不连库。
- `spring.flyway.enabled=false`（自动配置关闭），迁移由引导步骤显式执行。

**替代方案**：把 DataSource/JPA 自动配置整个排除（`exclude=...`）。这会让所有注入仓储的服务无法装配，等价于双上下文的代价，故否决。

### D7 失败态：报告原因 + 等待用户确认退出

- 失败信息落盘 `~/.metadata-registry/restart/bootstrap-failure.json`（阶段、原因码、可读描述、时间戳），并经 `GET /api/v1/startup/status` 暴露为 `ready=false` + `failure{stage,code,message}`。
- 界面显示"初始化系统失败" + 原因 + 两个动作：**重试初始化**（在应用内重跑引导，无需重启）、**退出应用**（后端退出 → 启动器结束托管 → 应用完全停止）。
- 用户既不重试也不退出时，应用保持在该状态页等待——对应"留待用户离线自行解决"。
- 日志与接口不得出现口令或完整连接串。

### D8 相位切换与自动重启的衔接

向导保存数据源配置时（同一次写入）：写 `application-local.yml` → 写状态文件（`datasourceConfigured=true` 与新指纹）→ 触发自动重启。新进程以 `full` 相启动并完成数据库初始化，随后向导自动续上。

"保持当前数据源并继续"（跳过数据库步骤）等价于"确认当前数据源"，同样写状态文件并触发那一次重启——否则默认库永远不会建表与预置。

**与既有 `datasource-auto-restart` 变更的关系**：本次复用其自动重启、状态查询与失败回滚机制，不改动其契约；引导失败时后端保持运行，与启动器现有的"无待处理事务时后端退出即结束托管"语义不冲突（引导失败不产生重启事务）。

### D9 前端契约

`GET /api/v1/init-wizard/status` 扩展失败态字段（`failure{stage,code,message}` 与 `phase`），前端在初始化界面按相位渲染：进行中（既有进度组件）、失败（原因 + 两个动作）、正常。

## Risks / Trade-offs

- **[R1] 建表改为引导期执行（三方言）** → 以三方言各自的全新库实测为验收项；产出的 DDL 与 `ddl-auto=update` 同源，可用同一 Hibernate 方言生成器对齐；若某方言实测失败，退回"该方言保留 `ddl-auto=update`"的混合策略。
- **[R2] 实体工厂在 `ddl-auto=none` 下可能仍尝试连库** → 已有 `use_jdbc_metadata_defaults=false` 与显式方言；补一条"上下文启动期间零数据库连接"的集成断言（用连接计数或代理拦截）锁死。
- **[R3] 状态文件与数据库不一致** → 以状态文件为判据；提供"重新确认数据源"动作；不一致时退回向导相而非按数据库事实行事。
- **[R4] 既有部署升级路径误判** → `legacy-bootstrap` 例外一次性对齐，且有专门测试覆盖"无状态文件 + 既有已初始化库"不得被推回向导。
- **[R5] 状态文件被手工删除** → 与 R4 同路径处理（读一次库补写），不误判为未初始化。
- **[R6] 引导失败后应用常驻，用户未察觉** → 失败态页面 + 控制目录落盘 + 启动器事件日志三处可查。
- **[R7] Hikari 后台线程在向导相尝试补连接** → 向导相 `minimum-idle=0` 且池不校验连接。
- **[R8] 改造触及启动链路，可能影响既有部署** → 启动期行为以集成测试与三方言实测覆盖；web 全量与前端全量相对基线零新增失败方可交付。

## Migration Plan

1. 先落地状态文件与相位判定（不改变既有数据库行为），验证既有部署升级后不被推回向导。
2. 再落地引导服务与失败降级，逐个方言验证全新库建表与预置。
3. 最后接入前端失败态与"重试初始化/退出应用"。
4. 回滚策略：本次变更不涉及表结构与数据迁移，回滚即回退代码；用户目录下的状态文件在回滚后被忽略即可（既有部署的行为回到"读库判定"）。

## Open Questions

- 引导失败时是否需要保留失败现场（连接信息、Flyway 历史表快照）以便用户离线提交？当前决定只保留阶段、原因码与可读描述，不复制数据库内容，避免在用户目录堆积含凭据的文件。

## 实施期补充决策（真实进程验证后）

### D10 向导相连接池指向一次性内存库

Hibernate 6 的 `PersistentTableStrategy` 在 EMF 构建期为 bulk 变更探测临时表能力，
**必然取一次物理连接**（与元数据访问开关无关）。这有两个后果：向导相会在真实库路径上
产生连接（创建 0 字节文件）；更关键的是真实库路径不可写时向导相启动直接失败——用户
连修复配置的向导都进不去。

决策：向导相把连接池的物理连接引到 `jdbc:sqlite::memory:`（同时覆盖
`hikari.jdbc-url` 与 `hikari.driver-class-name`）。真实 URL 仍保留在环境中——
当前数据源展示、指纹计算、连接测试都读环境而非连接池，不受影响。完整相不注入该覆盖。

### D11 全新库建表脚本的生命周期

- 脚本仅在"完整相 + 全新库"时注入（`scripts.action=create`），向导相不注入（避免
  D10 之外的连接）；
- Hibernate 写脚本文件是**追加**语义：注入前先删除旧 `schema-create.sql`，否则多次
  完整相全新库启动会叠出重复建表语句；
- 引导执行脚本时临时把连接置为自动提交——连接池 `auto-commit=false` 下 DDL 不显式
  提交会在连接关闭时整体回滚，Flyway 随后看到空库而从头重放不可重放的历史迁移。

### D12 全新库迁移基线强制共同基线版本

引导的迁移步骤在全新库时必须以 `FRESH_BASELINE_VERSION` 基线化；若沿用
application.yml 的既有库默认基线（20260918002），全新库会重放整段不可在空库执行的
历史迁移（如 SQLite 不支持的 `DROP COLUMN IF EXISTS`）。

### D13 schema 归属规则（用户裁定 2026-10-27）

`mdr` schema 只存放 ISO/IEC 11179 系列标准定义的内容；标准以外的一切（含扩展卫星表、
工具/框架表）统一使用 `ext` schema。`flyway_schema_history` 非标准内容，**必须落
ext**：Flyway 的 `default-schema` 为 ext（历史表随之落 ext），Flyway 同时以
default-schema 为会话默认 schema，因此**迁移脚本的表引用 MUST 全限定**（mdr./ext.，
AGENTS.md 已写入该规则）。配置落盘（saveConfig）与 team profile 均按
schemas=mdr,ext + default-schema=ext 执行；既有库的历史表若在 mdr，须迁移到 ext。
