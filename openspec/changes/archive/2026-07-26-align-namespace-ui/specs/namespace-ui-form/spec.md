## Purpose

定义「命名空间管理」页面中「新建 / 编辑」表单的能力边界：表单必须按 ISO/IEC 11179-3:2023 暴露并校验后端支持的全部可编辑字段；表单不显示 Namespace 自身标识符（由后端生成）；表单不涉及 ScopedIdentifier 录入。

## ADDED Requirements

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
