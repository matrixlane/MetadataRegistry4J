# 实施计划：命名规范表单对齐 ISO/IEC 11179-3:2023

来源：`standardize-naming-convention-form`（proposal/design/specs/tasks 已就绪并 validate 通过）
设计稿：`docs/superpowers/specs/2026-10-04-standardize-naming-convention-form-design.md`

## TDD 步骤

1. **红**：新增 `metadata-registry-ui/src/views/naming-convention/NamingConventionList.spec.ts`
   - mock `@/api/resources`（namingConventionApi.list/create/update/get/delete、namespaceApi.list）与 `vue-i18n`、`@/composables/useRegistryDefaultLanguage`。
   - 用例 A：点「新建」→ ResourceFormDialog.open → 断言页签集合 = {注册标识, 属性}，无 {指称, 定义}；属性页签内 scopeRule/authorityRule/semanticRule/syntacticRule/lexicalRule 五个 el-input 渲染。
   - 用例 B：以 `{scopeRule:'^a$',authorityRule:'',semanticRule:'',syntacticRule:'',lexicalRule:'',registration:{}}` 调 submitFn → `namingConventionApi.create` 被调，payload 含五规则属性、无 designations/definitions 键。
   - 用例 C：scopeRule 为空 → submitFn throw、create 未调用。
2. **绿**：改 `NamingConventionList.vue`（移除指称/定义页签与校验、新增三字段、payload/回填同步、列表加三列、首列回退）+ `zh-CN.json` 新增/删除 key。
3. **验证**：vitest 全量、`npm run build`、`openspec validate --all`、`detect-changes --scope all`。

## 关键实现点

- 表单字段：`form.semanticRule/syntacticRule/lexicalRule`，el-input + tooltip/placeholder（i18n key 命名与现有 scopeRule 系一致）。
- 提交：`{ scopeRule, authorityRule?, semanticRule?, syntacticRule?, lexicalRule?, registration }`。
- 列表：五规则列统一 `truncate(x, 50)`、空显 `-`；首列 `row.label || truncate(row.scopeRule, 40) || '-'`。
- 删除 `designationsRequired` i18n key（全局仅此一处引用）。
