## Context

ISO/IEC 11179-3:2023 §8 定义了 **Designation_and_Definition package**，其核心概念如下：

- **Designation**（指称）：一个语言符号（Sign），在特定语言和上下文中引用某个概念（Item），并可携带可接受性（Acceptability）标注
- **Definition**（定义）：一种文本性表达，在特定语言和上下文中描述某 Item 的概念内容，同样带可接受性
- **DesignationDefinitionPairing**（指称-定义配对）：将一条指称与一条定义在特定上下文（Context）中关联，并标注可接受性
- **Context**（上下文）：决定指称/定义适用域的限定因子
- **Namespace**（命名空间）：标识系统的作用域，Designation 可关联多个 Namespace

### ISO/IEC 11179-3:2023 §8 标准对齐说明

根据对标准文档及相关元模型的重新研究，该 package 核心要素的对齐规则与约束如下：

1. **可接受性枚举（Acceptability ratings, 8.7.1）**：
   - 规定了指称或定义在特定上下文中的可接受性等级，共有以下 5 个等级：
     - `preferred` (首选)：最佳的选择。
     - `admitted` (认可)：可以接受的选择，但不如 preferred。
     - `deprecated` (已弃用)：不再被认为可以接受，但目前仍在继续使用。
     - `superseded` (已取代)：已被另一个更优的选择所代替。
     - `obsolete` (已过时)：不再使用，且尚未被取代。
   - **唯一性约束**：在特定的语言/上下文环境下，只能有一个指称或定义被标记为首选（`preferred`）。

2. **多上下文关联（8.5.1, 8.5.2）**：
   - `Designation` 通过 `Designation_Context` 关系类与 Context 关联（包含 `acceptability` 属性），基数为 `0..*`。
   - `Definition` 通过 `Definition_Context` 关系类与 Context 关联（包含 `acceptability` 属性），基数为 `0..*`。
   - 这表明指称和定义在标准模型中均支持**多上下文关联**。前端组件 `DesignationEditor.vue` 和 `DefinitionEditor.vue` 应提供对多上下文及其对应可接受性（AcceptabilitySelect）的选择与绑定能力。

3. **文献来源关联（Source, 8.4.1, 8.4.2）**：
   - 标准中 `Designation` 与 `Definition` 均包含可选的 `source` 属性，指向 `Reference_Document`（文献引用）。
   - 系统后端在 DTO 与实体模型中均已对此予以支持（`sourceId`）。前端重构中，`TextLineForm` 类型中也保留了 `sourceId?: string` 属性以实现向上兼容。目前为简化 UI 交互，编辑器界面暂不提供对文献来源的显式编辑框，未来可在行卡片内独立扩展文献选择器。

当前代码库中，上述概念的编辑 UI 分散于：
- `MultilingualTextEditor.vue`（处理指称和定义，语义混用，组件名称不够精准）
- `DesignationDefinitionPairingEditor.vue`（处理配对，但内联了 Context 选择和 Acceptability 选择逻辑）
- `ItemFormDialog.vue`（整合上述两组件，通过 tabs 管理）
- `NamespaceList.vue`（独立内联使用 MultilingualTextEditor）
- `ConceptualDomainList.vue`、`ValueDomainList.vue`（同上）

问题：各调用点行为和样式一致性依赖调用者自身约束，没有统一入口，难以保持标准合规性。

## Goals / Non-Goals

**Goals:**
- 将每个 ISO 11179 §8 概念对应一个精准命名的可复用 Vue 组件
- 消除 `MultilingualTextEditor` 中指称/定义语义混用
- 所有调用方通过统一组件接口实现行为和样式一致
- 组件拆分后仍保持向上兼容（类型接口不变）
- 提供 `DesignationDefinitionSection` 复合组件，使简单场景一行即可完成集成

**Non-Goals:**
- 不修改后端 API 或数据模型
- 不引入新的状态管理层（Pinia store）
- 不改变 `TextLineForm` / `PairingForm` / `TextLineContextForm` 类型定义
- 不修改路由结构

## Decisions

### 决策 1：保留 `MultilingualTextEditor.vue` 还是拆分？

**选择：拆分为 `DesignationEditor.vue` 和 `DefinitionEditor.vue`**

**理由：** 指称（Designation）的核心是 **Sign**（符号），是对概念的"称呼"；定义（Definition）的核心是**文本描述**，是对概念的"解释"。两者虽然数据结构相同（`TextLineForm`），但 ISO 11179 在语义上是不同的概念，应有各自独立的入口点，便于未来独立扩展（例如：指称可能需要"简称"标记，定义可能需要"规范性来源"字段）。

保留 `MultilingualTextEditor.vue` 作为底层实现基础（二者复用同一底层渲染逻辑），通过 `DesignationEditor` 和 `DefinitionEditor` 各自封装并声明不同的 `label` 默认值与验证语义。

### 决策 2：`ContextSelector` 是否独立为组件？

**选择：是，独立为 `ContextSelector.vue`**

**理由：** Context 选择（远程搜索 + Acceptability 配对）在指称行内、定义行内、配对行内均需复用，提取为独立组件可避免三处各自内联 `contextApi.options` 调用，统一 loading 状态和显示样式。

### 决策 3：`AcceptabilitySelect` 是否独立？

**选择：是，独立为 `AcceptabilitySelect.vue`**

**理由：** Acceptability 枚举有固定 5 个值（PREFERRED/ADMITTED/DEPRECATED/SUPERSEDED/OBSOLETE），在多处重复硬编码，独立封装后枚举标签的翻译（如 i18n 扩展）只需改一处。

### 决策 4：是否需要 `DesignationDefinitionSection` 复合组件？

**选择：是，新增复合组件**

**理由：** `ItemFormDialog` 的 Tabs 结构对于命名空间、概念域等简单场景过于复杂。提供一个单组件封装"指称 + 定义 + 配对"三段式编辑区，使 `NamespaceList` 等场景可以直接 `<DesignationDefinitionSection>` 替代原有三块独立代码。`ItemFormDialog` 内部也可选择性引用此复合组件或继续使用独立 tabs 布局（灵活度更高）。

### 决策 5：原 `MultilingualTextEditor.vue` 文件去向

**选择：重构为内部基础组件，不对外暴露，更名为 `TextLineEditor.vue`（或直接内联到 DesignationEditor/DefinitionEditor）**

由于 `DesignationEditor` 和 `DefinitionEditor` 的模板结构几乎相同（仅 label 和校验消息不同），最简方案是将两者通过 `label` prop 参数化——实际上 `MultilingualTextEditor` 已有此设计，因此可直接在 `DesignationEditor.vue` 和 `DefinitionEditor.vue` 中各自 `export default { ... }` 包装，内部复用 `MultilingualTextEditor` 的渲染逻辑（或直接继承其模板）。

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|----------|
| 重构调用方时遗漏某个视图文件 | 执行阶段用 grep 全量扫描 `MultilingualTextEditor` 引用，逐一更新 |
| `DesignationEditor` 与 `DefinitionEditor` 模板高度重复 | 两者均包装同一 `MultilingualTextEditor`（仅 label/i18n-key 不同），不实际重复逻辑代码 |
| `DesignationDefinitionSection` 与 `ItemFormDialog` 中的 tabs 布局产生职责重叠 | 明确约定：`DesignationDefinitionSection` 用于无 tabs 场景；`ItemFormDialog` 内部继续独立使用三个子组件 |
| 新旧组件同时存在导致过渡期混乱 | 单次 PR 完成全部调用方迁移，不保留旧接口 |
