# Proposal: wizard-static-preset-bootstrap

## Why

2026-09-29 实测缺陷：SQL Server 库 `mdr.language_identification` 22 行、`ext.language_identification` 0 行（语言名称/地区名称全空），前端语言页两列、以及所有语言下拉全部没有中文名称。根因是初始化职责没有划清：

1. 静态预置数据散落 8 个载体（Flyway 脚本、`LanguagePresetSeeder`、`LanguageInitializer`、`SystemDefaultNamespaceInitializer`、`DataInitializer`、`ContextTestDataInitializer`、`RegistrationProcessSeedInitializer`、`RegistryContextBootstrapService`），其中语言代码同时存在 SQL 脚本与 Java 硬编码两份数据源，违反 2026-09-19 用户裁定"预置数据 MUST 仅存在于 SQL 脚本"。
2. 初始化向导第 2 步"数据库"保存配置后要求重启，建表与预置实际发生在重启后的启动期；而第 5 步 `setup()` 的 `clearBusinessDataOnly()` 把除 3 张保留表外的全部 113 张业务表清空（含语言两表），随后只补回语言代码，名称预置因 Flyway 不重跑已应用迁移而永久缺失。
3. 出厂流程与向导 setup 的静态预置序列顺序不一致，且都缺 `identifier_strategy_config` 全局默认行（该行仅 sqlserver 方言 `V20260527001` 种过一次，出厂后 SQL Server 丢失全局默认策略、自动生成标识退回 UUID；sqlite/dm 全新库从未有过）。
4. 前端所有语言下拉（向导文档语言、指称/定义编辑器、多语言编辑器、注册规范设置、条目表单）一律显示 BCP 47 标识符；向导文档语言选项甚至是前端硬编码字符串数组（`InitWizard.vue:669-674`），完全不读 API。

用户诉求（2026-09-29 确认按推荐方案实现）：向导"数据库设置"完成后立即预置全部静态数据，之后的步骤只处理需要用户输入的数据；所有语言下拉显示中文语言名称；当前库不做任何临时修复。

## What Changes

- **统一静态预置入口**：新增 `StaticDataPresetService.presetAll()`（幂等），步骤：重放语言预置 SQL（复用 `LanguagePresetSeeder` 方言切换与语句拆分机制，脚本清单扩展为「V20260919003 + 新增名称口径修正语句」）→ 系统组织 + `sys:` 系统默认命名空间（JPA 幂等装配）→ `identifier_strategy_config` 全局默认行（SQL 幂等插入，三方言补齐）→ 占位超级管理员（账户表非空即跳过）→ 每 RA 标准注册流转规则调和（保留既有调和语义）。数据值以 SQL 脚本为唯一数据源，Java 仅执行。
- **统一执行时机**：新增启动期 `ApplicationRunner`（Flyway 之后）调用 `presetAll()`；`FactoryResetService` 清空后改调 `presetAll()`（替代现有三段式）；初始化向导 `setup()` 移除语言初始化与预置重放调用（含 2026-09-29 止血补丁），清理用户数据后调用 `presetAll()` 幂等兜底。
- **向导清理范围收窄**：`clearBusinessDataOnly()` 的保留表集合增加纯静态预置表 `language_identification` / `ext_language_identification`（其余混合表仍全清，由 `presetAll()` 兜底恢复静态行）。
- **移除重复数据源**：删除 `LanguageInitializer` 组件（22 条语言代码硬编码列表），语言代码仅以 `V20260919003` 脚本为源；`SystemDefaultNamespaceInitializer` 保留 JPA 装配但不再自建 Runner 语义，改由统一入口编排（向导/出厂经统一入口触发）。
- **占位管理员归位**：`DataInitializer` 逻辑并入 `presetAll()`（账户表非空即跳过），原组件删除。
- **演示数据隔离**：`ContextTestDataInitializer` 改由独立配置 `app.seed.demo-data.enabled` 控制（默认 `false`），不再默认随启动灌入 8 条演示上下文。
- **zh-CN 名称口径**：新增三方言 Flyway 迁移 `V20260929001__Localize_language_names.sql`，把 `ext_language_identification` 中 zh-CN 行 `language_name` 由「中文」改为「简体中文」（BCP 47 zh-CN 隐含脚本 Hans；用户裁定）；名称口径修正语句同步加入预置重放脚本清单，保证恢复出厂/向导兜底后仍是「简体中文」。同步修正 `V20260919003` 之外的既有 `V20260919002` 仅面向历史库、本次不改。
- **前端语言中文显示**：所有语言下拉 `label = languageName || languageIdentifier`（`value` 仍为 BCP 47 标识符，提交格式不变），中文名参与搜索过滤；初始化向导「文档语言」选项改由 `GET /api/v1/languages` 驱动（删除硬编码数组），预填仍为 `zh-CN`、显示「简体中文」；`stores/dict.ts` `LanguageDict` 补 `languageName`/`territoryName`；`LanguageList.vue` 编辑弹窗 placeholder 去掉"留空则按当前界面语言自动回填"的过时说明（后端不做运行时解析）。
- **不做**：Registry Context 仍在向导 commit 后创建（属用户配置后的派生数据，非静态预置）；不改任何 API 契约；不做运行期数据源动态切换；不修复当前数据库实例。

## Capabilities

### New Capabilities

（无。）

### Modified Capabilities

- `system-initialization`：新增"静态预置数据的统一定义、时机与完整性"需求（数据库设置完成即预置全部静态数据；三处执行时机复用同一幂等入口；向导 setup 不再写静态预置且清理后静态数据保持完整；演示数据独立开关默认关闭）；既有"幂等与可恢复语义"需求补充静态预置表保留语义。
- `language-identification-edit`：新增"语言名称中文显示口径"需求（zh-CN=简体中文；下拉 label 回退规则；向导文档语言选项 API 驱动）。

## Impact

- `metadata-registry-web`：
  - 新增 `seed/StaticDataPresetService.java`、启动 Runner；改造 `FactoryResetService`、`InitWizardService`；删除 `LanguageInitializer`、`DataInitializer`（逻辑并入统一入口）；改造 `ContextTestDataInitializer`（独立开关）。
  - 新增三方言迁移 `V20260929001__Localize_language_names.sql`；`identifier_strategy_config` 全局默认行以预置 SQL 补入三方言（幂等守卫）。
  - 新增预置重放脚本资源（名称口径修正语句，三方言）。
  - 测试：`StaticDataPresetServiceTest`（幂等/顺序/三步骤）、既有 `InitWizardServiceIntegrationTest`、`FactoryResetServiceTest`、`LanguagePresetSeederTest` 适配。
- `metadata-registry-ui`：`InitWizard.vue`（文档语言选项 API 化）、`MultilingualTextEditor.vue`、`DesignationEditor.vue`、`DefinitionEditor.vue`、`RegistrySpecificationSettings.vue`、`ItemFormDialog.vue`（下拉中文 label）、`stores/dict.ts`（字段补齐）、`LanguageList.vue`（placeholder 文案）；相关 vitest 适配。
- 多方言：sqlite / sqlserver / dm 三份脚本语义一致；迁移 + 预置重放双通道（历史库走迁移，出厂/向导兜底走重放）。
- 兼容性：向后兼容；`GET /api/v1/languages` 响应已含名称字段，无契约变更。既有库应用 `V20260929001` 后 zh-CN 显示「简体中文」。
- 风险：向导清理保留集收窄需测试断言防止静态表被误清；统一入口执行顺序须先于 `IdentifierStrategyLoader`（`@Order(90)`）完成或显式刷新注册表。
