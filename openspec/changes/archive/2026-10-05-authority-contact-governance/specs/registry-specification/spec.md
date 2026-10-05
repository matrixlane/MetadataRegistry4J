# Registry Specification Delta Specification

## MODIFIED Requirements

### Requirement: Registry Specification Configuration Step in Initialization Wizard
初始化向导 MUST 包含注册规范配置步骤，位于数据库配置步骤之后、注册机构步骤之前（步骤顺序：版本 → 数据库 → 注册规范 → 注册机构 → 命名空间 → 管理员密码），开放编辑 `Registry_Specification`（ISO/IEC 11179-3:2023 表 28）的标量属性：name（必填）、comment、web_address、character_repertoire（必填）、standard、degree_of_conformance（conforming / strictly_conforming）、conformance_level、conformance_label、included_features、excluded_features、default_time_zone（[-+]HHMM）、default_language、alternative_language（0..*）、reference_document_identifier_kind（0..*）、representation_class_scheme_id。各属性 MUST 预填系统默认值；上下文与指称/定义多语言编辑不在向导内提供（初始化后于设置页维护）。

#### Scenario: 向导中配置注册规范并带入 setup
- **WHEN** 超级管理员在注册规范步骤修改字符集、标准、合规程度、时区、替代语言等属性并完成后续初始化
- **THEN** System SHALL 按所填值创建注册规范，初始化完成后设置页回显一致

#### Scenario: 向导中保留默认值提交
- **WHEN** 超级管理员不修改注册规范步骤的预填默认值直接提交
- **THEN** System SHALL 以默认值创建注册规范（字符集 UTF-8、标准 ISO/IEC 11179-3:2023、默认语言 zh-CN、时区 +0800）

## ADDED Requirements

### Requirement: 注册规范独立于注册机构且系统全局
注册规范（Registry_Specification）描述的是注册系统本身的运行环境（ISO/IEC 11179-3:2023 §9.4.10），SHALL 独立于任何注册机构创建与生效，其作用域为系统全局（每个注册系统一条）。系统 MUST NOT 在注册规范上提供任何注册机构归属或作用域字段；默认上下文、默认语言等全局消费语义 MUST 保持取系统既有注册规范。

#### Scenario: 初始化先建规范后建机构

- **WHEN** 管理员提交初始化 setup
- **THEN** 系统在创建任何注册机构之前创建注册规范；注册规范不携带注册机构引用

#### Scenario: 注册规范设置页无机构维度

- **WHEN** 注册管理员打开注册规范设置页
- **THEN** 表单与列表不出现任何注册机构归属/筛选字段

## REMOVED Requirements

### Requirement: Prevent Registration Authority deletion in Single Registry Mode
**Reason**: 注册机构删除保护改为按部署版本判定（个人版保护 Primary 机构，团队版允许删除全部），统一迁移至 `registration-authority-lifecycle` 能力接管。
**Migration**: 由 `registration-authority-lifecycle` 的"按部署版本的注册机构删除保护"需求替代；SINGLE 运行模式不再作为删除保护依据。

#### Scenario: Block delete operation on Backend in SINGLE mode
- **WHEN** System running mode is "SINGLE"
- **AND** A user attempts to delete a Registration Authority via the backend delete API
- **THEN** The backend SHALL reject the request with a business exception (422 Validation Failed)

#### Scenario: Hide or disable delete button in UI in SINGLE mode
- **WHEN** System running mode is "SINGLE"
- **THEN** The Registration Authority management page in the UI SHALL hide or disable the "Delete" button, preventing the user from triggering the deletion flow

### Requirement: Limit Registration Authority Creation in Single Registry Mode
**Reason**: 注册机构创建限制改为按部署版本判定（个人版放开数量限制，团队版 SINGLE 模式保留限建一个），统一迁移至 `registration-authority-lifecycle` 能力接管。
**Migration**: 由 `registration-authority-lifecycle` 的"按部署版本的注册机构创建规则"需求替代。

#### Scenario: Prevent Registration Authority creation in SINGLE mode when one already exists on Backend
- **WHEN** System running mode is "SINGLE" and at least one Registration Authority already exists in the database
- **AND** A user attempts to create a new Registration Authority via the backend API
- **THEN** The backend SHALL reject the request with a business validation exception (422 Validation Failed)

#### Scenario: Hide or disable Registration Authority creation button in SINGLE mode when one already exists in UI
- **WHEN** System running mode is "SINGLE" and at least one Registration Authority already exists in the database
- **THEN** The Registration Authority management page in the UI SHALL hide or disable the "Create" button, preventing the user from launching the creation flow
