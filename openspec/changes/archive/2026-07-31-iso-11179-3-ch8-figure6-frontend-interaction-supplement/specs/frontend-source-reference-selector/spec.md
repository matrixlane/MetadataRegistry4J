## ADDED Requirements

### Requirement: ReferenceDocument selector per designation/definition line
The `MultilingualTextEditor` component SHALL provide a `ReferenceDocument` selector for each designation or definition line.

#### Scenario: User adds a designation with a source
- **WHEN** the user opens an item form and expands the context section of a designation line
- **THEN** a `ReferenceDocument` dropdown is visible and selectable
- **AND** the selected `sourceId` is included in the emitted `TextLineForm`

### Requirement: Source field is optional
The source selector SHALL be optional and not block form submission when empty.

#### Scenario: User saves a designation without source
- **WHEN** the user submits a designation with no selected source
- **THEN** the backend receives `sourceId: null` and the designation is saved successfully
