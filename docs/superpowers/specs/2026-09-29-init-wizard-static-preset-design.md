# 初始化向导静态预置重构与语言中文显示 — 设计稿

- 日期：2026-09-29
- 状态：待用户确认（Superpowers 设计阶段，未进入 OpenSpec）
- 关联变更（建议）：`wizard-static-preset-bootstrap`
- 影响的主 spec：`system-initialization`、`language-identification-edit`

## 1. 问题陈述

用户诉求两条：

1. **职责划分**：初始化向导在"数据库设置"完成后，就应立即把所有**静态预置数据**准备好；此后才轮到需要用户输入的数据（注册机构、命名空间、注册规范、管理员密码等）。
2. **显示效果**：所有语言相关的下拉菜单都要显示中文语言名称，初始化向导的"文档语言"预填项应显示"简体中文"而不是 `zh-CN`。

用户明确：**当前库不做任何临时修复**，只从机制上根治。

### 1.1 触发本设计的直接缺陷

2026-09-29 实测：SQL Server 库 `localhost:1433/metadata_registry` 中

```
mdr.language_identification   22 行   （语言代码）
ext.language_identification    0 行   （语言名称 / 地区名称，全空）
```

根因链（`logs/metadata-registry.log` 实证）：

```
00:31:38.77  LanguagePresetSeeder 已重新应用语言预置脚本…（44 条语句）  ← 恢复出厂：名称已写入
00:31:38.77  FactoryResetService 恢复出厂设置完成
00:32:02.03  POST /api/v1/init-wizard/setup
00:32:02.06  DatabaseCleaner: clearing 113 tables on SQLSERVER (preserving [system_setting, flyway…])
00:32:02.29  LanguageInitializer 已新增内置语言标识: zh-CN …（仅 22 条语言代码）
```

`InitWizardService.clearBusinessDataOnly()` 传给 `DatabaseCleaner.clearTables(...)` 的是**保留表清单**（`DatabaseCleaner.java:58`），除 3 张表外全部清空，语言两表都在内；随后只由 `LanguageInitializer` 补回语言代码，而名称预置只存在于 Flyway 脚本 `V20260919003`、Flyway 不重跑已应用迁移 —— 于是名称永久为空。

本次会话已对向导做了临时打补丁（`InitWizardService.java:269` 调 `languagePresetSeeder.reapplyPreset()`）。**该补丁只是止血，不满足本设计的职责划分诉求，且在"向导不负责静态预置"的新模型下应当被撤销。**

## 2. 现状盘点（证据）

### 2.1 静态预置数据当前散落在 6 个地方

| # | 载体 | 位置 | 写什么 | 触发时机 |
|---|---|---|---|---|
| 1 | Flyway 脚本 `V20260919002/03` | `db/migration/{sqlite,sqlserver,dm}/` | 语言代码 22 + 中文名称/地区名 22 | 全新库/迁移 |
| 2 | `LanguagePresetSeeder` | `seed/LanguagePresetSeeder.java:44` | 重放 `V20260919003`（按方言取文件） | 出厂、向导（本次补丁） |
| 3 | `LanguageInitializer` | `seed/LanguageInitializer.java:29` | 语言代码 22（Java 硬编码，与脚本重复） | 每次启动 `@Order(1)` |
| 4 | `SystemDefaultNamespaceInitializer` | `seed/SystemDefaultNamespaceInitializer.java:65` | 系统组织 + `sys:` 命名空间 + 中英双名（Java 硬编码中文） | 每次启动 + 向导 setup + 出厂 |
| 5 | `DataInitializer` | `seed/DataInitializer.java:25` | 占位超级管理员 `admin/NO_PASSWORD` | 每次启动（表非空即跳过） |
| 6 | `ContextTestDataInitializer` | `seed/ContextTestDataInitializer.java:50` | 8 条中文演示上下文 | 每次启动（context 表空即灌） |
| 7 | `RegistrationProcessSeedInitializer` | `seed/RegistrationProcessSeedInitializer.java:33` | 每 RA 20 条标准流转规则（调和式） | 每次启动 |
| 8 | `RegistryContextBootstrapService` | `service/RegistryContextBootstrapService.java:84` | Registry Context | commit 后 + 启动补建 |

