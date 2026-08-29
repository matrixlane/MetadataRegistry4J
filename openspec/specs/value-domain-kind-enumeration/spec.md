# value-domain-kind-enumeration Specification

## Purpose
TBD - created by archiving change domain-three-tier-ui. Update Purpose after archive.
## Requirements
### Requirement: 值域（Value_Domain）创建与编辑采用两级分类选择器

新建或编辑值域时，前端 MUST 呈现两级联动的分类选择器，将 ISO/IEC 11179-31:2023 §7.4.2 中的 Value_Domain 三层元模型暴露为两个连续步骤。第一级 `kind` 必选（`DESCRIBED` / `ENUMERATED`）；第二级 `enumKind` 仅当一级为 `ENUMERATED` 时显示且必选（`LOCAL` / `REFERENCE`）。两级选择器 MUST 与提交体中的 `type` 字段保持如下映射：`DESCRIBED` → `DESCRIBED`；`ENUMERATED + LOCAL` → `LOCAL_ENUMERATED`；`ENUMERATED + REFERENCE` → `REFERENCE_ENUMERATED`。

#### Scenario: 创建态默认进入描述型

- **WHEN** 用户在值域列表点击"新建"打开对话框
- **THEN** 第一级选择器 MUST 默认为 `DESCRIBED`，第二级选择器 MUST 隐藏；表单 MUST 渲染描述型概念域选择器；提交体 `type` MUST 为 `DESCRIBED`

#### Scenario: 切换一级到枚举型出现二级选择器

- **WHEN** 用户将第一级从 `DESCRIBED` 切换到 `ENUMERATED`
- **THEN** 第二级选择器 MUST 立即出现并默认为 `LOCAL`；表单 MUST 渲染"许可值"编辑卡片；提交体 `type` MUST 变为 `LOCAL_ENUMERATED`

#### Scenario: 二级从本地切换到引用触发外部源卡片

- **WHEN** 用户在第一级为 `ENUMERATED` 时把第二级从 `LOCAL` 切换到 `REFERENCE`
- **THEN** 许可值编辑卡片 MUST 立即被替换为"外部源"卡片，包含"外部源字段名"输入与"外部源概念域"选择器；提交体 `type` MUST 变为 `REFERENCE_ENUMERATED`；既有 `permissibleValues` 数组 MUST 在切换瞬间被清空

#### Scenario: 编辑既有引用枚举值域反查两级初值

- **WHEN** 用户点击列表中类型为 `REFERENCE_ENUMERATED` 的行打开编辑对话框
- **THEN** 第一级选择器 MUST 自动设为 `ENUMERATED`，第二级选择器 MUST 自动设为 `REFERENCE`，外部源卡片 MUST 渲染并回填 `permittedValuesFieldname` 与 `referencedConceptualDomainId`

### Requirement: 概念域（Conceptual_Domain）创建与编辑采用两级分类选择器

新建或编辑概念域时，前端 MUST 呈现与值域同构的两级联动分类选择器。一级 `kind`（`DESCRIBED` / `ENUMERATED`）；二级 `enumKind` 仅一级为 `ENUMERATED` 时显示（`LOCAL` / `REFERENCE`）。提交 `type` 映射：`DESCRIBED` → `DESCRIBED`；`ENUMERATED + LOCAL` → `LOCAL_ENUMERATED`；`ENUMERATED + REFERENCE` → `REFERENCE_ENUMERATED`。

#### Scenario: 创建态默认进入描述型

- **WHEN** 用户在概念域列表点击"新建"打开对话框
- **THEN** 第一级选择器 MUST 默认为 `DESCRIBED`，第二级选择器 MUST 隐藏；表单 MUST 渲染"来源 URI"输入与概念域定义编辑区；提交体 `type` MUST 为 `DESCRIBED`

#### Scenario: 本地枚举型分支渲染值含义卡片

- **WHEN** 用户组合为 `LOCAL_ENUMERATED`
- **THEN** 表单 MUST 显示"值含义"编辑卡片（含增删 VM、有效期、设计/定义多语行）；其他卡片 MUST 不可见

#### Scenario: 引用枚举型分支无专属字段

- **WHEN** 用户组合为 `REFERENCE_ENUMERATED`
- **THEN** 表单 MUST NOT 渲染任何引用枚举专属卡片或字段（ISO/IEC 11179-31:2023 §7.4.2.6.4：Reference_Enumerated_Conceptual_Domain "has no additional attributes"）；值含义卡片 MUST 不可见

#### Scenario: 编辑既有引用枚举概念域反查两级初值

- **WHEN** 用户点击列表中类型为 `REFERENCE_ENUMERATED` 的概念域行打开编辑对话框
- **THEN** 第一级选择器 MUST 自动设为 `ENUMERATED`，第二级选择器 MUST 自动设为 `REFERENCE`

### Requirement: 概念域与值域 list 接口接受 `type` 过滤参数

`GET /api/v1/conceptual-domains` 与 `GET /api/v1/value-domains` MUST 接受可选 query param `type`，取值 `DESCRIBED` / `LOCAL_ENUMERATED` / `REFERENCE_ENUMERATED` / `ENUMERATED`（最后一个语义等价于"任何枚举子类型"）；不传 `type` 时 MUST 维持既有行为（不过滤）。

