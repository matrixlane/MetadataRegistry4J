## ADDED Requirements

### Requirement: Limit Registration Authority Creation in Single Registry Mode
当系统运行模式被设置为单注册表模式（SINGLE），且系统中已经存在一个或多个注册机构（Registration Authority）时，系统 SHALL 不允许再新增任何注册机构。

#### Scenario: Prevent Registration Authority creation in SINGLE mode when one already exists on Backend
- **WHEN** System running mode is "SINGLE" and at least one Registration Authority already exists in the database
- **AND** A user attempts to create a new Registration Authority via the backend API
- **THEN** The backend SHALL reject the request with a business validation exception (422 Validation Failed)

#### Scenario: Hide or disable Registration Authority creation button in SINGLE mode when one already exists in UI
- **WHEN** System running mode is "SINGLE" and at least one Registration Authority already exists in the database
- **THEN** The Registration Authority management page in the UI SHALL hide or disable the "Create" button, preventing the user from launching the creation flow