结论：**同一份静态数据有两个数据源**（SQL 脚本 vs Java 硬编码），违反 2026-09-19 用户裁定"预置数据 MUST 仅存在于 SQL 脚本"；且出厂流程与向导 setup 的预置序列**顺序不一致、都缺 `identifier_strategy_config` 全局默认行**。

### 2.2 "数据库设置"这一步当前不碰数据库

向导第 2 步只有三个动作：读配置、测连接、保存配置。保存后 `restartRequired = true` 并阻断流程（`InitWizard.vue:894`、`:1038`），建表与预置全部发生在**重启后的启动期**：Flyway 建表 → `ApplicationRunner` 播种。

### 2.3 前端语言下拉全线不显示名称

| 位置 | 现状 |
| --- | --- |
| 向导「文档语言」 | `InitWizard.vue:669-674` **硬编码 22 个 BCP 47 字符串数组**，`InitWizard.vue:180` `:label="opt" :value="opt"`，完全不调 API |
| `MultilingualTextEditor.vue:81` | `:label="lang.languageIdentifier"` |
| `DesignationEditor.vue:22`、`DefinitionEditor.vue:22` | 同上 |
| `RegistrySpecificationSettings.vue:79,95` | 同上（默认语言 / 备选语言） |
| `ItemFormDialog.vue:661,931` | 显示语言码 |
| `stores/dict.ts:6` | `LanguageDict` 无 `languageName` 字段 |
| `LanguageList.vue:16` | 唯一用到名称处（即用户看到空列的页面） |

后端 `GET /api/v1/languages` 已返回 `languageName` / `geopoliticalTerritoryName`（`LanguageIdentificationService.java:117`，不做运行时 Locale 解析）——**后端就绪，前端未接线**。

## 3. 方案比较（预置时机）

### 方案 A：启动期统一预置 + 单一入口复用（推荐）

把全部静态预置收敛为**一个入口** `StaticDataPresetService.presetAll()`（幂等），由三方复用：启动期（Flyway 之后）、向导"数据库设置完成"（即重启后启动期）、恢复出厂后。向导第 3~6 步只写用户数据。

- 优点：与现有 Flyway + ApplicationRunner 架构一致；不引入运行期数据源切换；跨方言只需维护 SQL 脚本；幂等可反复执行。
- 代价：重启仍是必经环节（但这本来就是既有流程，向导本来就要求重启）。

### 方案 B：向导第 2 步内动态切换数据源并当场建表 + 预置

运行期新建连接、独立执行迁移与预置，不重启。

- 优点：体验上"设置完立即可用"。
- 代价：Spring 运行期换 DataSource、需自带迁移执行器与事务边界，跨三方言复杂度与风险显著升高；且向导进程此时仍连旧库，失败回滚路径复杂。**不推荐。**

### 方案 C：向导第 2 步保存后，由前端轮询一个新端点触发预置

本质是 A 的变体（重启仍发生），只是把启动期预置改为按需触发。

- 优点：预置时机更显式。
- 代价：多一个端点与前端状态机；预置失败需要额外的重试入口。收益有限。

**推荐方案 A。**

## 4. 设计（方案 A）

### 4.1 静态预置清单（"所有静态预置数据"的定义）

