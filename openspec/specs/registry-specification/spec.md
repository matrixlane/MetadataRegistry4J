# Registry Specification Capability Specification

## Purpose
This capability defines the core rules and settings that govern how the metadata registry operates (e.g., identifier generation, versioning policy, mandatory classification, and default language). It enforces data integrity, controls system initialization steps, and acts as an admission gate for data governance and metadata registration.
## Requirements
### Requirement: Super Admin Registry Specification Choice in System Initialization
在系统初始化阶段，超级管理员（Super Admin，即系统的第一个注册管理员）MUST 能够选择立即创建注册规范（`Registry_Specification`），或者选择稍后创建。

#### Scenario: Choose to create Registry Specification immediately during initialization
- **WHEN** Super Admin initializes the system and chooses to create Registry Specification immediately
- **THEN** System SHALL transition to the Registry Specification creation step, allowing the admin to input registry configuration, and save it, marking the initialization complete
- **AND** System SHALL enable full metadata registration features

#### Scenario: Choose to defer Registry Specification creation during initialization
- **WHEN** Super Admin initializes the system and chooses to create Registry Specification later
- **THEN** System SHALL complete the basic initialization (e.g., seeding default users, roles, basic settings) without creating the Registry Specification
- **AND** System SHALL display a global warning/banner indicating that data governance features are locked until a Registry Specification is created

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

### Requirement: Locking Critical Registry Specification Settings When Metadata Exists
一旦创建了注册规范，并且系统中已存在与该注册规范对应的任何元数据（包括命名空间、注册项、值域等已被保存或发布的受管项），则为了防止数据一致性冲突，以下注册规范的关键配置字段 MUST NOT **被修改**：
1. `identifierPrefix` (标识符前缀)
2. `autoGenerateIdentifier` (是否自动生成标识符)
3. `identifierPattern` (标识符正则表达式)
4. `versioningPolicy` (版本策略)
5. `mandatoryClassification` (是否强制分类)
6. `defaultLanguage` (默认语言)

#### Scenario: Modify non-critical settings when metadata exists
- **WHEN** Admin edits a Registry Specification that has associated metadata, and only changes non-critical fields (e.g., `registryName`, description)
- **THEN** System SHALL allow the update and save changes successfully

#### Scenario: Attempt to modify critical settings when metadata exists
- **WHEN** Admin attempts to edit a Registry Specification that has associated metadata, and changes any critical field (e.g., `identifierPrefix`, `autoGenerateIdentifier`, `versioningPolicy`)
- **THEN** System SHALL reject the request with a business validation error (400 Bad Request) stating that critical fields cannot be modified because metadata already exists
- **AND** Front-end UI SHALL disable (make read-only) these critical fields if associated metadata exists

---

### Requirement: Admission Gate for Projects and Metadata Registry
只有在注册规范已创建并启用后，本注册表 MUST 才正式允许数据治理工作的开展（即允许创建注册项目 `Project`，以及注册或导入任何元数据项 `Administered Item`）。

#### Scenario: Attempt to create project before Registry Specification is created
- **WHEN** User attempts to create a new registration project or registry item, and no Registry Specification exists in the system
- **THEN** System SHALL block the operation with a business exception (400 Bad Request) indicating that a Registry Specification must be configured first
- **AND** Front-end UI SHALL block/disable the "Create Project" and "Register Metadata" buttons, showing a guide page to set up the Registry Specification

---

### Requirement: Prevent Registration Authority deletion in Single Registry Mode
当系统运行模式被设置为单注册表模式（SINGLE）时，系统 SHALL 不允许删除已创建的注册机构（Registration Authority）。

#### Scenario: Block delete operation on Backend in SINGLE mode
- **WHEN** System running mode is "SINGLE"
- **AND** A user attempts to delete a Registration Authority via the backend delete API
- **THEN** The backend SHALL reject the request with a business exception (422 Validation Failed)

#### Scenario: Hide or disable delete button in UI in SINGLE mode
- **WHEN** System running mode is "SINGLE"
- **THEN** The Registration Authority management page in the UI SHALL hide or disable the "Delete" button, preventing the user from triggering the deletion flow

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

### Requirement: Limit Registration Authority Creation in Single Registry Mode
当系统运行模式被设置为单注册表模式（SINGLE），且系统中已经存在一个或多个注册机构（Registration Authority）时，系统 SHALL 不允许再新增任何注册机构。

#### Scenario: Prevent Registration Authority creation in SINGLE mode when one already exists on Backend
- **WHEN** System running mode is "SINGLE" and at least one Registration Authority already exists in the database
- **AND** A user attempts to create a new Registration Authority via the backend API
- **THEN** The backend SHALL reject the request with a business validation exception (422 Validation Failed)

#### Scenario: Hide or disable Registration Authority creation button in SINGLE mode when one already exists in UI
- **WHEN** System running mode is "SINGLE" and at least one Registration Authority already exists in the database
- **THEN** The Registration Authority management page in the UI SHALL hide or disable the "Create" button, preventing the user from launching the creation flow

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

