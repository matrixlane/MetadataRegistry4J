# Registry Specification Capability Specification

## Purpose
This capability defines the core rules and settings that govern how the metadata registry operates (e.g., identifier generation, versioning policy, mandatory classification, and default language). It enforces data integrity, controls system initialization steps, and acts as an admission gate for data governance and metadata registration.

## Requirements

### Requirement: Default Registry Specification Configured During System Initialization
在系统初始化阶段，系统 MUST 在 setup 阶段自动创建并启用一个默认注册规范（`Registry_Specification`）——注册规范是注册表的固有组成部分（ISO/IEC 11179-3:2023 §9.4.10），不可跳过；显式请求不创建（`createRegistrySpecification=false`）MUST 被拒绝。初始化向导 MUST 提供注册规范配置步骤，开放表 28 标量属性的编辑：name、comment、web_address、character_repertoire（必填）、standard、degree_of_conformance、conformance_level、conformance_label、included_features、excluded_features、default_time_zone（[-+]HHMM）、default_language、alternative_language（0..*）、reference_document_identifier_kind（0..*）、representation_class_scheme_id；各属性 MUST 以系统默认值预填（如字符集 UTF-8、标准 ISO/IEC 11179-3:2023、默认语言 zh-CN、时区 +0800），留空提交时由后端按版本规则兜底。上下文（context）与指称/定义的多语言编辑不在向导内提供（初始化后于设置页维护）。

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

---

### Requirement: Management Permission for Registry Specification
系统中的任何注册管理员（Registry Administrator）MUST 均有权创建和更新注册规范。非注册管理员角色用户 MUST NOT 拥有操作权限。

#### Scenario: Non-registry administrator attempts to create or edit Registry Specification
- **WHEN** User without registry administrator role attempts to save a Registry Specification
- **THEN** System SHALL deny the request with an access denied (403 Forbidden) response

#### Scenario: Registry administrator creates Registry Specification successfully
- **WHEN** User with registry administrator role submits valid Registry Specification settings
- **THEN** System SHALL save the Registry Specification and log an audit trail event

---

### Requirement: 注册规范唯一且初始化后禁止增删
注册规范（Registry_Specification）为注册表的固有组成部分，全系统 MUST 有且仅有一条。任何在已存在记录时提交的新增请求 MUST 被拒绝并返回业务校验错误（400 Bad Request）；系统初始化完成后，注册规范 MUST NOT 被删除：后端 MUST 拒绝单条删除请求（400），批量删除经同一删除接口逐条执行，MUST 同样逐条被拒绝。前端 MUST 在注册规范设置页隐藏新增、删除与批量删除入口，仅保留编辑更新。系统未初始化（初始化向导草稿阶段）时：新增仅由初始化向导经 createDefault 创建（setup 前清空业务数据保证唯一），删除请求 MAY 放行。

#### Scenario: 已存在注册规范时再次新增
- **WHEN** 系统中已存在一条注册规范记录，用户提交新增注册规范请求（任意入口含 API 直连）
- **THEN** System SHALL 拒绝请求并返回 400 业务校验错误，提示注册规范有且仅有一条
- **AND** 既有注册规范记录保持不变

#### Scenario: 初始化完成后删除注册规范
- **WHEN** 系统已初始化，用户提交删除注册规范请求（单条或批量）
- **THEN** System SHALL 拒绝请求并返回 400 业务校验错误，提示初始化后注册规范不允许删除
- **AND** 注册规范记录保持不变

#### Scenario: 初始化完成后注册规范设置页仅保留编辑入口
- **WHEN** 系统已初始化，用户打开注册规范设置页
- **THEN** 不出现"新建"按钮，列表行操作列不出现"删除"按钮，且不提供批量删除（多选删除）入口，仅保留"编辑"

#### Scenario: 未初始化（向导草稿阶段）删除注册规范
- **WHEN** 系统尚未初始化，用户提交删除注册规范请求
- **THEN** System SHALL 按既有删除逻辑放行（初始化向导草稿数据允许清理重建）

#### Scenario: 初始化向导重复提交后仍仅一条注册规范
- **WHEN** 超级管理员重复提交初始化向导 setup（前次草稿未提交 commit）
- **THEN** System SHALL 先清空业务数据再经 createDefault 创建，最终系统中仍仅存在一条注册规范

---

### Requirement: Admission Gate for Projects and Metadata Registry
只有在注册规范已创建并启用后，本注册表 MUST 才正式允许数据治理工作的开展（即允许创建注册项目 `Project`，以及注册或导入任何元数据项 `Administered Item`）。

