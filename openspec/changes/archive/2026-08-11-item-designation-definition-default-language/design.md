## Context

当前系统的前后端对 ISO/IEC 11179-3:2023 §8 的 `Designation` 与 `Definition` 语言属性处理存在不一致：

- **前端**：`DesignationEditor.vue`、`DefinitionEditor.vue` 和 `MultilingualTextEditor.vue` 在新增行时把 `languageId` 初始化为空字符串，且表单项校验要求“至少有一条完整的指称（语言+内容）”；`ItemFormDialog.vue` 在提交时还会过滤掉 `languageId` 为空的行。
- **后端**：`TextLine.java` 对 `languageId` 标注 `@NotNull`，`ItemAdministrationService.loadLanguage` 在 `languageId` 为 `null` 时直接抛出异常，未利用 `RegistrySpecification.default_language` 进行自动推导。
- **标准依据**：ISO/IEC 11179-3:2023 Table 14（Designation 属性）与 Table 15（Definition 属性）规定，`language` 属性的重数为 `0..1` 且为条件属性；当 `Registry_Specification.default_language` 已指定时，该属性可省略。`source` 属性同样为 `0..1`。

同时，指称/定义编辑能力分散在多个组件和视图中，没有统一作为 `Item` 抽象层的通用编辑区被所有注册项复用。

## Goals / Non-Goals

**Goals:**
- 统一从前端 `RegistrySpecification` 读取默认语言，并在所有 Item 创建/编辑流程中作为新指称/定义行的默认选择。
- 将语言标识和来源文档从必填改为可选，前后端均按 ISO 11179-3 条件规则处理。
- 在 `languageId` 为空时，后端自动使用 `RegistrySpecification.defaultLanguage` 补全；无默认语言且未指定语言时返回明确错误。
- 将 `DesignationDefinitionSection` 提升为所有 `Item` 子类通用的复合编辑器，并统一替换各创建/编辑视图中分散的编辑器调用。
- 保持已有数据的读取、编辑行为不变，实现向后兼容。

**Non-Goals:**
- 不改写 `LanguageIdentification` 数据模型或多语言字典机制。
- 不新增“语言不适用”的显式标志位；当前阶段通过默认语言省略或空值处理已覆盖标准的条件重数。
- 不修改 `ReferenceDocument` 的 CRUD 行为；仅将 `sourceId` 作为可选关联字段处理。
- 不重构 `ItemFormDialog.vue` 的 tab 布局；仅替换其指称/定义编辑组件，并保留现有的标识/版本/注册机构逻辑。

## Decisions

### 1. 默认语言由注册规范提供，UI 使用统一组合式函数
**选择**：新增 `useRegistryDefaultLanguage()`（或复用现有全局 store）在 `AppLayout.vue` 初始化时加载唯一/首个 `RegistrySpecification`，并向下提供 `defaultLanguageId`/`defaultLanguageIdentifier`。
**理由**：
- 避免每个视图/编辑器重复调用 `registrySpecificationApi.list`。
- 注册规范在系统初始化后通常唯一，默认语言属于注册级配置，而非用户会话状态。
**替代方案**：每个编辑器挂载时独立请求； rejected，因为会产生冗余请求且容易遗漏。

### 2. 前端编辑器以 prop 接收 defaultLanguageId，提交时补齐空语言
**选择**：`DesignationEditor`、`DefinitionEditor`、`MultilingualTextEditor`、`DesignationDefinitionSection` 新增 `defaultLanguageId?: string` prop；新增行时若 prop 存在则默认填入；提交/向父组件通知前，若某行 `value` 非空但 `languageId` 为空，则自动替换为 `defaultLanguageId`。
**理由**：
- 用户看到的下拉框中已预选默认语言，符合“不必每次选择”的诉求。
- 即使用户清空语言，后端也能通过前端补齐的数据正常处理，避免提交时丢失有效文本。
**替代方案**：完全依赖后端在提交时补全语言；rejected，因为 UI 会显示为空，体验不一致，且 `ItemFormDialog` 的过滤逻辑会丢弃空语言行。

### 3. 后端 TextLine.languageId 改为可选，并在业务层解析默认语言
**选择**：移除 `TextLine.languageId` 的 `@NotNull`；在 `ItemAdministrationService` 中新增 `resolveLanguage(UUID languageId)`，逻辑为：若 `languageId` 非空则加载；否则读取 `RegistrySpecificationRepository` 的第一条记录，取 `defaultLanguage`；若仍为空则抛出 `BusinessException`。
**理由**：
- 与 ISO 11179-3 的条件重数一致。
- 将默认语言解析集中在单一业务服务，避免每个 `Item` 子类服务重复实现。
**替代方案**：在 Controller 或 DTO 中默认值注入；rejected，因为默认语言属于业务规则，不应由 Web 层承担。

