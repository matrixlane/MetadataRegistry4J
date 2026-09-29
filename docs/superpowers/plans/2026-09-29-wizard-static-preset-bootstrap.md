# 实施计划：wizard-static-preset-bootstrap

依据：`docs/superpowers/specs/2026-09-29-init-wizard-static-preset-design.md`（已确认）+ `openspec/changes/wizard-static-preset-bootstrap/`（proposal/design/specs/tasks 已就绪且 validate 通过）。

## 里程碑

1. **后端统一预置入口**（TDD）
   - `LanguagePresetSeeder` 脚本清单扩展（003 + `db/preset/<vendor>/localize-language-names.sql`）
   - 新增 `StaticDataPresetService.presetAll()` + `StaticDataPresetRunner`（@Order(10)，先于 `@Order(90)` IdentifierStrategyLoader；入口末尾显式 `refreshRegistry()` 双保险）
   - 步骤：重放脚本 → 系统组织/sys: 命名空间（JPA 幂等，`SystemDefaultNamespaceInitializer` 去掉 Runner/条件装配语义）→ 全局默认标识策略（JdbcTemplate 幂等插入，三方言兼容）→ 占位管理员（并入，删 `DataInitializer`）→ 流转规则调和（删 `RegistrationProcessSeedInitializer`）
   - 删除 `LanguageInitializer`（语言代码唯一数据源 = SQL 脚本）
2. **向导与出厂接线**
   - `InitWizardService`：撤止血补丁、撤语言初始化调用；`clearBusinessDataOnly` 保留集 += 两张语言表；setup 清理后 `presetAll()` 兜底
   - `FactoryResetService`：clearAll → reseedAdmin → `presetAll()`
3. **迁移与预置脚本**（三方言）：`V20260929001__Localize_language_names.sql` + `db/preset/*/localize-language-names.sql`
4. **演示数据开关**：`ContextTestDataInitializer` 改 `app.seed.demo-data.enabled`（默认 false），application.yml 补配置
5. **前端**：下拉中文 label（5 组件 + dict.ts + ItemFormDialog）、向导文档语言 API 化、LanguageList placeholder
6. **测试与验证**：后端新增/适配测试 → 全量 → 前端 vitest + build → 三方言冒烟（SQLite 内存全向导；SQL Server scratch 副本验证迁移机制）→ detect_changes → openspec validate

## 关键约束

- 预置数据值仅存 SQL 脚本；Java 只执行/装配。
- `value` 提交格式不变（BCP 47）。
- 当前运行库不做任何修复。
- 既有 6 个 InitWizardServiceIntegrationTest 红（registrar WIP 引起）不属于本变更，保持隔离不回填。
