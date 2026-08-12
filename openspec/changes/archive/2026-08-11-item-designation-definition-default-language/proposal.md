## Why

在 MetadataRegistry4j 中，指称（Designation）与定义（Definition）是 ISO/IEC 11179-3:2023 §8 Designation_and_Definition 包的核心概念，应作为 `Item` 抽象层的一部分被所有注册项复用。当前前端在创建数据元等注册项时，语言标识被设计成必填项，且每次都需要用户手动选择；而标准明确指出，当 `Registry_Specification.default_language` 已配置时，指称/定义的语言属性可以省略，由注册表默认语言自动推导。同时，来源文档（source）也不是必填项。这既增加了用户操作负担，也偏离了标准语义。

此外，指称/定义编辑器目前分散在 `MultilingualTextEditor`、`DesignationEditor`、`DefinitionEditor` 和 `DesignationDefinitionSection` 中，部分视图仍使用旧组件或未接入统一的默认语言能力，导致同一份 `Item` 抽象层语义在不同注册项创建流程中体验不一致。

## What Changes

- **统一默认语言推导机制**：前端在所有注册项创建/编辑流程中自动使用 `RegistrySpecification.defaultLanguage` 作为新指称/定义行的默认语言；后端在 `languageId` 为空时，自动回退到注册表默认语言，未配置默认语言时再拒绝提交。
- **指称/定义字段可选化**：将语言标识和来源文档从“必填”改为“可选”，UI 保持输入控件但不再强制选择；后端 `TextLine` 校验与 `ItemAdministrationService` 按 ISO 11179-3 的条件规则处理。
- **抽象并复用 Item 层编辑器**：把现有的 `DesignationDefinitionSection` 提升为所有 `Item` 子类通用的“指称+定义+上下文”编辑区，统一替换 `ItemFormDialog` 中分散的 `MultilingualTextEditor` 调用，以及 `NamespaceList`/`ConceptualDomainList`/`ValueDomainList`/`NamingConventionList` 等视图中各自独立的编辑器引用。
- **向后兼容**：已有数据（含显式语言/来源）的读取、编辑和 API 行为不变；仅放宽创建时的必填约束并补充默认值。

## Capabilities

### New Capabilities
- `item-designation-definition-editor`：Item 抽象层通用的指称/定义复合编辑器，支持默认语言、可选语言、可选来源、上下文关联，供所有注册项创建/编辑复用。
- `registry-default-language-provider`：向 UI 提供当前注册表默认语言标识的能力，确保各编辑器无需重复请求。

### Modified Capabilities
- `designation-editor`：新增 `defaultLanguageId` prop；新行默认填充默认语言；语言标识改为可选。
- `definition-editor`：新增 `defaultLanguageId` prop；新行默认填充默认语言；语言标识改为可选。
- `designation-definition-section`：新增 `defaultLanguageId` prop，并作为所有 Item 创建/编辑的默认选择。

### Removed Capabilities
- （无）

## Impact

- **前端组件**：`metadata-registry-ui/src/components/DesignationEditor.vue`、`DefinitionEditor.vue`、`DesignationDefinitionSection.vue` 需要支持默认语言与可选校验；`ItemFormDialog.vue` 中仍暂用的 `MultilingualTextEditor.vue` 亦须同步支持 `defaultLanguageId` 与可选校验；`NamespaceList.vue`、`ConceptualDomainList.vue`、`ValueDomainList.vue`、`NamingConventionList.vue` 等视图统一接入新的复合编辑器或默认语言 prop。
- **前端状态**：新增或复用注册表默认语言的全局/组合式提供（如 `useRegistryDefaultLanguage`），避免每个视图重复请求 `registry-specification`。
- **后端 DTO**：`metadata-registry-web/src/main/java/.../dto/common/TextLine.java` 的 `languageId` 从 `@NotNull` 改为可选；`sourceId` 保持可选。
- **后端服务**：`ItemAdministrationService.applyDesignations/applyDefinitions` 在 `languageId` 为 `null` 时，通过 `RegistrySpecificationRepository` 读取默认语言并补全；`loadLanguage` 不再无条件抛出 `languageId 不能为空`。
- **API 行为**：创建/更新指称/定义时，允许提交 `languageId=null` 或 `sourceId=null`，但系统会按默认语言规则解析；无默认语言且未指定语言时返回 400 验证错误。
- **测试**：需要补充单元/集成测试，验证默认语言补全、可选字段、编辑器默认值、多视图调用方行为一致。
