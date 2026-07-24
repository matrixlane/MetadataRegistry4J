## ADDED Requirements

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
