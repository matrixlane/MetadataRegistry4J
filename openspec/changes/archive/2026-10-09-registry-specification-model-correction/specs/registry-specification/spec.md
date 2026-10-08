# Delta: registry-specification

## MODIFIED Requirements

### Requirement: Registry_Specification class exists
The system SHALL provide a `Registry_Specification` entity class as specified in ISO/IEC 11179-3:2023, 9.4.10. Per the standard, Registry_Specification has no declared superclass (its class section specifies Description, Associations — "No associations are specified in this metamodel region." — and Attributes in Table 28 only); it MUST therefore be modelled as a standalone root entity and MUST NOT extend `Item` nor inherit Item's identification/designation/definition/slot/administration machinery. The JPA mapping MUST NOT produce a foreign key from `registry_specification.id` to `item.id`, and persisting a registry specification MUST NOT create a row in the `item` base table.

#### Scenario: Model conformance
- **WHEN** inspecting the common-facilities domain model
- **THEN** `Registry_Specification` is a JPA entity whose direct superclass is the mapped-superclass `Entity` (identifier only), not `Item`

#### Scenario: No item base row nor FK
- **WHEN** a registry specification is persisted and the database schema is inspected
- **THEN** no row exists in the `item` table for the specification's identifier
- **AND** no foreign key constraint from `registry_specification` to `item` exists

### Requirement: Registry Specification Configuration Step in Initialization Wizard
初始化向导 MUST 包含注册规范配置步骤，位于数据库配置步骤之后、注册机构步骤之前（步骤顺序：版本 → 数据库 → 注册规范 → 注册机构 → 命名空间 → 管理员密码），开放编辑 `Registry_Specification`（ISO/IEC 11179-3:2023 表 28）的标量属性：name（必填）、comment、web_address、character_repertoire（必填）、standard、degree_of_conformance（conforming / strictly_conforming）、conformance_level、conformance_label、included_features、excluded_features、default_time_zone（[-+]HHMM）、default_language、alternative_language（0..*）、reference_document_identifier_kind（0..*）、representation_class_scheme_id。各属性 MUST 预填系统默认值；上下文（context）编辑不在向导内提供（初始化后于设置页维护）。注册规范不提供指称/定义编辑（标准表 28 无此属性，且该类不继承 Item 的指称/定义机制）。

#### Scenario: 向导中配置注册规范并带入 setup
- **WHEN** 超级管理员在注册规范步骤修改字符集、标准、合规程度、时区、替代语言等属性并完成后续初始化
- **THEN** System SHALL 按所填值创建注册规范，初始化完成后设置页回显一致

#### Scenario: 向导中保留默认值提交
- **WHEN** 超级管理员不修改注册规范步骤的预填默认值直接提交
- **THEN** System SHALL 以默认值创建注册规范（字符集 UTF-8、标准 ISO/IEC 11179-3:2023、默认语言 zh-CN、时区 +0800）

### Requirement: Registry Specification Full Attribute Editing in Settings
注册规范设置页的编辑界面 MUST 开放 `Registry_Specification`（ISO/IEC 11179-3:2023 表 28）全部属性的编辑：name、comment、web_address、default_language、alternative_language（0..*）、character_repertoire、reference_document_identifier_kind（0..*）、representation_class_scheme_id、context、standard、degree_of_conformance（conforming / strictly_conforming）、conformance_level、conformance_label、included_features、excluded_features、default_time_zone（[-+]HHMM）。其中 name 与 character_repertoire 为必填。注册规范 MUST NOT 提供指称（designation）、定义（definition）或注册记录（registration/标识符/版本）编辑——这些能力来自 Item 继承，而标准 9.4.10 未声明 Registry_Specification 为 Item 子类，表 28 亦无对应属性；对应 API 字段（`designations`、`definitions`、`registration`、`label`、`meta`）MUST NOT 存在于请求/响应契约中。

#### Scenario: 编辑注册规范并保存全部属性
- **WHEN** 注册管理员在设置页打开注册规范编辑对话框，填写替代语言、参考文档标识种类、表示类方案标识、上下文等属性并保存
- **THEN** 系统持久化全部属性，重新打开时回显一致

