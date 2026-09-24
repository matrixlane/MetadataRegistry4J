# 实施计划：语言标识扩展表（2026-09-19）

依据：OpenSpec 变更 `add-language-identification-extension`（proposal/design/specs/tasks）与已确认设计
`docs/superpowers/specs/2026-09-19-language-identification-extension-design.md`。

## 步骤（TDD）

1. **迁移与登记**
   - Flyway：sqlserver/dm 建 `ext.language_identification`（id uniqueidentifier/varchar PK, language_id not null unique, language_name, territory_name）；sqlite 建 `ext_language_identification`。
   - `DatabaseEnvironment.EXT_TABLES` + `orm-team.xml` + 新增 `orm/orm-sqlite.xml`（personal profile 加载）+ `OrmTeamXmlTest` 清单。
2. **实体 + 仓储 + 服务（先写测试）**
   - `ext.basicandcore.LanguageIdentificationExtension`（卫星模式）。
   - `LanguageIdentificationExtensionRepository`、`LanguageExtensionService`（getOrDefault/mapByLanguageIds/upsert）。
3. **接线（先改测试）**
   - `LanguageRequest`/`LanguageResponse` 加 `languageName`/`territoryName`（Response 已有，语义改为持久化优先）。
   - `LanguageIdentificationService.toResponse()` 持久化优先、Locale 回退；create/update upsert + 留空回填。
   - `LanguageInitializer` 22 条中文名称补种。
4. **前端**：`LanguageList.vue` 文案与表单字段。
5. **验证**：`mvn test`、vitest/build/eslint、真实启动手工验收、detect-changes。

## 验证基线

- 后端 `mvn test`（web + ext + common-facilities 模块）；`OrmTeamXmlTest` 通过。
- 前端 `npm run build`、vitest、eslint 0 error。