| 静态数据 | 现状 | 目标形态 |
| --- | --- | --- |
| 语言代码 22 条（G20） | SQL 脚本 + `LanguageInitializer` 双份 | **仅保留 SQL 脚本**；Java 侧删除硬编码列表 |
| 语言中文名称 / 地区名称 22 条 | `V20260919003` | 保留脚本；新增迁移统一 zh-CN 口径（见 4.4） |
| 系统组织 + `sys:` 系统默认命名空间（含中英双名） | `SystemDefaultNamespaceInitializer`（Java 硬编码中文） | 迁入 SQL 脚本，纳入统一预置；Java 侧仅保留"存在性检查 + 触发" |
| 全局默认标识策略 `identifier_strategy_config`（全局默认行） | **仅 sqlserver 有**（`V20260527001`）；sqlite/dm 缺 | 三方言补齐，纳入统一预置 |
| 占位超级管理员 `admin/NO_PASSWORD` | `DataInitializer` | 纳入统一预置（向导/出厂后的前置条件） |
| 每 RA 的 20 条标准注册流转规则 | 启动期调和式播种 | 纳入统一预置入口（保留"标准集调和"语义，不覆盖用户定制） |
| Registry Context | commit 后派生 | **保持不动**：依赖用户提交的 RA / 命名空间，属派生数据而非静态预置 |
| 8 条演示上下文 | `ContextTestDataInitializer`（默认开） | 明确隔离为**演示数据**，受独立开关控制，默认不随初始化灌入 |

### 4.2 统一入口与执行时机

新增 `StaticDataPresetService`（或等价命名的 seed 包组件）：

```
presetAll():
  1. 执行静态预置 SQL 脚本（按 vendor 取文件，复用 LanguagePresetSeeder 已有的方言切换与语句拆分机制）
  2. 系统默认组织 / 命名空间（幂等：存在即跳过）
  3. 全局默认标识策略（幂等）
  4. 占位超级管理员（表非空即跳过）
  5. 标准注册流转规则调和（按现有 RA 逐个 ensureSeed）
```

调用点收敛为三处，**顺序完全一致**：

- 启动期：新的 `StaticDataPresetRunner`（`@Order` 在 Flyway 之后、依赖数据装载之后）
- 恢复出厂：`FactoryResetService` 替换现有的三段式调用
- 初始化向导：**移除** `setup()` 中的 `languageInitializer.initializeLanguages()` 与 `languagePresetSeeder.reapplyPreset()`（含本次止血补丁），改由启动期入口负责

### 4.3 向导各步骤的职责边界（用户诉求 1 的落地）

| 步骤 | 名称 | 职责 |
| --- | --- | --- |
| 1 | 版本选择 | 用户输入 |
| 2 | **数据库** | 测试连接 → 保存数据源 → **重启** →（启动期）Flyway 建表 + `presetAll()` 静态预置全部就绪 |
| 3 | 注册机构 | 用户输入 |
| 4 | 命名空间 | 用户输入 |
| 5 | 注册规范 | 用户输入 |
| 6 | 管理员密码 | 用户输入；commit 后创建 Registry Context（派生数据） |

向导第 3~6 步**不再写任何静态预置数据**。

`setup()` 保留的清理语义：向导读到第 5 步时若发现上一次失败遗留的脏数据，需要清干净。当前 `clearBusinessDataOnly()` 是"全清"，会把步骤 2 预置好的静态数据一并抹掉 —— 这是本次缺陷的直接来源。目标：**清理范围收窄为用户可配置的业务数据表**（RA / Registrar / Namespace / 注册规范 / 相关关联表），静态预置表不在清理范围内；同时在清理之后调用一次幂等的 `presetAll()` 作为兜底（双重保险，代价为零）。

### 4.4 语言中文名称口径（用户诉求 2 的落地）

- 显示口径：语言下拉 `label = languageName || languageIdentifier`，`value` 仍为 BCP 47 标识符（保证提交格式不变）；中文名参与搜索过滤。
- zh-CN 口径：新增迁移脚本（三方言各一份）把 `ext_language_identification` 中 `zh-CN` 行的 `language_name` 由「中文」定为「**简体中文**」。
  - 依据：BCP 47 `zh-CN` 隐含脚本 `Hans`（脚本标识已在预置中为 `Hans`），简体中文是该标签在中文语境下的通行称谓；用户 2026-09-29 明确要求显示"简体中文"。
  - 已应用的 Flyway 迁移不可修改，故走新增版本脚本。
