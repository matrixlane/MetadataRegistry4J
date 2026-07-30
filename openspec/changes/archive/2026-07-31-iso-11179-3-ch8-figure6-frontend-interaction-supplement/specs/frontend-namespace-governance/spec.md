## ADDED Requirements

### Requirement: Namespace form supports multiple naming conventions
`NamespaceList` form SHALL provide a multi-select naming convention selector.

#### Scenario: User associates two naming conventions with a namespace
- **WHEN** the user selects two naming conventions in the namespace form
- **THEN** the save payload includes both `namingConventionIds`
- **AND** the namespace list reflects the selected conventions

### Requirement: Namespace form exposes three indicator switches
The namespace form SHALL provide switches for `mandatoryNamingConventionIndicator`, `oneNamePerItemIndicator`, and `oneItemPerNameIndicator`.

#### Scenario: User enables mandatory naming convention
- **WHEN** the user toggles "强制命名约定" on and selects exactly one naming convention
- **THEN** the namespace saves successfully
- **AND** if the user selects zero or more than one convention while the indicator is on, the backend error is displayed