#### Scenario: Attempt to create project before Registry Specification is created
- **WHEN** User attempts to create a new registration project or registry item, and no Registry Specification exists in the system
- **THEN** System SHALL block the operation with a business exception (400 Bad Request) indicating that a Registry Specification must be configured first
- **AND** Front-end UI SHALL block/disable the "Create Project" and "Register Metadata" buttons, showing a guide page to set up the Registry Specification

---

### Requirement: Support English Name for Registration Authority in Initialization Wizard
在系统初始化引导向导中，系统 SHALL 允许超级管理员输入注册机构的英文名称，并将中英文名称一同保存。

#### Scenario: Initializing system with both Chinese and English Registration Authority names
- **WHEN** Super Admin initializes the system and inputs both Chinese name and English name for the Registration Authority
- **THEN** The system SHALL create the Registration Authority entity with both names populated in its name list

---

### Requirement: Display Registration Authorities in Organization List
在组织管理界面中，系统 SHALL 将所有已创建的注册机构（Registration Authority）与普通组织一同在列表中显示。

#### Scenario: Registration Authority displayed in Organization list with correct columns
- **WHEN** A Registration Authority exists in the database
- **AND** User views the Organization list page
- **THEN** The Registration Authority SHALL be included in the list
- **AND** The list column "名称" SHALL display the Registration Authority's Chinese name
- **AND** The list column "标识符" SHALL display the Registration Authority's Organization Identifier

---

### Requirement: 单注册表初始化与注册管理员联系人生成
在系统初始化单注册表向导中，超级管理员成为注册管理员后，系统 MUST 仅为该超级管理员生成一条 `Registrar` 联系人记录，而 MUST NOT 自动生成对应的 `Individual` 个人记录。该联系人记录在联系人列表中显示为“注册管理员”角色，关联对应的注册机构（organization）及业务角色，但其 `individual` 字段暂时为空。直到用户在“我的”页面中创建个人资料后，才会生成 `Individual` 记录并关联至该联系人。

#### Scenario: 单注册表初始化完成后的联系人与个人记录状态
- **WHEN** 超级管理员完成单注册表初始化并成为注册管理员，但尚未在“我的”中创建个人资料
- **THEN** 联系人列表中出现且仅出现一条该注册管理员的 `Contact` 记录
- **AND** 该 `Contact` 记录的业务角色为“注册管理员”，且关联对应的注册机构
- **AND** 该 `Contact` 记录的个人信息（`individual` 属性）为空
- **AND** 个人（`Individual`）列表中不会出现与此超级管理员相关的任何记录

---

### Requirement: 数据库 Schema 统一变更为 mdr
系统所有的数据库表及其数据对象 MUST 统一使用 `mdr` 作为 Schema 名称。

#### Scenario: 数据库表访问验证
- **WHEN** 系统执行 any 数据库查询或迁移
- **THEN** 所有的表（如 `account`, `individual`, `system_setting` 等）都在 `mdr` Schema 下进行读取和写入（如 `mdr.account`）

### Requirement: Registry_Specification class exists
The system SHALL provide a `Registry_Specification` entity class that is a subclass of `Item`, as specified in ISO/IEC 11179-3:2023, 9.4.10.

#### Scenario: Model conformance
- **WHEN** inspecting the common-facilities domain model
- **THEN** `Registry_Specification` extends `Item` and is mapped as a JPA entity

### Requirement: Registry_Specification attributes
The system SHALL store all attributes specified in ISO/IEC 11179-3:2023, 9.4.10.3 / Table 28: `name`, `comment`, `web_address`, `default_language`, `alternative_language`, `character_repertoire`, `reference_document_identifier_kind`, `representation_class_scheme_id`, `context`, `standard`, `degree_of_conformance`, `conformance_level`, `conformance_label`, `included_features`, `excluded_features`, and `default_time_zone`.

#### Scenario: Required attributes
- **WHEN** a registry specification is created
- **THEN** `name` and `character_repertoire` are mandatory

### Requirement: Registry_Specification REST API
The system SHALL expose CRUD endpoints under `/api/v1/registry-specifications` for managing registry specifications, including list, create, read, update, and delete operations.

#### Scenario: List registry specifications
- **WHEN** a client sends `GET /api/v1/registry-specifications`
- **THEN** the system returns a paginated list of registry specifications

