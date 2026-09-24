# 设计：语言标识扩展表（语言名称/地区名称持久化）

详细设计已与用户在 `docs/superpowers/specs/2026-09-19-language-identification-extension-design.md` 中确认，本文件为其 OpenSpec 设计摘要（HOW）。

## Context

- 标准实体 `LanguageIdentification`（表 `language_identification`，ISO/IEC 11179-3:2023 6.3.7）只存 BCP 47 代码；名称目前由 `LanguageDisplayNames`（JDK Locale）在 `LanguageIdentificationService.toResponse()` 中实时计算。
- EXT schema 机制已成熟（`2026-09-01-mdr-ext-schema-split`）：实体注解不带 schema；team profile 用 `orm/orm-team.xml` 覆盖 `schema="ext"`；`DatabaseEnvironment.EXT_TABLES` 做裸表名 → schema 路由；Flyway 按方言分目录；`OrmTeamXmlTest` 守卫清单。
- 卫星实体先例 `LanguageGovernance`：UUID 主键 + `language_id` 唯一值引用，不建 JPA 关联，读写经值键，缺失行有默认语义。

## Goals / Non-Goals

- Goals：名称持久化、可编辑、一对一、扩展表与主表同名（schema 区分）；UI 文案修正。
- Non-Goals：多语言名称（1:N，未来再加 `display_locale` 列）；改动标准表或 `language_governance`；标准表旧数据迁移（扩展表为新增卫星表，无标准数据迁移）。

## Decisions

1. **扩展表与主表同名，schema 区分**（用户决策）：`ext.language_identification` ↔ `mdr.language_identification`，强调紧密关系。
   - SQLite 无 schema，同名会在 `main` 命名空间撞名 → personal profile 新增 `orm/orm-sqlite.xml`，对该实体覆盖 `<table name="ext_language_identification"/>`；`EXT_TABLES` 同时登记 `language_identification` 与 `ext_language_identification` 两个路由键。
   - 备选（否决）：命名 `language_extension`/`language_governance` 式独立名——用户明确要求同名。
2. **卫星模式一对一**：`LanguageIdentificationExtension(id, language_id unique, language_name, territory_name)`，仿 `LanguageGovernance` 不建 JPA 关联。备选 `@OneToOne` 关联——否决，避免加载标准实体对象图。
3. **名称仅来自扩展表（用户裁定 2026-09-19）**：扩展行缺失/名称为空 → 响应返回 null（前端展示为空），MUST NOT 对标识做运行时解析（已删除 `LanguageDisplayNames`）。
4. **留空保存为空（用户裁定 2026-09-19）**：create/update 时 `languageName/territoryName` 为空白/null → 存 null，不按 JDK Locale 回填。
5. **种子**：名称预置数据 MUST 仅存在于 SQL 迁移脚本；`LanguageInitializer` 只播种语言代码，不包含名称数据。参考文档接口的语言名称经 `LanguageExtensionService` 取持久化值。
6. **一体化预置（用户裁定 2026-09-19 追加）**：V20260919003 以固定 UUID 同时播种 22 条语言代码与名称，保证全新库开箱即有；恢复出厂设置清空语言表后由出厂流程重放同一脚本（脚本文件为唯一数据源）；V20260919002 保持不动（既有库 flyway_schema_history 已标记应用），继续承担既有库名称补种。

## Risks / Trade-offs

- [SQLite 物理表名与 team 不一致，跨库 SQL/文档易混] → `DatabaseEnvironment.EXT_TABLES` 双路由键 + 迁移脚本按方言分目录已有机制兜住；设计中显式记录。
- [同名表在通用 SQL 工具/日志中需带 schema 限定才能区分] → 团队库查询统一走应用层；`MetadataSearchService`/`DatabaseCleaner` 已按 `EXT_TABLES` 路由，无需人工区分。
- [Flyway 需显式指定 schemas] → 应用表在 mdr/ext 而连接默认 schema 为 dbo（SQL Server），不显式配置时 Flyway 会判定空库跳过基线、重放老迁移（已实测会在 V20260517001 失败）；team profile 与 application-local.yml 均配置 `schemas: mdr,ext`。
- [全新库预置名称缺省（已实测确认并由 V20260919003 解决）] → Flyway（含 V20260919002）先于 LanguageInitializer 执行，仅按标识关联的 02 在全新库关联不到行；V20260919003 以固定 UUID 直接播种代码+名称，全新库开箱即有 22 语言 + 22 名称（实测）。恢复出厂会清空语言表且 Flyway 不重跑，由 `LanguagePresetSeeder` 重放 03 脚本兜底。

## Migration Plan

1. Flyway 迁移（sqlserver/dm 建 `ext.language_identification`，sqlite 建 `ext_language_identification`），版本号接现有序列；sqlite 脚本带 `IF NOT EXISTS` 幂等守卫（ddl-auto 可能先建表）。
2. Flyway 迁移 V20260919002（三方言）：按 `language_identifier` 关联标准表幂等插入 22 条中文名称预置行（`NOT EXISTS` 守卫，不清表重插）。
3. Flyway 迁移 V20260919003（三方言）：22 条内置语言以固定 UUID 一体化播种语言代码与名称（代码按 `language_identifier` 防重、名称按 `language_id` 防重）；面向全新库与恢复出厂场景。V20260919002 不可改动（既有库已标记应用），两者分工共存。
3. **启用 Flyway（用户裁定 2026-09-19）**：`application.yml` 默认 `spring.flyway.enabled=true`，`baseline-on-migrate: true` + `baseline-version: "20260918002"`（既有最大版本号）。既有库表由 ddl-auto 历史创建、老迁移不可重放，基线化使既有库首次启用时只应用 V20260919001/V20260919002 两个带幂等守卫的新迁移。
4. **初始化顺序**：`spring.jpa.defer-datasource-initialization: true` 使 Hibernate ddl-auto 先于 Flyway 执行——全新库因此建表后 schema 非空，Flyway 走 baseline-on-migrate 基线路径（而非从空库重放全部老迁移）；既有库同样只应用两个新迁移。两环境共用同一基线路径，全新库与既有库均开箱即有预置名称。注：Boot 3.5 下 defer 与 Flyway 会互相登记 depends-on 造成循环依赖，由 `FlywayAfterJpaConfig`（BeanFactoryPostProcessor）摘除 entityManagerFactory→flyway 边、保留 flyway→entityManagerFactory 边固定顺序。
5. 预置名称数据 MUST 仅存在于 SQL 迁移脚本；`LanguageInitializer` 只播种语言代码，应用代码 MUST NOT 包含名称预置数据。
6. `orm-team.xml` + `DatabaseEnvironment.EXT_TABLES` + `OrmTeamXmlTest` 同步。
7. 部署新 jar 即完成升级；旧数据无需迁移（扩展表为新增卫星表）。
8. 回滚：回退 jar + 删除 EXT 新表与 `flyway_schema_history` 中基线后两条记录（仅 EXT 表，无主表变更，回滚安全）。

## Open Questions

无。
