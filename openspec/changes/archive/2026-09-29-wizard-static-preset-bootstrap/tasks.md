# Tasks: wizard-static-preset-bootstrap

## 1. 后端：统一静态预置入口

- [x] 1.1 新增 `seed/StaticDataPresetService.java`：`presetAll()` 按序执行 重放预置 SQL 脚本清单 → 系统组织与 `sys:` 系统默认命名空间（JPA 幂等装配，逻辑迁入）→ `identifier_strategy_config` 全局默认行（SQL 幂等插入）→ 占位超级管理员（账户表非空即跳过）→ 每 RA 标准注册流转规则调和；全部步骤幂等；入口完成后显式触发 `IdentifierStrategyLoader.refreshRegistry()` 或保证执行顺序先于 `@Order(90)` 装载
- [x] 1.2 新增预置重放脚本资源 `db/preset/{sqlite,sqlserver,dm}/localize-language-names.sql`（zh-CN 行 `language_name` 更新为「简体中文」，幂等守卫），并把预置重放清单从单一 `V20260919003` 扩展为「`V20260919003` + 名称口径修正」，复用 `LanguagePresetSeeder` 的方言目录切换与语句拆分机制（抽取为通用 Runner 或扩展清单配置）
- [x] 1.3 `identifier_strategy_config` 全局默认行（`UUID`/`is_global_default=1`）以 `StaticDataPresetService` 内 JdbcTemplate 幂等插入实现（`qualifyAuto` 路由 EXT schema；列集合按当前实体为准——`identifier_type`/`sequence_scope` 已于 V20260727001 移除），三方言行为由同一 Java 步骤保证，sqlite 内存库集成测试验证
- [x] 1.4 新增启动期 `ApplicationRunner`（`@Order` 满足 1.1 顺序约束）调用 `presetAll()`
- [x] 1.5 删除 `seed/LanguageInitializer.java`（22 条语言代码硬编码双数据源）；排查并替换其全部引用（向导 setup、出厂、`SystemDefaultNamespaceInitializer.createLanguage` 兜底改查存在性）
- [x] 1.6 删除 `seed/DataInitializer.java`，占位管理员逻辑并入 `presetAll()` 第 4 步
- [x] 1.7 `FactoryResetService` 清空后改调 `presetAll()`（替代 languageInitializer + systemDefaultNamespace + reapplyPreset 三段式），`reseedAdminUser()` 保持在其后执行（或改为 upsert）
- [x] 1.8 `ContextTestDataInitializer` 改由 `app.seed.demo-data.enabled` 控制（默认 false），原 `app.seed.enabled` 语义收敛
- [x] 1.9 `SystemDefaultNamespaceInitializer` 保留 JPA 装配逻辑但由统一入口编排（向导/出厂经 `presetAll()` 触发，不再各自直调）

## 2. 后端：初始化向导职责边界

- [x] 2.1 `InitWizardService.setup()` 移除 `languageInitializer.initializeLanguages()` 与 `languagePresetSeeder.reapplyPreset()` 调用（含 2026-09-29 止血补丁），静态预置由统一入口负责
- [x] 2.2 `clearBusinessDataOnly()` 保留集合增加 `language_identification`、`ext_language_identification`
- [x] 2.3 `setup()` 在清理完成后调用 `presetAll()` 幂等兜底，再执行用户数据写入（RA / Registrar / 文档语言关联 / 命名空间 / 注册规范）
- [x] 2.4 文档语言关联逻辑保持：从 `languageRepository` 按 BCP 47 标识符查既有行（预置已保证存在），未命中才新建

## 3. 后端：迁移

- [x] 3.1 新增三方言迁移 `V20260929001__Localize_language_names.sql`：UPDATE `ext_language_identification` 中 zh-CN 行 `language_name` → N'简体中文'（WHERE 命中才更新，幂等；sqlite / sqlserver / dm 语法与 schema 限定按各方言约定）

## 4. 前端：语言中文显示

