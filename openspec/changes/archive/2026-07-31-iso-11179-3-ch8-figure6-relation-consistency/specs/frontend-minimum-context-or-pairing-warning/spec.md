## ADDED Requirements

### Requirement: Designation/definition row warns when isolated
`MultilingualTextEditor.vue` SHALL display an inline warning for any designation or definition row that has zero contexts and is not referenced by any existing pairing.

#### Scenario: Row has no context and no pairing
- **WHEN** a designation/definition row has no context entries and its id is not used in any `DesignationDefinitionPairing` row
- **THEN** the row header shows a warning icon and the message “未关联上下文或配对，违反 ISO/IEC 11179-3 8.4.1.4 / 8.4.2.4”

#### Scenario: Row is referenced by a pairing
- **WHEN** a designation/definition row has no context entries but is referenced by at least one pairing
- **THEN** no isolation warning is shown

### Requirement: Form submission blocked for isolated rows unless explicitly allowed
`ItemFormDialog` SHALL block submission when isolated rows exist unless the user has toggled a “允许例外” switch on that row.

#### Scenario: User submits form with isolated row
- **WHEN** the user clicks submit and at least one designation/definition row is isolated
- **THEN** a confirmation dialog appears listing the isolated rows and the form is not submitted

#### Scenario: User marks row as exception and submits
- **WHEN** the user toggles the “允许例外” switch on an isolated row and clicks submit
- **THEN** the form submits successfully and the row is saved with `isolatedException: true` for audit purposes