#### Scenario: 非法时区格式被拒绝
- **WHEN** 提交的 default_time_zone 不匹配 [-+]HHMM 格式（如 "800"、"+08:00"）
- **THEN** 系统返回 400 业务校验错误；前端在失焦时即提示格式错误且不提交

#### Scenario: 指称与定义支持任意语言
- **WHEN** 注册管理员在保存注册规范的请求体中携带 `designations` 或 `definitions` 字段（任意语言与上下文）
- **THEN** 契约中不存在这两个字段：服务端不持久化指称/定义数据且响应不包含二者；设置页不提供指称/定义编辑入口（标准 9.4.10 未声明该能力）

### Requirement: Default Registry Specification Configured During System Initialization
在系统初始化阶段，系统 MUST 在 setup 阶段自动创建并启用一个默认注册规范（`Registry_Specification`）——注册规范是注册表的固有组成部分（ISO/IEC 11179-3:2023 §9.4.10），不可跳过；显式请求不创建（`createRegistrySpecification=false`）MUST 被拒绝。初始化向导 MUST 提供注册规范配置步骤，开放表 28 标量属性的编辑：name、comment、web_address、character_repertoire（必填）、standard、degree_of_conformance、conformance_level、conformance_label、included_features、excluded_features、default_time_zone（[-+]HHMM）、default_language、alternative_language（0..*）、reference_document_identifier_kind（0..*）、representation_class_scheme_id；各属性 MUST 以系统默认值预填（如字符集 UTF-8、标准 ISO/IEC 11179-3:2023、默认语言 zh-CN、时区 +0800），留空提交时由后端按版本规则兜底。上下文（context）编辑不在向导内提供（初始化后于设置页维护）。注册规范不提供指称/定义编辑（标准表 28 无此属性）。

#### Scenario: 初始化向导中配置注册规范全部属性
- **WHEN** 超级管理员在注册规范步骤填写字符集、标准、合规程度、时区、替代语言等属性并进入下一步
- **THEN** System SHALL 在 setup 时按所填值创建注册规范，初始化完成后设置页回显一致

#### Scenario: 注册规范属性留空使用默认值
- **WHEN** 超级管理员不修改注册规范步骤的预填默认值（或清空可选字段）直接提交
- **THEN** System SHALL 以默认值创建注册规范（字符集 UTF-8、标准 ISO/IEC 11179-3:2023、默认语言 zh-CN、时区 +0800 等），PERSONAL 版名称缺省取"默认注册规范"

#### Scenario: 非法时区被初始化向导拒绝
- **WHEN** 提交的 default_time_zone 不匹配 [-+]HHMM 格式
- **THEN** System SHALL 返回 400 业务校验错误，前端在失焦时即提示且不提交

#### Scenario: Explicitly refuse to create Registry Specification during initialization
- **WHEN** 初始化 setup 请求显式携带 `createRegistrySpecification=false`
- **THEN** System SHALL 拒绝该请求（400 Bad Request），提示注册规范为必选项

## ADDED Requirements

### Requirement: 持久化注册规范不产生 Item 基行
注册规范持久化路径（初始化向导 createDefault、设置页 create/update）MUST 仅写入 `registry_specification` 及其语言关联表；系统 MUST NOT 为注册规范创建 `item` 基行、`designation` 行或任何 administered item 结构。注册规范 MUST NOT 出现在联邦迁移条目流与全局元数据搜索结果中。

#### Scenario: 向导创建后数据库形态
- **WHEN** 初始化向导创建默认注册规范后检查数据库
- **THEN** `registry_specification` 表存在该记录，`item`/`designation`/`administered_item` 表中不存在以该 id 关联的行

#### Scenario: 联邦迁移不包含注册规范
- **WHEN** 构建联邦迁移包并收集条目
- **THEN** 条目流中不包含任何注册规范记录（无需特殊过滤即天然排除）

#### Scenario: 全局搜索不出现注册规范
- **WHEN** 客户端请求全局元数据搜索（含显式指定类型 RegistrySpecification）
- **THEN** 结果集中不包含注册规范记录；`RegistrySpecification` 不在搜索类型白名单中
