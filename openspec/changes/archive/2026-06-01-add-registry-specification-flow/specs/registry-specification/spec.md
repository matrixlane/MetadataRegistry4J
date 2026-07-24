## ADDED Requirements

### Requirement: Super Admin Registry Specification Choice in System Initialization
在系统初始化阶段，超级管理员（Super Admin，即系统的第一个注册管理员）应当能够选择立即创建注册规范（`Registry_Specification`），或者选择稍后创建。

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
系统中的任何注册管理员（Registry Administrator）均有权创建和更新注册规范。非注册管理员角色用户没有操作权限。

#### Scenario: Non-registry administrator attempts to create or edit Registry Specification
- **WHEN** User without registry administrator role attempts to save a Registry Specification
- **THEN** System SHALL deny the request with an access denied (403 Forbidden) response

#### Scenario: Registry administrator creates Registry Specification successfully
- **WHEN** User with registry administrator role submits valid Registry Specification settings
- **THEN** System SHALL save the Registry Specification and log an audit trail event

---

### Requirement: Locking Critical Registry Specification Settings When Metadata Exists
一旦创建了注册规范，并且系统中已存在与该注册规范对应的任何元数据（包括命名空间、注册项、值域等已被保存或发布的受管项），则为了防止数据一致性冲突，以下注册规范的关键配置字段将**不允许被修改**：
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
只有在注册规范已创建并启用后，本注册表才正式允许数据治理工作的开展（即允许创建注册项目 `Project`，以及注册或导入任何元数据项 `Administered Item`）。

#### Scenario: Attempt to create project before Registry Specification is created
- **WHEN** User attempts to create a new registration project or registry item, and no Registry Specification exists in the system
- **THEN** System SHALL block the operation with a business exception (400 Bad Request) indicating that a Registry Specification must be configured first
- **AND** Front-end UI SHALL block/disable the "Create Project" and "Register Metadata" buttons, showing a guide page to set up the Registry Specification
