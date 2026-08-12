## ADDED Requirements

### Requirement: 所有 Item 子类的创建/编辑流程使用统一的指称/定义编辑区
系统 SHALL 在所有 Item 子类（如 DataElement、ConceptualDomain、ValueDomain、Namespace、NamingConvention 等）的创建与编辑对话框中，使用同一套指称/定义编辑能力，确保用户交互、校验规则与默认值一致。

#### Scenario: 数据元创建流程使用统一编辑区
- **WHEN** 用户打开数据元创建对话框
- **THEN** 对话框 SHALL 显示包含指称、定义、上下文关联的编辑区，且语言字段默认填充注册表默认语言

#### Scenario: 概念域创建流程使用统一编辑区
- **WHEN** 用户打开概念域创建对话框
- **THEN** 对话框 SHALL 使用与数据元相同的指称/定义编辑区

#### Scenario: 值域创建流程使用统一编辑区
- **WHEN** 用户打开值域创建对话框
- **THEN** 对话框 SHALL 使用与数据元相同的指称/定义编辑区

#### Scenario: 命名空间创建流程使用统一编辑区
- **WHEN** 用户打开命名空间创建对话框
- **THEN** 命名空间表单中的名称/描述区域 SHALL 使用统一编辑区，并保留其 governance/identifier/version 等自定义 tab

#### Scenario: 命名规范创建流程使用统一编辑区
- **WHEN** 用户打开命名规范创建对话框
- **THEN** 表单 SHALL 使用统一编辑区作为名称/描述输入

### Requirement: 统一编辑区支持 Item 抽象层上下文与命名空间语义
统一编辑区 SHALL 支持为指称/定义添加上下文（Context）关联，并接受由父表单提供的命名空间（Namespace）信息，用于命名规范校验等场景。

#### Scenario: 上下文关联在统一编辑区中可用
- **WHEN** 用户在编辑区展开某条指称/定义的上下文区域
- **THEN** 系统 SHALL 提供 ContextSelector 和 AcceptabilitySelect 供用户选择上下文与可接受性

#### Scenario: 父表单传入命名空间后编辑区参与命名规范校验
- **WHEN** 父表单向编辑区传入 `namingConventionOptions` 和 `requireNamingConventions`
- **THEN** 指称行 SHALL 显示适用的命名规范选择，并在 `requireNamingConventions=true` 时校验至少选择一项

### Requirement: 过渡期间 MultilingualTextEditor 同步支持默认语言与可选语言
在 `ItemFormDialog.vue` 尚未整体切换为统一编辑区时，`MultilingualTextEditor` SHALL 支持 `defaultLanguageId` prop，并允许语言、来源文档为空。

#### Scenario: 数据元表单中的 MultilingualTextEditor 使用默认语言
- **WHEN** 用户在数据元创建表单的指称 tab 中点击“添加指称”
- **THEN** 新行 SHALL 默认填充 `defaultLanguageId`；用户清空时 SHALL 保留为可选项，提交前自动补回默认值

#### Scenario: 来源文档保持可选
- **WHEN** 用户在 `MultilingualTextEditor` 的某行中未选择来源文档
- **THEN** 系统 SHALL 允许提交，且该行的 `sourceId` 为 `null`