#### Scenario: Create registry specification
- **WHEN** a client sends `POST /api/v1/registry-specifications` with a valid JSON payload
- **THEN** the system creates a new `Registry_Specification` and returns its identifier

### Requirement: Degree_of_Conformance enumeration
The system SHALL provide a `Degree_of_Conformance` enumeration with values `conforming` and `strictly_conforming`, as specified in ISO/IEC 11179-3:2023, 9.6.1.

#### Scenario: Conformance value storage
- **WHEN** a registry specification references a standard
- **THEN** the `degree_of_conformance` field accepts only `conforming` or `strictly_conforming`

### Requirement: Registry Specification Configuration Step in Initialization Wizard
初始化向导 MUST 包含注册规范配置步骤，位于数据库配置步骤之后、注册机构步骤之前（步骤顺序：版本 → 数据库 → 注册规范 → 注册机构 → 命名空间 → 管理员密码），开放编辑 `Registry_Specification`（ISO/IEC 11179-3:2023 表 28）的标量属性：name（必填）、comment、web_address、character_repertoire（必填）、standard、degree_of_conformance（conforming / strictly_conforming）、conformance_level、conformance_label、included_features、excluded_features、default_time_zone（[-+]HHMM）、default_language、alternative_language（0..*）、reference_document_identifier_kind（0..*）、representation_class_scheme_id。各属性 MUST 预填系统默认值；上下文与指称/定义多语言编辑不在向导内提供（初始化后于设置页维护）。

#### Scenario: 向导中配置注册规范并带入 setup
- **WHEN** 超级管理员在注册规范步骤修改字符集、标准、合规程度、时区、替代语言等属性并完成后续初始化
- **THEN** System SHALL 按所填值创建注册规范，初始化完成后设置页回显一致

#### Scenario: 向导中保留默认值提交
- **WHEN** 超级管理员不修改注册规范步骤的预填默认值直接提交
- **THEN** System SHALL 以默认值创建注册规范（字符集 UTF-8、标准 ISO/IEC 11179-3:2023、默认语言 zh-CN、时区 +0800）

### Requirement: Registry Specification Full Attribute Editing in Settings
注册规范设置页的编辑界面 MUST 开放 `Registry_Specification`（ISO/IEC 11179-3:2023 表 28）全部属性的编辑：name、comment、web_address、default_language、alternative_language（0..*）、character_repertoire、reference_document_identifier_kind（0..*）、representation_class_scheme_id、context、standard、degree_of_conformance（conforming / strictly_conforming）、conformance_level、conformance_label、included_features、excluded_features、default_time_zone（[-+]HHMM），以及继承自 Item 的指称（0..*，任意语言与上下文）与定义（0..*，任意语言与上下文）。其中 name 与 character_repertoire 为必填。

#### Scenario: 编辑注册规范并保存全部属性
- **WHEN** 注册管理员在设置页打开注册规范编辑对话框，填写替代语言、参考文档标识种类、表示类方案标识、上下文等属性并保存
- **THEN** 系统持久化全部属性，重新打开时回显一致

#### Scenario: 指称与定义支持任意语言
- **WHEN** 注册管理员在编辑对话框的指称/定义折叠面板中添加非中/英文语言的指称或定义行并保存
- **THEN** 系统按语言保存各行（含上下文与可接受度），回显时完整还原

#### Scenario: 非法时区格式被拒绝
- **WHEN** 提交的 default_time_zone 不匹配 [-+]HHMM 格式（如 "800"、"+08:00"）
- **THEN** 系统返回 400 业务校验错误；前端在失焦时即提示格式错误且不提交

### Requirement: 注册规范独立于注册机构且系统全局
注册规范（Registry_Specification）描述的是注册系统本身的运行环境（ISO/IEC 11179-3:2023 §9.4.10），SHALL 独立于任何注册机构创建与生效，其作用域为系统全局（每个注册系统一条）。系统 MUST NOT 在注册规范上提供任何注册机构归属或作用域字段；默认上下文、默认语言等全局消费语义 MUST 保持取系统既有注册规范。

#### Scenario: 初始化先建规范后建机构

- **WHEN** 管理员提交初始化 setup
- **THEN** 系统在创建任何注册机构之前创建注册规范；注册规范不携带注册机构引用

#### Scenario: 注册规范设置页无机构维度

- **WHEN** 注册管理员打开注册规范设置页
- **THEN** 表单与列表不出现任何注册机构归属/筛选字段
