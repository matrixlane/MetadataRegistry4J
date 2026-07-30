## Why

ISO/IEC 11179-3:2023 第 8 章（Designation and Definition package）通过 Figure 6 定义了 `Item`、`Designation`、`Definition`、`Context`、`DesignationContext`、`DefinitionContext`、`DesignationDefinitionPairing`、`NamingConvention` 和 `Namespace` 之间的一组复杂关系。项目中这些实体已实现，但存在两类偏差：

1. **元模型关系偏差**：`Namespace` 与 `NamingConvention` 在标准中为 many-to-many（`naming_convention_utilization`），当前实现仅为单值外键；`NamingConvention` 未按标准拆分 `scope_rule`、`authority_rule`、`semantic_rule`、`syntactic_rule`、`lexical_rule` 属性；`Namespace` 的三个约束指示器（`one_name_per_item_indicator`、`one_item_per_name_indicator`、`mandatory_naming_convention_indicator`）尚未在后端校验。
2. **前端关系表达不完整**：`MultilingualTextEditor` 缺少 `ReferenceDocument`（来源文献）选择器；`MetadataDetailView` 未展示指称/定义/上下文/配对关系；`ContextList` 没有反向浏览；`BrowserView` 未按上下文/可接受性过滤；配对编辑只能在保存后使用，且缺少重复校验、可读标签和可接受性徽章。

本变更将补齐上述偏差，使前后端在 Figure 6 描述的复杂关系上保持一致并可交互。

## What Changes

- 后端将 `Namespace.namingConvention` 从 `ManyToOne` 改为 `ManyToMany`（`namespace_naming_convention` 关联表），并保留过渡列实现无损迁移；新增 `NamingConvention` 标准属性字段（`scopeRule`、`authorityRule`、`semanticRule`、`syntacticRule`、`lexicalRule`），与现有 `rulesJson` 并存以兼容现有规则引擎。
- 后端在命名空间保存/更新时校验三个指示器约束：强制命名约定时必须有且只有一个命名约定、强制命名约定时关联指称必须遵循同一命名约定、唯一名称/非歧义名称时校验 `Designation` 与 `Item` 的对应关系。
- 后端为 `Designation` 与 `Definition` 增加直接可接受性属性（`acceptability`），并与 `DesignationContext`/`DefinitionContext` 中的可接受性并存；该属性可选，默认由上下文可接受性推导。
- 前端在 `MultilingualTextEditor` 的每行增加 `ReferenceDocument` 选择器；在指称/定义上下文行中显式展示 `Acceptability` 标签；为首条 PREFERRED 指称显示徽章。
- 前端扩展 `MetadataDetailView`，增加「指称」「定义」「上下文」「配对」四个懒加载标签页，展示完整关系表格并支持跳转到关联项。
- 前端在 `ContextList` 增加「查看引用」操作，弹窗展示引用该上下文的所有 `DesignationContext`、`DefinitionContext` 和 `DesignationDefinitionPairing`。
- 前端扩展 `BrowserView` 搜索：增加「上下文」和「可接受性」高级筛选，并在结果卡片中展示匹配上下文徽章。
- 前端优化 `DesignationDefinitionPairingEditor`：支持在未保存项内使用临时客户端 ID 创建配对（可选高级实现）、重复配对校验、指称/定义选项显示语言与上下文标签。
- 更新 `ISO_11179-3_2023_Comparison_Report.md` 第 8 章状态，并补充 `Namespace` 约束与 `NamingConvention` 属性的覆盖说明。

## Capabilities

### New Capabilities

- `iso-11179-3-ch8-frontend-reference-source`：在前端指称/定义编辑器中支持 `ReferenceDocument` 来源选择，补齐 Figure 6 中 `Designation`/`Definition` → `ReferenceDocument` 关系。
- `iso-11179-3-ch8-frontend-detail-relationships`：在元数据详情页展示指称、定义、上下文、配对及其关系。
- `iso-11179-3-ch8-frontend-context-browsing`：支持从上下文反向浏览关联的指称/定义和配对。
- `iso-11179-3-ch8-frontend-acceptability-badges`：在列表与详情中显式展示 `Acceptability` 可接受性徽章。
- `iso-11179-3-ch8-frontend-search-by-context`：数据字典支持按上下文与可接受性过滤。
- `iso-11179-3-ch8-frontend-pairing-flow`：优化指称-定义配对创建流程，支持在同一弹窗内完成并校验重复。
- `iso-11179-3-ch8-backend-namespace-naming-convention-m2m`：将 `Namespace` 与 `NamingConvention` 实现为 many-to-many 关联，并校验 `mandatory_naming_convention_indicator` 约束。
- `iso-11179-3-ch8-backend-naming-convention-standard-attrs`：为 `NamingConvention` 补充 ISO/IEC 11179-3:2023 8.4.4 标准属性。
- `iso-11179-3-ch8-backend-designation-acceptability`：为 `Designation` 与 `Definition` 增加直接可接受性属性，保持与上下文可接受性的语义一致性。

### Modified Capabilities

（无现有 OpenSpec 规范的需求行为需要修改；本变更仅补充第 8 章未覆盖的部分，不改动现有业务功能的需求定义。）

## Impact

- `metadata-registry-common-facilities`：`Namespace` 和 `NamingConvention` 实体结构变更；新增 `Designation.acceptability` 与 `Definition.acceptability` 字段；新增约束校验逻辑。
- `metadata-registry-web`：`NamespaceService` 新增约束校验；`NamingConvention` 的 API/DTO 扩展；`DesignationDefinitionPairingService` 增加重复校验；`ItemRelationshipController` 已存在，需扩展返回上下文关系与命名约定引用。
- `metadata-registry-ui`：修改 `MultilingualTextEditor.vue`、`DesignationDefinitionPairingEditor.vue`、`ItemFormDialog.vue`、`MetadataDetailView.vue`、`ContextList.vue`、`BrowserView.vue`、新增/扩展类型定义。
- 数据库：新增 `namespace_naming_convention` 关联表；迁移 `namespace` 单列外键；`naming_convention` 表新增标准属性列；`designation`/`definition` 表新增 `acceptability` 列。
- 测试与文档：补充 JPA 关系测试、约束单元测试、前端组件测试；更新对比报告。
