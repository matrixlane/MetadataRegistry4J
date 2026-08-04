# namespace-ui-form Specification

## Purpose
TBD - created by archiving change align-namespace-ui. Update Purpose after archive.
## Requirements
### Requirement: 表单必须按 ISO 治理属性全字段录入

「命名空间」新建/编辑表单 MUST 暴露并支持编辑以下字段，分三组展示：
- **基础信息组**：`designations`（多语言指称）、`definitions`（多语言定义）
- **ISO 治理属性组**：`shorthandPrefix`（简写前缀）、`schemeReference`（方案引用 IRI）、`mandatoryNamingConventionIndicator`、`oneNamePerItemIndicator`、`oneItemPerNameIndicator`
- **关联引用组**：`namingAuthorityId`（命名机构，Organization 下拉）、`namingConventionIds`（命名规范，多选）、`parentId`（父命名空间，Namespace 下拉）

#### Scenario: 新建命名空间成功提交
- **WHEN** 用户打开「新建」表单，填写中文名称与英文名称各一行，填写 `shorthandPrefix = "my-app"`，绑定一个 Organization，一个 NamingConvention，提交
- **THEN** 系统调用 `POST /api/v1/namespaces` 提交完整 `NamespaceRequest`，返回的 `NamespaceResponse` 在列表中可见，三个 indicator 列默认值（false/false/false）显示

#### Scenario: 编辑回填完整字段
- **WHEN** 用户点击列表中已有命名空间的「编辑」按钮
- **THEN** 系统调用 `GET /api/v1/namespaces/{id}/detail` 拉取 `NamespaceDetailResponse`，表单回填全部字段（包括 `parentId`、`namingConventionIds`、`shorthandPrefix`、`schemeReference`、三个 indicator），且不显示 `meta.identifier` 字段

#### Scenario: mandatoryNamingConventionIndicator 开启时强制要求至少一个命名规范
- **WHEN** 用户将 `mandatoryNamingConventionIndicator` 切换为 `true`，但 `namingConventionIds` 为空
- **THEN** 表单校验失败，提示「开启强制命名规范必须至少绑定一个命名规范」

### Requirement: 表单不显示 Namespace 自身标识符

「命名空间」表单 MUST NOT 暴露 Namespace 自身 `meta.identifier` 字段。标识符在新建时由后端自动生成；编辑时从后端响应中读取并随提交体一并回传，但不展示在 UI 上。

#### Scenario: 编辑时标识符字段不出现
- **WHEN** 用户打开任意已存在 Namespace 的编辑表单
- **THEN** 表单中不存在名为「标识符」的可编辑输入项，但提交请求体包含 `registration.identifier = <原标识符>` 与 `meta.identifier` 一致

#### Scenario: 提交时携带原标识符
- **WHEN** 用户编辑 Namespace 后保存
- **THEN** 提交到 `PUT /api/v1/namespaces/{id}` 的 `NamespaceRequest` 中 `registration.identifier` 与编辑前的 `meta.identifier` 保持一致，后端不会因为缺标识符报错

### Requirement: 多语言名称/定义复用既有编辑器组件

表单中 `designations` / `definitions` 字段 MUST 使用既有 `DesignationEditor` / `DefinitionEditor` 组件进行多语言录入，禁止自建简化版。

#### Scenario: designations 多行编辑
- **WHEN** 用户在「基础信息」组中点击「添加指称」
- **THEN** 出现新行，包含「语言下拉」与「输入框」，用户可输入多个语言版本的指称；语言下拉数据来源于 `GET /api/v1/languages`

#### Scenario: definitions 多行编辑
- **WHEN** 用户在「基础信息」组中点击「添加定义」
- **THEN** 出现新行，可输入多语言版本定义

### Requirement: 关联引用下拉候选项

- `namingAuthorityId` 下拉候选项 MUST 来源于 `GET /api/v1/organizations?size=200`，展示 `name` 与 `identifier`
- `namingConventionIds` 多选候选项 MUST 来源于 `GET /api/v1/naming-conventions?size=200`，展示 `label`
- `parentId` 下拉候选项 MUST 来源于 `GET /api/v1/namespaces?size=200`，展示 `label`，并 MUST 过滤掉「自身」以及「所有 `parentId` 链最终指向自身的候选」（防循环引用）

#### Scenario: 编辑时父命名空间下拉排除自身
- **WHEN** 用户打开 Namespace A 的编辑表单
- **THEN** `parentId` 下拉的可选项中不包含 A 自身，且不包含 A 的所有后代

#### Scenario: 强制循环引用防护
- **WHEN** 用户在客户端绕过下拉过滤通过其他途径尝试将 parentId 设为自身
- **THEN** 表单提交前校验失败，提示「不能将命名空间设为自身的父级」

### Requirement: 删除前关联性检查

「命名空间」列表的删除按钮 MUST 在点击后调用 `GET /api/v1/namespaces/{id}/deletion-check`，并根据响应内容做差异化提示。

#### Scenario: 存在子命名空间或标识符
- **WHEN** 后端返回 `{ hasIdentifiers: true, count: 3, message: "..." }`
- **THEN** 弹窗提示包含「该命名空间下还有 3 个标识符 / 子命名空间，确认删除？」

#### Scenario: 无关联内容
- **WHEN** 后端返回 `{ hasIdentifiers: false, count: 0 }`
- **THEN** 弹窗显示默认确认「确认删除？」

### Requirement: 表单与后端数据契约一致

表单提交时 MUST 提交完整的 `NamespaceRequest` 结构（`designations: TextLine[]`、`definitions: TextLine[]`、`registration: ItemRegistrationRequest`、`namingAuthorityId`、`parentId`、`shorthandPrefix`、`schemeReference`、`mandatoryNamingConventionIndicator`、`namingConventionIds`、`oneNamePerItemIndicator`、`oneItemPerNameIndicator`），禁止再使用旧的 `{ identifier, titleZh, titleEn }` 平铺结构。

#### Scenario: 新建请求体包含全部治理字段
- **WHEN** 用户在表单填写完整后点击保存
- **THEN** 提交到后端的 JSON 包含上述 11 个字段名（即使为空值也保留为字段），后端 `@NotEmpty` 校验通过

### Requirement: 命名空间性质展示与设置分支

命名空间表单/设置 MUST 展示命名空间性质（自有/外部），性质在创建时确定且 MUST NOT 提供变更入口。自有命名空间的设置 SHALL 包含标识生成策略（段规则设计器入口）与版本生成策略（自动化选项）；外部命名空间的设置 SHALL 呈现手动编辑策略说明与可选的格式校验配置，MUST NOT 提供自动生成配置入口。

#### Scenario: 自有命名空间设置

- **WHEN** 打开自有命名空间的设置
- **THEN** 显示性质"自有"，提供段规则设计器入口与版本策略选项（SEMVER/SEQUENTIAL/DATE）

#### Scenario: 外部命名空间设置

- **WHEN** 打开外部命名空间的设置
- **THEN** 显示性质"外部"，仅提供手动录入说明与可选的正则校验规则配置（含样例即时测试），无自动生成配置项

#### Scenario: 性质不可变更

- **WHEN** 编辑既有命名空间
- **THEN** 性质字段只读展示，表单无变更入口