### 4. 统一使用 DesignationDefinitionSection 作为 Item 抽象层编辑器
**选择**：将 `DesignationDefinitionSection.vue` 作为所有 `Item` 子类创建/编辑对话框的默认“指称+定义+上下文”编辑区；`NamespaceList` 等简单视图由分散的 `DesignationEditor` + `DefinitionEditor` 改为使用该复合组件；`ItemFormDialog` 的“指称”与“定义” tab 分别引入该组件的两个子编辑器或整体使用 `DesignationDefinitionSection`，以保持一致。
**理由**：
- 该组件已封装 ISO 11179-3 §8 的指称、定义、上下文、配对等编辑能力，符合 `Item` 抽象层通用语义。
- 减少视图层的重复代码和风格差异。
**替代方案**：为每种注册项单独维护编辑器；rejected，与本次“抽象复用”的目标冲突。

### 5. 保留 MultilingualTextEditor 作为过渡兼容层
**选择**：`ItemFormDialog.vue` 若暂时无法整体替换为 `DesignationDefinitionSection`，则仍使用 `MultilingualTextEditor`，但为其增加 `defaultLanguageId` prop 与可选校验，并确保提交前补齐空语言。
**理由**：降低一次性替换带来的回归风险，同时满足默认语言需求。
**替代方案**：直接删除 `MultilingualTextEditor`；rejected，风险高且超出当前变更范围。

## Risks / Trade-offs

- **[Risk] 默认语言解析依赖数据库中存在 RegistrySpecification** → **Mitigation**：`InitWizardService` 初始化时会创建默认注册规范；后端 `resolveLanguage` 在缺失时抛明确错误，前端在检测到无默认语言时禁用编辑器或提示用户先完成初始化。
- **[Risk] 现有测试期望 TextLine.languageId 必填** → **Mitigation**：更新 `DesignationServiceTest`、`DefinitionServiceTest`、`ItemAdministrationServiceTest` 等测试用例，增加“无 languageId 时使用默认语言”的场景，并保留原有“显式 languageId”场景。
- **[Risk] 多视图统一替换后，命名规范/上下文交互出现差异** → **Mitigation**：优先在 `DesignationDefinitionSection` 中统一处理命名规范选项和上下文选择器，再逐一验证各视图渲染；如有视图需要特殊字段（如 Namespace 的 governance tab），保持其自定义字段，仅替换指称/定义区域。
- **[Risk] 提交时前端自动补语言可能导致用户误以为是已手动选择** → **Mitigation**：UI 中默认语言显示为已选中，且用户可手动更改为其他语言；清空后仍会在提交前补齐，但保存成功后详情页会显示实际语言，用户可再次编辑。
- **[Trade-off] 可选语言后，去重/冲突校验（如 PREFERRED 唯一性）仍以实际解析后的 languageId 为准**，因此即使前端传递空值，后端补全后仍按统一语言参与校验，逻辑一致。

## Migration Plan

1. **后端**：调整 `TextLine` 校验与 `ItemAdministrationService` 默认语言解析；运行 `metadata-registry-web` 单元测试与集成测试修复失败用例。
2. **前端基础**：实现/复用 `useRegistryDefaultLanguage`，为编辑器组件增加 `defaultLanguageId` prop。
3. **前端编辑器**：更新 `DesignationEditor`、`DefinitionEditor`、`MultilingualTextEditor`、`DesignationDefinitionSection`，并替换各视图引用。
4. **验证**：运行 `npx vite build` 和 `npm run test:unit`（或等价命令），并执行 `mvnw test -pl metadata-registry-web` 验证后端。
5. **回滚**：由于仅放宽约束且新增默认行为，回滚时恢复 `@NotNull` 与前端的必填校验即可；无数据迁移风险。

## Open Questions

- 当前系统中 `RegistrySpecification` 是否允许多个？若允许多个，UI 与后端应以“第一条”还是“标记为默认”为准？本设计暂按“唯一/首个”处理，后续可扩展为 `current=true` 标记。
- `MultilingualTextEditor` 中的“孤立行”例外与 PREFERRED 冲突校验是否需要在新的 `DesignationDefinitionSection` 中复现？建议统一迁移，避免行为差异。
