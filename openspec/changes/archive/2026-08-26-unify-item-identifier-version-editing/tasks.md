## 1. 前端：抽取可复用注册区段

- [x] 1.1 从 `ItemFormDialog.vue` 抽取 `ItemRegistrationSection.vue`（命名空间选择、标识符自动/手工、段模板变量、版本自动/手工、编辑态禁用与回显），保持 `ItemFormDialog` 对外 props/事件签名不变
- [x] 1.2 为 `ItemRegistrationSection.vue` 编写组件测试，覆盖三种 identifierInputMode/versionInputMode 与编辑态语义
- [x] 1.3 回归运行 `ItemFormDialog.spec.ts`，确认数据元/值域/概念域/语境四个既有界面行为不变

## 2. 前端：逐页迁移到 ItemFormDialog

- [x] 2.1 DataElementConceptList、ObjectClassList、PropertyList、ValueMeaningList（Concept 家族，后端 ConceptRequest 已支持 registration）
- [x] 2.2 MeasureClassList、UnitOfMeasureList（Concept 家族；Dimensionality 无独立界面，不涉及）
- [x] 2.3 DataTypeList、DataTypeSchemeList（后端已支持 registration）
- [x] 2.4 确认 AdministeredItemList 无创建/编辑入口（仅状态迁移），无需接入
- [x] 2.5 确认 ClassificationTreeBrowser / MappingScheme 界面为非 Item 实体，不接入
- [x] 2.6 确认 PermissibleValue / PermissibleValueSet / PermissibleValueMeaning / ValueMeaningSet 无独立创建/编辑界面（许可值内嵌于值域表单），前端不涉及
- [x] 2.7 逐页核对：创建/编辑提交负载携带 registration，编辑态标识符禁用、版本按策略行为正确；另补齐 NamingConventionList 与 RegistrySpecificationSettings（内嵌 ItemRegistrationSection）

## 3. 后端：补齐 registration 支持缺口

- [x] 3.1 盘点确认非 Item 实体清单（MappingScheme、ClassificationScheme/Node 等），移出范围
- [x] 3.2 为 PermissibleValueMeaningRequest 增加可选 `ItemRegistrationRequest registration` 字段
- [x] 3.3 PermissibleValueMeaningService 的 create/update 接入 `ItemAdministrationService.ensureAdministeredItem`（无 registration 时保持原行为）
- [x] 3.4 为新增路径补服务层测试（带 registration 创建 / 不带 registration 向后兼容 / 编辑补录场景）

## 4. 验证与收尾

- [x] 4.1 前端 `npx vite build` 与组件测试通过（27/27）
- [x] 4.2 后端注册相关测试通过（ItemAdministrationServiceTest、PermissibleValueMeaningServiceTest、ScopedIdentifierServiceTest、NamespaceConstraintValidatorTest、IdentifierResolutionServiceTest 共 73/73）
- [x] 4.3 逐页扫描核对：全部 Item 列表页已统一为 ItemFormDialog 或内嵌 ItemRegistrationSection，无残留裸 identifier 表单
- [x] 4.4 以 `git status`/`git diff --stat` 核对变更范围与 spec/tasks 一致（GitNexus MCP 本会话不可用，用等效手段）
