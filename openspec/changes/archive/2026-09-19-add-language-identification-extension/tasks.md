# 任务拆分：语言标识扩展表

## 1. 数据模型与迁移

- [x] 1.1 在 `metadata-registry-ext` 新增实体 `LanguageIdentificationExtension`（`id` / `language_id` 唯一 not null / `language_name` / `territory_name`），`@Table(name = "language_identification")`，仿 `LanguageGovernance` 卫星模式（不建 JPA 关联），验证编译通过
- [x] 1.2 Flyway 迁移：sqlserver 与 dm 目录建 `ext.language_identification`，sqlite 目录建 `ext_language_identification`（版本号接现有序列），验证三方言迁移脚本命名与语法通过既有迁移测试
- [x] 1.3 `DatabaseEnvironment.EXT_TABLES` 登记 `"language_identification"` 与 `"ext_language_identification"` 两个路由键；`orm-team.xml` 增加实体 `schema="ext"` 覆盖；新增 `orm/orm-sqlite.xml` 覆盖表名并在 personal profile 的 `mapping-resources` 加载；同步更新 `OrmTeamXmlTest` 守卫清单，验证 `OrmTeamXmlTest` 通过
- [x] 1.4 新增 Flyway 迁移 V20260919002（sqlserver/sqlite/dm 三方言）：按 `language_identifier` 关联标准表幂等插入 22 条中文名称预置行（NOT EXISTS 守卫，不清表重插），验证 sqlite 方言脚本在临时 SQLite 库实际执行（`LanguageNameSeedMigrationTest`）通过
- [x] 1.5 启用 Flyway：`application.yml` 默认 `spring.flyway.enabled=true`，`baseline-version: "20260918002"` + `baseline-on-migrate`，`spring.jpa.defer-datasource-initialization: true`（ddl-auto 先于 Flyway，既有库基线化、全新库走基线路径）；sqlite 版 V20260919001 补 `IF NOT EXISTS` 幂等守卫（ddl-auto 可能先建表）；`application-test.yml` 保持 `flyway.enabled: false` 保证测试确定性，验证真实 SQL Server 库出现 `flyway_schema_history`（baseline + 两条新迁移）且 ext 表 22 行值不变
- [x] 1.6 Flyway 启用过程修复（实测发现）：`FlywayAfterJpaConfig` 摘除 Boot 3.5 defer+Flyway 的循环 depends-on；SQL Server 侧显式 `spring.flyway.schemas: mdr,ext`（否则判定空库跳过基线重放老迁移）；`application.yml` 补 `hikari.auto-commit: false`（与 `provider_disables_autocommit` 配对，此前仅用户本地配置提供，测试确定性受损）；application-team.yml 同步 schemas；application-local.yml 启用 flyway 并加 schemas
- [x] 1.7 真实环境验证：SQL Server 既有库基线化成功（flyway_schema_history 3 条、ext 22 行值不变）；既有 SQLite 库（副本，personal）基线化成功且获得 22 条预置名称；全新 SQLite 库走基线路径可启动并播种 22 条语言代码，但预置名称为 0 行（Flyway 先于播种执行）——由 1.8 解决
- [x] 1.8 新增 Flyway 迁移 V20260919003（三方言）：22 条内置语言以固定 UUID 一体化播种语言代码与名称（幂等防重；V20260919002 不可改动保持既有库名称补种分工）；恢复出厂设置（FactoryResetService，经查 DatabaseCleaner 会清空语言标准表与扩展表）在数据重置与初始器重跑后由 `LanguagePresetSeeder` 重放同一 classpath 脚本恢复预置（脚本为唯一数据源，出厂相关测试同步），验证 SQL Server 既有库应用 03 幂等跳过、全新 SQLite 库启动后 22 语言 + 22 名称非空

## 2. 后端服务

- [x] 2.1 新增 `LanguageIdentificationExtensionRepository`（按 `languageId` 查找/批量）与 `LanguageExtensionService`（getOrDefault、mapByLanguageIds、upsert），验证新增单测通过
- [x] 2.2 `LanguageResponse` 名称字段语义改为仅取扩展表持久化值（缺失或为空返回 null，不回退 Locale）；`LanguageRequest` 增加可选 `languageName` / `territoryName`；`LanguageIdentificationService.toResponse()` 不做运行时解析；`create`/`update` 时 upsert 扩展行、留空存 null（不按 JDK Locale 回填），验证新增/更新的 Service 单测（含缺失返回 null 与留空存 null 分支）通过
- [x] 2.3 预置名称数据迁入 SQL 迁移 V20260919002（sqlserver/sqlite/dm 三方言，按 `language_identifier` 关联标准表幂等插入 22 条中文名称）；`LanguageInitializer` 仅播种语言代码，移除名称补种逻辑，验证迁移脚本在临时 SQLite 库实际执行通过

## 3. 前端

- [x] 3.1 `LanguageList.vue` 列名与表单项「文字」→「文字标识」、「地区」→「地区标识」；表单新增「语言名称」「地区名称」输入项，验证 vitest + `npm run build` + eslint 通过

## 4. 验证

- [x] 4.1 后端全量测试（`mvn test`，含既有 `LanguageIdentificationServiceTest` 回归）通过
- [x] 4.2 前端全量验证（vitest / build / eslint）通过
- [x] 4.3 启动真实后端 + 前端手工验收：语言页展示持久化名称；编辑保存后重查生效；启用开关即改即存；名称留空存 null 不回填（2026-09-19 用户确认，以 SQL Server 真实库 + 全新 SQLite 库 + 既有库副本三环境验证代替逐条点击）
- [x] 4.4 提交前 `bunx gitnexus@latest detect-changes --scope all --repo .` 无 partial/truncated；同步检查 README/ARCHITECTURE 是否受影响
