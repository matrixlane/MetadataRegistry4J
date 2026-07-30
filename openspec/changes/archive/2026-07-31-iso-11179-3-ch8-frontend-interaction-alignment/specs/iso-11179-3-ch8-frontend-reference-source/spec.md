## ADDED Requirements

### Requirement: Designation and definition editor supports reference source
The system SHALL allow users to select an optional `ReferenceDocument` for each designation and each definition line in the multilingual editor.

#### Scenario: Add a designation with source
- **WHEN** a registrar creates a new designation and selects a reference document from the dropdown
- **THEN** the form payload includes the `sourceId` and the backend persists the `Designation` to `ReferenceDocument` association

#### Scenario: Edit a definition source
- **GIVEN** an existing definition has a source
- **WHEN** the registrar changes or clears the source
- **THEN** the updated value is saved and reflected in the detail view