- 其余语言的 `language_name` 维持现值（英语 / 日语 / 法语 …）。
- 文案修正：`LanguageList.vue:47,50` 的 placeholder "留空则按当前界面语言自动回填"与后端"不做运行时解析"的裁定矛盾，改为说明"留空则列表显示为语言标识符"。

### 4.5 前端改造范围（用户诉求 2 的落地）

| 文件 | 改动 |
| --- | --- |
| `InitWizard.vue` | 删除硬编码语言数组，改为拉取 `/api/v1/languages`；文档语言选项 label 为中文名；预填仍为 `zh-CN`，显示"简体中文" |
| `MultilingualTextEditor.vue`、`DesignationEditor.vue`、`DefinitionEditor.vue` | 语言下拉 label 改为 `languageName \|\| languageIdentifier`，补中文名搜索 |
| `RegistrySpecificationSettings.vue` | 默认语言 / 备选语言两处同上 |
| `ItemFormDialog.vue` | 语言展示改为优先中文名 |
| `stores/dict.ts` | `LanguageDict` 补 `languageName` / `territoryName` 字段 |
| `LanguageList.vue` | placeholder 文案修正 |

## 5. 影响与风险

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| 向导 `setup()` 清理范围收窄 | 若静态预置表被误纳入清理名单，缺陷复现 | 清理表清单显式枚举并配测试断言 |
| 统一入口的顺序依赖 | 标识策略装载（`IdentifierStrategyLoader` `@Order(90)`）必须在预置之后 | 统一入口置于 `@Order(80)` 之前，或入口内完成后显式 `refreshRegistry()` |
| 移除 `LanguageInitializer` 硬编码 | 依赖它的 `InitWizardService` / `SystemDefaultNamespaceInitializer` 需改为存在性查询 | 保留组件但降级为"存在性检查 + 触发统一入口" |
| 三方言脚本同步 | 新增迁移脚本需 sqlite / sqlserver / dm 各一份 | 沿用现有三目录约定，测试覆盖方言选择 |
| 演示数据默认关闭 | 影响本地开发便利性 | 独立配置项 `app.seed.demo-data.enabled`，开发环境可开 |

## 6. 不在本次范围

- 不修复当前数据库实例（用户明确不做临时修复）。
- 不改变 `LanguageResponse` 等 API 契约（已具备返回名称的能力）。
- 不引入运行期数据源动态切换。
- 不重做 Registry Context 的创建时机。

## 7. 验证计划

1. **全新库三方言**：SQLite 内存库 + SQL Server 真实库各跑一次完整初始化向导，断言 `ext.language_identification` 22 行、`language_name` 含"简体中文"、向导文档语言显示中文名。
2. **幂等性**：`presetAll()` 连续执行两次，结果集不变。
3. **回归**：恢复出厂后静态数据完整（含 `identifier_strategy_config` 全局默认行）。
4. **既有测试**：`InitWizardServiceIntegrationTest`、`FactoryResetServiceTest`、`LanguagePresetSeederTest`、`LanguageIdentificationServiceTest` 全绿；前端 vitest 全绿；`vue-tsc` 通过。
5. **图谱**：`detect_changes --scope all` 无 partial / truncated。

## 8. 待用户确认的决策点

1. **预置时机**取方案 A（启动期统一预置，重启为必经环节）。
2. **zh-CN 显示口径**取「简体中文」（新增迁移脚本改写），而非「中文」或「中文（Hans）」组合显示。
3. **Registry Context** 归为用户配置后的派生数据，仍在 commit 后创建，不纳入静态预置。
4. **演示上下文**从"每次启动即灌"改为独立开关且默认关闭。
5. 建议的 OpenSpec 变更名：`wizard-static-preset-bootstrap`，规格 delta 落到 `system-initialization` 与 `language-identification-edit` 两个主 spec。
