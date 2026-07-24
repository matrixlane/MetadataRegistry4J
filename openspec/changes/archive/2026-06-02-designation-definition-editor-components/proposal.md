## Why

目前系统中，指称（Designation）、定义（Definition）、指称-定义配对（DesignationDefinitionPairing）、上下文（Context）等编辑功能分散在多个视图中，每处均有各自独立的实现逻辑与样式。随着业务模型来源于同一 ISO/IEC 11179-3:2023 §8 Designation_and_Definition package，这种分散实现既违背了标准的语义统一性，也使后续维护、扩展和风格统一变得困难。

## What Changes

- **抽取并重构** `MultilingualTextEditor.vue` 为专用的 `DesignationEditor.vue` 和 `DefinitionEditor.vue` 两个独立组件，语义更明确
- **新增** `ContextSelector.vue`：可复用的上下文选择器，含可接受性（Acceptability）配置
- **新增** `AcceptabilitySelect.vue`：可复用的可接受性枚举选择器
- **重构** `DesignationDefinitionPairingEditor.vue` 使其引用以上新组件
- **新增** `DesignationDefinitionSection.vue`：封装"指称 + 定义 + 配对"完整三段式编辑区域，作为顶层复合组件
- **更新** `ItemFormDialog.vue`：引用新组件替换旧实现
- **更新** `NamespaceList.vue`：引用新组件替换旧内联实现
- **统一** 所有调用方（ConceptualDomainList、ValueDomainList 等）的使用方式

## Capabilities

### New Capabilities
- `designation-editor`: 符合 ISO 11179 §8 语义的指称编辑器组件，每条指称含语言、文本值、上下文-可接受性关联
- `definition-editor`: 符合 ISO 11179 §8 语义的定义编辑器组件，每条定义含语言、文本值、上下文-可接受性关联
- `context-selector`: 可复用的上下文选择器（远程搜索），支持嵌入于指称/定义行中
- `acceptability-select`: 可接受性枚举选择器（PREFERRED/ADMITTED/DEPRECATED/SUPERSEDED/OBSOLETE）
- `designation-definition-section`: 封装指称、定义、配对三段式完整编辑区的复合组件

### Modified Capabilities
- （无需求级别变更，仅内部实现重构）

## Impact

- **前端组件**：重构 `src/components/` 下的 `MultilingualTextEditor.vue`、`DesignationDefinitionPairingEditor.vue`；新增 5 个组件文件
- **调用方视图**：`ItemFormDialog.vue`、`NamespaceList.vue`、`ConceptualDomainList.vue`、`ValueDomainList.vue` 需要更新 import 与模板引用
- **类型定义**：`src/types/common.ts` 中的 `TextLineForm`、`PairingForm`、`TextLineContextForm` 保持不变
- **API 调用**：不变（仍使用 `languageApi`、`contextApi`、`pairingApi`）
- **后端无变更**
