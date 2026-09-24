# 提案：语言标识扩展表（语言名称/地区名称持久化）

## Why

当前语言页的「语言名称」「地区名称」由后端在响应组装时用 JDK `Locale` API 实时计算（`LanguageDisplayNames`），数据库中无任何名称字段。这带来两个问题：名称不可编辑、不可审计，且无法按注册表自身需要扩展（如登记非 JDK 支持的语言的显示名称）。需要将名称持久化为独立的语言扩展表，与标准 MDR 语言表一对一关联。

## What Changes

- 新增 EXT 扩展表：team 库（SQL Server/达梦）为 `ext.language_identification`，与主表 `mdr.language_identification` **同名同构关系、仅以 schema 区分**；SQLite（无 schema）物理表名为 `ext_language_identification`。
- 扩展表与标准 `language_identification` 表**一对一**（`language_id` 唯一引用主表 `id`），列：`language_name`（语言名称）、`territory_name`（地区名称）。
- 语言接口（列表/详情）返回的名称仅取扩展表持久化值；扩展行缺失或名称为空时该名称返回空（null，前端展示为空），系统不对标识做运行时解析。
- 语言新建/编辑接口接受可选的 `languageName` / `territoryName`，保存时 upsert 扩展行；留空时保存为空（null），不按 JDK Locale 解析回填。
- 参考文档接口的语言名称/地区名称同样仅取扩展表持久化值（缺失或为空时返回 null）。
- 预置名称数据（内置 G20 二十二种语言的中文名称）仅存在于 SQL 迁移脚本 V20260919002/V20260919003（sqlserver/sqlite/dm 三方言）：02 按 `language_identifier` 关联既有库补种名称；03 以固定 UUID 同时播种语言代码与名称，面向全新库与恢复出厂场景；应用代码（含初始化器与出厂流程）不包含名称预置数据，出厂流程仅重放脚本文件。
- 启用 Flyway：`spring.flyway.enabled` 默认 `true`，`baseline-on-migrate` + `baseline-version: "20260918002"`（既有最大版本号），并开启 `spring.jpa.defer-datasource-initialization` 使 ddl-auto 先于 Flyway 执行——既有库首次启用 Flyway 时基线化、只应用两个带幂等守卫的新迁移；全新库同样经基线路径获得预置名称，两环境开箱即用。
- UI 语言页文案修改：列与表单项「文字」改为「文字标识」，「地区」改为「地区标识」；表单新增「语言名称」「地区名称」可编辑项。

## Capabilities

### New Capabilities

（无——本变更修改既有能力，不引入新能力域。）

### Modified Capabilities

- `language-identification-edit`: 语言名称与地区名称的来源从"运行时 JDK Locale 计算"改为"仅取 EXT 扩展表持久化值，缺失或为空时展示为空（返回 null），不做运行时解析"；语言编辑表单新增语言名称/地区名称字段（留空保存为空）；列表与表单文案「文字」→「文字标识」、「地区」→「地区标识」；内置语言名称预置数据从初始化器迁入 SQL 迁移脚本 V20260919002。

## Impact

- 后端：`metadata-registry-ext`（新实体 `LanguageIdentificationExtension`）、`metadata-registry-web`（Repository/Service、`LanguageIdentificationService`、`LanguageRequest/Response`、`LanguageInitializer`、`DatabaseEnvironment.EXT_TABLES`、`orm-team.xml`、新增 `orm-sqlite.xml`、Flyway 三方言迁移、`OrmTeamXmlTest`）。
- 前端：`metadata-registry-ui/src/views/language/LanguageList.vue`（列名、表单项、新增名称输入）。
- 数据库：新增 EXT schema 表（同名 `language_identification` / SQLite `ext_language_identification`）及预置名称数据迁移 V20260919002（三方言，按 `language_identifier` 关联标准表幂等插入）；启用 Flyway 并基线化到 20260918002（`defer-datasource-initialization` 保证 ddl-auto 先于 Flyway，全新库与既有库同一路径）。
- API：语言 create/update 请求体新增两个可选字段；响应字段语义变更（持久化优先）。