- [x] 4.1 `stores/dict.ts` `LanguageDict` 补 `languageName`、`territoryName` 字段
- [x] 4.2 `InitWizard.vue`：删除硬编码语言数组（`:669-674`），文档语言选项改拉 `/api/v1/languages`，`label = languageName || languageIdentifier`，预填 `zh-CN`，中文名参与搜索
- [x] 4.3 `MultilingualTextEditor.vue:81`、`DesignationEditor.vue:22`、`DefinitionEditor.vue:22`：语言下拉 label 改 `languageName || languageIdentifier`，补搜索过滤
- [x] 4.4 `RegistrySpecificationSettings.vue:79,95`（默认语言 / 备选语言）同上
- [x] 4.5 `ItemFormDialog.vue` 语言展示优先中文名（缺省回退标识符）
- [x] 4.6 `LanguageList.vue:47,50` placeholder 改为与"不做运行时解析"一致的说明（留空显示语言标识符，无自动回填）

## 5. 测试

- [x] 5.1 新增 `StaticDataPresetServiceTest`：幂等（连续两次结果不变）、步骤顺序、占位管理员跳过语义、全局默认策略三方言 SQL 生效
- [x] 5.2 `InitWizardServiceIntegrationTest` 适配（setup 不再写语言预置、清理保留两张语言表、setup 后静态数据完整）；既有 6 个既有红与本次改动无关，保持隔离
- [x] 5.3 `FactoryResetServiceTest` 适配统一入口；断言出厂后静态集合完整（含全局默认策略行）
- [x] 5.4 `LanguagePresetSeederTest` 适配脚本清单扩展（双脚本顺序、拆分正确性）
- [x] 5.5 前端：相关组件 vitest 适配中文 label 与回退；`vue-tsc` 通过
- [x] 5.6 后端全量测试通过；前端 build 通过

## 6. 验证与收尾

- [x] 6.1 三方言验证：SQLite 内存库全向导集成测试通过（静态预置 + setup 兜底语义）；SQL Server scratch 库验证 V20260929001 迁移机制（zh-CN → 简体中文，已清理）；dm 脚本按 sqlserver 镜像核对（本机无达梦实例，沿用"达梦待实测"先例）
- [x] 6.2 `detect_changes --scope all` 无 partial / truncated
- [x] 6.3 `openspec validate wizard-static-preset-bootstrap` 通过
- [x] 6.4 `.superpowers-memory` 更新（CURRENT_STATE + session journal）；AGENTS.md 文档同步规则检查（README seed 描述仍准确，历史设计文档保留原状）

## 评审修正（2026-09-29 评审轮，详见 session journal）

- [x] R1（BLOCKER·前端）`/api/v1/languages` 加入未初始化系统匿名白名单：`SecurityConfig.java` 仅 GET 精确路径 permitAll；`InitializationWizardInterceptor.java` 同步放行 GET——修复向导无账号态下拉 401/499 导致预填显示原始标签的缺陷
- [x] R2（MAJOR·后端）`abort()` 清理后补 `presetAll()`，消除"语言预置保留、组织/sys: 命名空间/全局策略被清"的静态预置半缺失缺口
- [x] R3（MAJOR·后端）`SystemDefaultNamespaceInitializer` 移除 catch-all 吞异常，失败向上抛出由调用方事务回滚（fail-fast 与统一入口其余步骤一致）
- [x] R4（前端）下拉 label 统一为「中文名（BCP 47 标识符）」组合格式，标识符可见
- [x] R5（前端）向导文档语言去掉 `allow-create`/`default-first-option`（防中文输入污染语言字典）；向导"注册规范默认语言"下拉 API 化；请求带 `enabled: true`
- [x] R6（前端）`ItemFormDialog` AI 草稿与 languageCodeById 恢复发送 BCP 47 标识符（回程按标识符解析），并新增回归用例钉住该契约
- [x] R7（后端·卫生）`splitStatements` 改 `\R` 切行并去 `\r`；`FactoryResetService` 移除未使用的 `systemSettingService`；`InitWizardService` 过期 `{@link LanguageInitializer}` 改 `{@code}`；全局默认策略 INSERT 增加并发唯一冲突兜底
- [ ] R8（遗留·非本变更）`getWizardState()` 把 `sys:` 命名空间计入 `nsCount` 致 READY 不可达（既有缺陷）；`MigrationService` 导入路径未接 `presetAll()`（既有缺口，重启自愈）——登记 backlog，不在本变更处理