#### Scenario: 概念域按引用枚举过滤

- **WHEN** 客户端以 `GET /api/v1/conceptual-domains?type=REFERENCE_ENUMERATED` 调用
- **THEN** 后端 MUST 仅返回类型为 `REFERENCE_ENUMERATED` 的概念域行；其他类型 MUST NOT 出现

#### Scenario: 值域按枚举大类过滤

- **WHEN** 客户端以 `GET /api/v1/value-domains?type=ENUMERATED` 调用
- **THEN** 后端 MUST 返回 `LOCAL_ENUMERATED` 与 `REFERENCE_ENUMERATED` 两类行；`DESCRIBED` MUST NOT 出现

#### Scenario: 不传 type 不过滤

- **WHEN** 客户端以 `GET /api/v1/conceptual-domains` 调用（无 `type` 参数）
- **THEN** 后端 MUST 返回全部类型概念域（既有行为）

### Requirement: 数据元 / 数据元概念关联选择器展示 kind 标签并支持过滤

数据元（Data_Element）的"值域"picker 与数据元概念（Data_Element_Concept）的"概念域"picker MUST 在每条候选项 label 旁显示 kind 标签（"描述型" / "本地枚举型" / "引用枚举型"），picker 头部 MUST 提供按 kind 多选过滤；现有选择语义（单选 / 多选）不变。

#### Scenario: 数据元值域 picker 候选项显示 kind 标签

- **WHEN** 数据元表单加载完 `valueDomainApi.list({ size: 1000 })`
- **THEN** "值域"picker 的每条 `<el-option>` MUST 在 label 旁显示一个 kind `el-tag`（三色：info/success/warning 分别对应 描述型 / 本地枚举型 / 引用枚举型）；picker 头部 MUST 提供 kind 多选过滤

#### Scenario: 数据元概念概念域 picker 候选项显示 kind 标签

- **WHEN** 数据元概念表单加载完 `conceptualDomainApi.list({ size: 1000 })`
- **THEN** "概念域"picker 的每条 `<el-option>` MUST 在 label 旁显示 kind `el-tag`；picker 头部 MUST 提供 kind 多选过滤

#### Scenario: picker 头部 kind 多选过滤生效

- **WHEN** 用户在 picker 头部的 kind 过滤勾选"仅引用枚举型"
- **THEN** picker 候选 MUST 仅保留 `type === 'REFERENCE_ENUMERATED'` 的选项；其他类型 MUST NOT 出现

### Requirement: 数据元概念表单以 picker 替代 UUID 输入框

`DataElementConceptList.vue` 的"对象类"与"特性"输入 MUST 由 `<el-input>` 升级为 `<el-select>`，picker 加载失败时保留 `<el-input>` fallback。表单 MUST 新增"概念域"多选 picker（`conceptualDomainApi.list({ size: 1000 })`），支持 kind 标签化与多选过滤；提交时把 `objectClassId` / `propertyId` / `conceptualDomainIds` 交给后端。

#### Scenario: 对象类与特性改用 picker

- **WHEN** 数据元概念对话框加载完成 `objectClassApi.list({ size: 1000 })` 与 `propertyApi.list({ size: 1000 })`
- **THEN** "对象类"与"特性"输入 MUST 显示为可搜索下拉选择器（label 渲染为对象类/特性的名称）

#### Scenario: 概念域多选 picker 绑定 conceptualDomainIds

- **WHEN** 用户在"概念域"picker 多选 N 个概念域
- **THEN** 提交体 MUST 包含 `conceptualDomainIds: [UUID, ...]`（长度 N）；后端 MUST 在 DEC 持久化时按多对多重建 `dataElementConcepts` 关联

#### Scenario: 编辑既有 DEC 回填 conceptualDomainIds

- **WHEN** 用户点击列表中具有 2 个关联概念域的 DEC 行打开编辑对话框
- **THEN** "概念域"picker MUST 自动勾选这两条概念域（label 与 kind 标签均回显）

### Requirement: 概念域后端枚举与请求响应字段扩展

`ConceptualDomainType` MUST 包含 `DESCRIBED` / `LOCAL_ENUMERATED` / `REFERENCE_ENUMERATED` 三值。`ConceptualDomainRequest` / `ConceptualDomainResponse` MUST NOT 携带任何 Reference_Enumerated_Conceptual_Domain 专属字段（依据 §7.4.2.6.4，该子类无附加属性）；`permitted_values_fieldname` 仅属于 Reference_Enumerated_Value_Domain（§7.4.2.13.4 Table 7）。

#### Scenario: ConceptualDomainType 枚举响应包含三个值

- **WHEN** 客户端读取概念域列表
- **THEN** 任一返回行的 `type` 字段 MUST 严格属于 `{DESCRIBED, LOCAL_ENUMERATED, REFERENCE_ENUMERATED}`；前端类型列 MUST 按该枚举渲染"描述型"/"本地枚举型"/"引用枚举型"三个标签

