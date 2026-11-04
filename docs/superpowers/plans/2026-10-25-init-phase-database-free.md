# 初始化阶段数据库依赖延后实施计划（2026-10-25）

对应 OpenSpec 变更 `init-phase-database-free` 与 Superpowers 设计
`docs/superpowers/specs/2026-10-25-init-phase-database-free-design.md`。TDD 逐章推进，
每章以任务清单 1.x–6.x 的验证方式收口。

## 关键代码事实（实现前核对结论）

- 相位判定必须在 `main()` 的初始化器层完成：注册顺序 `LocalConfigBootstrapInitializer` →
  （新增）`BootstrapPhaseInitializer` → `FreshDatabaseInitializer`；前者把本地配置注入
  environment 后，后者才能读到解析后的 `${...}` 值算指纹。
- `SystemSettingService.isInitialized()` 约 10 处调用。必须改为状态文件判定的只有
  启动期可达路径：`InitializationWizardInterceptor`（每请求）、`InitWizardService.getStatus()`、
  `ForcePasswordResetInterceptor`、`DatasourceConfigController` 的数据源保存守卫；
  setup/commit/abort 等完整相操作维持读库（它们本就要写库）。
- 现有 Runner 的 `run(...)` 均为 public，可抽为无参方法供引导调用：
  `LegacyLanguageSchemaRepairer`(@Order 5)、`StaticDataPresetRunner`(@Order 10)、
  `IdentifierStrategyLoader`(@Order 90)、`RegistryContextBackfillRunner`(@Order 101)、
  `ContextTestDataInitializer`(@Order 100，条件装配)；`StaticDataPresetService.presetAll()` 已是服务方法。
- `FlywayHistoryRelocator` 挂在 `@PostConstruct` 且依赖 Flyway bean；Flyway 自动配置关闭后
  它自然失效，其搬迁逻辑移入引导的迁移步骤（按方言守卫）。
- `StartupReadinessIndicator` 目前在 `ApplicationReadyEvent` 无条件置 ready；改为
  `ready = 上下文就绪 && (向导相 || 引导结果成功)`。

## 章 1：状态基座与相位注入（任务 1.1–1.4）

新包 `io.github.matrixlane.metadataregistry.web.bootstrap`：

- `InitializationState`（record：schemaVersion / datasourceConfigured / datasourceFingerprint /
  initialized / edition / updatedAt）。
- `InitializationStateStore`：路径解析（与 `DatasourceConfigService.localConfigPath()` 同目录）、
  原子写（temp + replace）、容错读（损坏 → empty）、内存缓存按 mtime 失效。
- `DatasourceFingerprint`：对 vendor / jdbcUrl / dialect / mapping-resources / flyway locations /
  schemas / default-schema 的规范化序列化取 SHA-256；测试锁定键序与空白无关、不含口令。
- `BootstrapPhase`（WIZARD / FULL）与 `BootstrapPhaseResolver`：矩阵判定 + LEGACY_BOOTSTRAP 分支
  （状态文件缺失且配置存在 → 一次性连库读 `system.initialized` + edition 并写状态文件；
  连库失败 → 向导相且不写状态文件）。连库用裸 JDBC，驱动与方言从 environment 读。
- `BootstrapPhaseInitializer`（ApplicationContextInitializer）：注入 `app.bootstrap.phase`、
  `spring.flyway.enabled=false`、`spring.jpa.hibernate.ddl-auto=none`，向导相再注入 Hikari
  不快速失败与 `minimum-idle=0`。

TDD 顺序：指纹 → 状态文件读写 → 判定矩阵 → 属性注入（`StandardEnvironment` 断言）。

## 章 2：上下文存活与零数据库行为（任务 2.1–2.5）

- Hikari/Flyway/Hibernate 参数由章 1 的注入承担；集成测试用代理连接池或计数包装断言
  向导相上下文刷新期间零连接。
- `FreshDatabaseInitializer` 读 `app.bootstrap.phase`：向导相直接返回（不探测）。
- `InitializationStatusProvider`（@Component）：`isInitialized()` / `getEdition()` 读状态文件
  （缓存 + mtime 失效）；切换 `InitializationWizardInterceptor`、`InitWizardService.getStatus`、
  `ForcePasswordResetInterceptor`、`DatasourceConfigController` 守卫四处调用。

## 章 3：数据库初始化引导（任务 3.1–3.6）

- `BootstrapStage` 枚举与 `BootstrapFailure`（stage / code / message）。
- `DatabaseBootstrapService.bootstrap()`：`synchronized`、幂等可重入，按序
  连接校验 → 建表（仅全新库）→ 迁移（Flyway 程序化 + 历史搬迁）→ 旧库修复 → 静态预置 →
  策略装载 → 治理回填；任一步失败记录阶段与原因并短路；`BootstrapException` 携带 stage/code。
- 建表：从 `EntityManagerFactory` 元数据 `SchemaExport` 导出 DDL 至脚本再 `ScriptUtils` 执行；
  全新库判定复用 `FreshDatabaseInitializer` 的 Freshness 语义（抽为共享工具）。
- 各 Runner 抽无参方法、`run(...)` 委托之；引导服务直接调用；`DatabaseBootstrapRunner`（@Order 1）
  在完整相调用一次，其余 Runner 改为检查引导结论后跳过。
- `POST /api/v1/bootstrap/retry`（回环 + 个人版守卫）复用同一 `bootstrap()`。

## 章 4：失败态与用户交互（任务 4.1–4.3）

- 失败落盘 `~/.metadata-registry/restart/bootstrap-failure.json`（原子写，无凭据）；
  `StartupStatusResponse` 扩展 `failure{stage,code,message}`，`StartupController` 暴露；
  `StartupReadinessIndicator` 按相位与引导结论计算 ready。
- 前端：启动等待页新增失败态（原因 + 重试 + 退出）；退出调 `POST /api/v1/bootstrap/exit`
  （复用 `ApplicationRestartExecutor.scheduleExit`，无事务 → 启动器随之结束托管）。

## 章 5：相位切换与自动重启衔接（任务 5.1–5.3）

- `DatasourceConfigService.saveConfig` 末尾写状态文件（datasourceConfigured=true + 指纹）；
  向导"下一步"保存路径沿用；跳过数据库步骤（保持当前数据源）也写状态文件并触发自动重启。
- 真实进程验证：配置保存 → 自动重启 → 引导完成 → 向导续上（scratch 隔离端口）。

## 章 6：兼容、故障复现与回归（任务 6.1–6.6）

- 既有部署一次性引导集成测试（已初始化库不丢数据、未初始化库正常进向导）。
- `FactoryResetService` 重置状态文件 `initialized=false`。
- 三方言全新库实测；复现 2026-10-25 事故场景（ext 卫星表缺失 → 应用可启动并展示原因）。
- 全量回归对基线、构建、静态产物同步、文档与记忆同步。

## 验证命令

- 后端目标：`./mvnw.cmd -pl metadata-registry-web test -Dtest=<类> -Dsurefire.failIfNoSpecifiedTests=false`
- 后端全量：`./mvnw.cmd -pl metadata-registry-web test`
- 前端：`npm test`、`npm run build`（`metadata-registry-ui/`）
- 规范：`openspec validate init-phase-database-free --strict`
- 图谱：交付前 `detect_changes(scope=all)`（先 `node .gitnexus/run.cjs analyze --index-only`）
