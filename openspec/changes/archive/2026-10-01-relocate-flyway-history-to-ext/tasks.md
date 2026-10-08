# 任务：Flyway schema 历史表迁出 mdr，归位 ext

## 1. 配置

- [x] 1.1 在 `metadata-registry-web/src/main/resources/application-team.yml` 的
  `spring.flyway` 下新增 `default-schema: ${FLYWAY_DEFAULT_SCHEMA:ext}`，修订
  注释（历史表属工具表归 ext；既有库由搬运器零重放搬迁；达梦用
  `FLYWAY_DEFAULT_SCHEMA` 逃逸覆盖）。验证：profile 加载后
  `spring.flyway.default-schema` 解析为 ext（可经 Spring Boot 配置绑定或
  启动日志确认）

## 2. 搬运器组件

- [x] 2.1 新增 `FlywayHistoryRelocator`（`web/service/database` 包）：
  仅 `DatabaseEnvironment.vendor() == SQLSERVER` 时执行；目标处无历史表且
  主 schema 有历史表且目标 schema 存在时执行
  `ALTER SCHEMA <目标> TRANSFER <主>.flyway_schema_history`；两表并存时
  跳过并 WARN；其余方言 no-op。验证：`FlywayHistoryRelocatorTest` 全绿 ✅ 13/13
  （评审修正后：目标 schema 取自 Flyway 自身配置；搬迁 DDL 显式
  setAutoCommit(true) 并恢复——连接池 auto-commit=false 会回滚未提交 DDL；
  目标 schema 不存在时 fail fast；探测改 DatabaseMetaData；标识符双引号限定）
- [x] 2.2 新增启动顺序 `BeanFactoryPostProcessor`（`web/config`，仿
  `FlywayAfterJpaConfig`）：存在 Flyway 迁移初始化器 bean 定义时把
  搬运器追加进其 dependsOn；flyway 关闭（local profile）时无操作。验证：
  `FlywayHistoryRelocationConfigTest` 3/3 绿 ✅（评审修正：经
  `getBeanNamesForType(FlywayMigrationInitializer.class)` 类型查找——Boot 3.5
  实际 bean 名为 `flywayInitializer`，原字面量 `flywayMigrationInitializer`
  会静默 no-op；已同修正 `FlywayAfterJpaConfig` 失效字面量）+
  `FlywayHistoryRelocationWiringTest`（真实 Boot 上下文接线 + default-schema
  绑定断言）1/1 绿

## 3. 测试与验证

- [x] 3.1 `FlywayHistoryRelocatorTest` 覆盖：SQL Server 需搬/无需搬/双表
  并存/目标 schema 缺失（fail fast）、SQLite 与达梦与 Flyway 未启用 no-op、
  目标取自 Flyway 配置（含 defaultSchema 回退 schemas[0]）、显式提交与
  autoCommit 恢复、连接失败包装。验证：该测试类全绿 ✅ 13/13
- [x] 3.2 `metadata-registry-web` 模块全量测试零回归。验证：
  `./mvnw -pl metadata-registry-web test`（或 `mvn`）0 failures ✅
  worktree 隔离比对（评审修正后复跑）：本变更运行 1365 用例（+17 新增测试），
  失败集（10）与干净 HEAD 基线失败集（12）相比为真子集且与初版完全一致
  ——WorkspaceServiceIntegrationTest 既有失败 + 并发类时序抖动，零回归
  （注：多模块验证须 `-am`，否则 web 模块对 ~/.m2 中并行流程反复改写的
  common-facilities jar 编译，结果不可信）
- [x] 3.3 SQLite scratch 副本冒烟：personal 路径启动与迁移行为不变。
  验证：应用以 personal profile 指向 scratch 副本库正常启动、
  `flyway_schema_history` 仍在库顶层且被业务保留逻辑覆盖 ✅
  `scratch/flyway-history-ext-smoke/smoke.sh` 3/3 PASS（顶层保留、
  行数只增、baseline 行数不变=零 re-baseline、日志无 ALTER SCHEMA）
- [ ] 3.4 SQL Server 真实库核对（用户环境）：重启后
  `ext.flyway_schema_history` 行数等于迁移前 `mdr` 表行数、
  `OBJECT_ID('mdr.flyway_schema_history')` 为 NULL、启动日志无
  re-baseline/无基线后迁移重放 —— 待用户部署新包后执行（见交付说明）

## 4. 收尾

- [x] 4.1 `openspec validate --strict` 通过 ✅（`Change 'relocate-flyway-history-to-ext' is valid`）；
  `detect_changes` 本次运行无 partial/truncated 标记，所列变更符号全部属于
  并行流程（avatar/individual）；本变更提交时需按 AGENTS.md 重跑（新增文件+
  单 yml 编辑，预期干净）
- [x] 4.2 更新 `.superpowers-memory/CURRENT_STATE.md` 并添加 session
  journal 条目，记录方案 A 决策与验证证据 ✅
  （`session-journal/2026-10-01-relocate-flyway-history-to-ext.md`）
