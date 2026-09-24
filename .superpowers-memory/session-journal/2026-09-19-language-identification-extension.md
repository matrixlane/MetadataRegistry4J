# 2026-09-19 语言标识扩展表（add-language-identification-extension）

## 做了什么

- 需求：语言页「语言名称」「地区名称」从 JDK Locale 运行时计算迁移到独立 EXT 扩展表（1:1，可编辑，防硬编码）；UI 文案「文字」→「文字标识」、「地区」→「地区标识」。
- 走 superpowers-openspec-superpowers-workflow：设计经用户确认（扩展表与主表**同名** `language_identification`，schema 区分；SQLite 无 schema 故物理名 `ext_language_identification`）。产物：`docs/superpowers/specs/2026-09-19-language-identification-extension-design.md`、OpenSpec 变更 `add-language-identification-extension`（已 validate 通过）、计划 `docs/superpowers/plans/2026-09-19-language-identification-extension.md`。
- 实现：新卫星实体 `LanguageIdentificationExtension`（仿 LanguageGovernance）；Flyway V20260919001（sqlserver/dm 建 `ext.language_identification`，sqlite 建 `ext_language_identification`）；`EXT_TABLES` 双路由键；`orm-team.xml` + 新增 `orm-sqlite.xml`；`LanguageExtensionService`；`LanguageIdentificationService` 持久化优先 + Locale 回退 + 留空回填；`LanguageInitializer` 22 条中文名称补种；`LanguageList.vue` 文案 + 表单新增名称输入。
- 验证：后端 1649 tests 0 failures；前端 vitest 185 全过、build/lint 通过；真实启动（临时 SQLite）冒烟：补种 22 条、持久化值优先、留空回填均确认；detect-changes 无 partial/truncated。

## 状态

- **待用户浏览器人工验收（任务 4.3）**，随后 continue-review 代码评审、continue-archive 归档。
- 既有 spec 漂移记录：`language-identification-edit` 规定页面无新建/删除按钮且列表应有启用开关，当前 UI 有新增/删除、无开关——需单独变更对齐。

## 重要发现（跟进）

1. **`spring.jpa.mapping-resources` 不能用 `classpath:` 前缀**（Hibernate 6.6.11 只剥 `classpath://`）：`application-team.yml` 既有 `classpath:orm/orm-team.xml` 有隐患，team profile（SQL Server/达梦）启动需验证，必要时改裸路径 `orm/orm-team.xml`。新入口已全部用裸路径。
2. 7 个既有集成测试需显式指定 `orm/orm-sqlite.xml`（原置空 mapping-resources 会导致同名表映射冲突）。
3. sqlserver/dm 迁移仅静态审查，未在真实实例跑 Flyway。

## 代码评审与修复（当日续）

