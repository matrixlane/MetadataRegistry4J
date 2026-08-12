## 1. 后端默认语言解析与校验

- [ ] 1.1 在 `ItemAdministrationService` 中注入 `RegistrySpecificationRepository`，新增 `resolveLanguage(UUID languageId)` 方法，逻辑：非空则加载；否则取首条 `RegistrySpecification` 的 `defaultLanguage`；无默认语言则抛 `BusinessException`。
- [ ] 1.2 替换 `applyDesignations` 与 `applyDefinitions` 中对 `loadLanguage(line.languageId())` 的调用，改为 `resolveLanguage(...)`。
- [ ] 1.3 移除 `TextLine` 中 `languageId` 的 `@NotNull` 注解；`sourceId` 保持可选；更新构造函数重载保持向后兼容。
- [ ] 1.4 调整 `validateIsolatedLines`/`validatePreferredUniqueness` 使其在 `languageId` 为空时仍按解析后的默认语言参与校验。
- [ ] 1.5 更新 `DesignationService`、`DefinitionService` 及相关 Controller 中的校验与错误提示（若存在）。
- [ ] 1.6 使用 GitNexus 对 `ItemAdministrationService.applyDesignations`、`ItemAdministrationService.applyDefinitions`、`TextLine` 进行影响分析，确认下游调用方与测试范围。

## 2. 后端测试补充

- [ ] 2.1 在 `ItemAdministrationServiceTest` 中新增测试：未指定 `languageId` 且存在默认语言时，成功创建指称/定义。
- [ ] 2.2 新增测试：未指定 `languageId` 且不存在默认语言时，抛出 `BusinessException`。
- [ ] 2.3 新增测试：显式指定 `languageId` 时仍按原语言保存，默认语言不影响已有行为。
- [ ] 2.4 更新 `DesignationServiceTest`、`DefinitionServiceTest` 中断言 `languageId` 必填的用例，改为验证默认语言解析行为。
- [ ] 2.5 运行 `mvnw test -pl metadata-registry-web` 并修复相关失败（注意排除既有无关失败）。

## 3. 前端默认语言提供能力

- [ ] 3.1 新增 `useRegistryDefaultLanguage()` 组合式函数（或复用全局 store），在 `AppLayout.vue` 挂载时加载首个 `RegistrySpecification` 的 `defaultLanguageId` 并缓存。
- [ ] 3.2 确保该函数返回 `{ defaultLanguageId, defaultLanguageIdentifier, loading, error }`，并提供重试机制。
- [ ] 3.3 在 `main.ts` 或 `App.vue` 中处理无注册规范/无默认语言时的全局提示，避免编辑器无法使用。

## 4. 前端编辑器组件改造

- [ ] 4.1 为 `DesignationEditor.vue` 新增 `defaultLanguageId` prop；新增行时默认填充；允许 `languageId` 为空；提交前向父组件传递时保留空值由父组件补全。
- [ ] 4.2 为 `DefinitionEditor.vue` 新增 `defaultLanguageId` prop；新增行时默认填充；允许 `languageId` 为空。
- [ ] 4.3 为 `MultilingualTextEditor.vue` 新增 `defaultLanguageId` prop；新增行默认填充；语言、来源文档可选；保留孤立行与 PREFERRED 冲突校验。
- [ ] 4.4 为 `DesignationDefinitionSection.vue` 新增 `defaultLanguageId` prop，并透传给 `DesignationEditor`、`DefinitionEditor`；确保无 `itemId` 时配对区提示文案不变。
- [ ] 4.5 在 `DesignationEditor` 中为 `Designation` 行支持可选的 `sourceId` 选择器（若设计决定与 `MultilingualTextEditor` 保持一致），或保留现状并在 `MultilingualTextEditor` 中处理来源文档。
- [ ] 4.6 验证 `ItemFormDialog.vue` 使用 `MultilingualTextEditor` 或切换为 `DesignationDefinitionSection` 后，默认语言与可选校验生效，且提交前过滤逻辑补全空语言为默认值。

## 5. 视图层统一接入统一编辑器

- [ ] 5.1 在 `NamespaceList.vue` 的名称/描述区域使用 `DesignationDefinitionSection` 替换独立的 `DesignationEditor` + `DefinitionEditor`；保留 governance/identifier/version 等 tab。
- [ ] 5.2 在 `ConceptualDomainList.vue` 中使用 `DesignationDefinitionSection` 替换 `MultilingualTextEditor`。
- [ ] 5.3 在 `ValueDomainList.vue` 中使用 `DesignationDefinitionSection` 替换 `MultilingualTextEditor`。
- [ ] 5.4 在 `DataElementList.vue` 中确保 `ItemFormDialog` 使用统一编辑器或透传 `defaultLanguageId`；指称/定义 tab 默认语言生效。
- [ ] 5.5 在 `NamingConventionList.vue` 中使用 `DesignationDefinitionSection` 替换独立的 `DesignationEditor` + `DefinitionEditor`。
- [ ] 5.6 检查所有引用 `MultilingualTextEditor` 的视图，统一替换为 `DesignationDefinitionSection` 或同步 `defaultLanguageId`。

## 6. 前端测试与验证

- [ ] 6.1 更新或新增 `DesignationEditor.spec.ts`、`DefinitionEditor.spec.ts` 测试默认语言填充与可选语言。
- [ ] 6.2 更新 `MultilingualTextEditor.spec.ts` 测试默认语言与可选来源文档。
- [ ] 6.3 运行 `npx vite build` 与 `npm run test:unit`（或项目等价命令），修复 TypeScript/编译错误。

## 7. 集成验证与文档

- [ ] 7.1 本地启动前后端，手动验证：创建数据元时新指称/定义行默认语言为注册表默认语言；清空语言仍可保存（由后端补全）。
- [ ] 7.2 手动验证命名空间、概念域、值域、命名规范创建流程语言默认行为一致。
- [ ] 7.3 运行 `gitnexus_detect_changes()` 确认变更范围仅涉及预期符号与执行流。
- [ ] 7.4 更新 `ISO_11179-3_2023_Comparison_Report.md` 中 Designation/Definition 语言可选与默认语言的相关说明（若存在）。
- [ ] 7.5 完成 OpenSpec 归档：执行 `npx openspec archive item-designation-definition-default-language` 并确认 specs 被合并到 `openspec/specs/`。