- 只读评审结论：无 BLOCKER；M1（语言页无启用开关、spec 禁新建/删除 vs UI 实际有增删——既有漂移）、M2（EXT_TABLES 裸键 `language_identification` 劫持导入 FK 路由）；m1 upsert 竞态、m2 EXT 表跨库迁移不 round-trip（既有共同缺口）、m3 PUT 留空覆盖自定义名称（spec 内设计决策）；n1 计数注释漂移。
- 用户裁定 M1：语言页**允许**新建/删除 → 修改 spec 措辞对齐（非改 UI）；启用开关列缺失记为既有遗留。
- 已修复：M2（新增 `DatabaseEnvironment.qualifyStandard` + `DatabaseCleaner` 显式 schema 重载，MigrationService 导入 FK 停/启走主 schema；加警示 javadoc + 2 个锁定测试）；n1 计数注释；spec 措辞。后端全量 1157 tests 0 failures；openspec validate --strict valid。
- 仍待：任务 4.3 浏览器人工验收 + SQL Server 库生效（重建 jar、team profile 重启），随后 continue-archive。`application-team.yml` mapping-resources 已改裸路径。
- 追加（当日续 2）：用户裁定 M1 本次一并修——`LanguageList.vue` 新增「是否启用」el-switch 列（model-value 单向绑定 + @change 即改即存、成功刷新/失败回滚、防重入 loading；`enabled !== false` 处理治理缺失行默认启用；请求体显式映射 territoryName 防误覆盖持久化名称）；spec 措辞改回"可直接操作的启用开关"并补切换 scenario。新建/删除按裁定保留。前端 vitest 190 全过、build/lint 通过；openspec validate --strict valid。
- 部署实录（当日续 3）：用户反馈 SQL Server 无新表。排查：运行的 jar（07:33 打包）为旧构建不含新类（前端显示名称靠 Locale 兜底）；另外 ~/.metadata-registry/application-local.yml 的 `mapping-resources: classpath:orm/orm-team.xml` 有前缀 bug，已改裸路径。
- **新坑（已修）**：orm XML 中 `<table schema="ext"/>` 不带 name 会**整体覆盖**注解 @Table，未指定的 name 回退为隐式命名 → 实体被建成 `ext.language_identification_extension`。修复：orm-team.xml 该实体条目补 `name="language_identification"`。其余 27 实体因隐式名与注解名相同从未暴露此问题。OrmTeamXmlTest 的 EXPECTED 已是 language_identification，修复后一致。
- 已重建 jar（含 -Dfile.encoding=UTF-8 启动，修中文乱码种子）、删错表、重启，SQL Server 现已有 ext.language_identification + 22 条中文名称。
- 追加（当日续 4）：用户裁定——名称为动态数据，代码 MUST NOT 包含名称数据来源/预置。已删除 `LanguageDisplayNames`（Locale 兜底+回填整体移除，含 ReferenceDocumentService 改用 LanguageExtensionService 持久化值、缺失 null）；LanguageInitializer 移除 22 条中文名称补种（保留代码播种）；新增 V20260919002 三方言幂等 SQL 预置（NOT EXISTS 守卫，按 language_identifier 关联）。web 1175 tests 0 failures；openspec validate --strict valid；spec/proposal 同步。
- 已知取舍：全新库首次启动 Flyway 先于 LanguageInitializer，V20260919002 关联不到语言行 → 预置名称缺省（已在脚本注释/测试记录）。如需全新库开箱即有名称，需另行裁定（如语言代码播种也迁 SQL 定 UUID）。
- 部署注意：现有环境 FLYWAY_ENABLED=false，V20260919002 不会自动执行；SQL Server 库已有 22 行（旧初始化器所种）不受影响。
- 追加（当日续 5）：用户裁定(1)全新库开箱即有名称 (2)开启 Flyway。落地：flyway.enabled 默认 true + baseline-version 20260918002（既有库基线化只跑 001/002/003）+ defer-datasource-initialization（ddl-auto 先、Flyway 后）+ FlywayAfterJpaConfig（解 Boot3.5 循环 depends-on）+ flyway.schemas=mdr,ext（否则误判空库重放老迁移，dbo 残留已清理）+ hikari auto-commit:false 补进 application.yml。
- V20260919003（三方言，44 条幂等 INSERT，22 固定 UUID 语言代码+名称）= 全新库/出厂一体化播种；V20260919002 不动（既有库名称补种）。LanguagePresetSeeder 复用同一份 003 脚本资源在恢复出厂后重放（FactoryReset 实测会清空 ext/标准表）。预置数据 100% SQL 化，代码零名称数据。
- 验证：SQL Server history 4 行/22 行不变；全新 SQLite 库 22+22 行非空（核心验收）；personal 副本 22+22；全量测试 BUILD SUCCESS；validate --strict valid。
- 遗留：全新 personal 空库全量重放 V20260727001 失败（既有，SQLite DROP COLUMN IF EXISTS）；达梦未实测。
- 归档（当日终）：`openspec archive` 完成 → `openspec/changes/archive/2026-09-19-add-language-identification-extension`；主 spec `language-identification-edit` 合并 3 ADDED + 1 MODIFIED（现 7 条 requirement）。任务 4.3 经用户确认以三环境真实验证勾收。变更全程未 git commit（工作区与其他并行变更共存，提交需用户分组决策）。
