## ADDED Requirements

### Requirement: Designation editor supports ReferenceDocument source selection
The `MultilingualTextEditor` component SHALL provide a `ReferenceDocument` selector for each designation row, and the selected `sourceId` SHALL be included in the saved payload.

#### Scenario: User selects a source for a designation
- **WHEN** the user opens the designation editor and selects a reference document from the source dropdown of a designation row
- **THEN** the row stores the selected `sourceId` and the backend receives it on save

### Requirement: Definition editor supports ReferenceDocument source selection
The `MultilingualTextEditor` component SHALL provide a `ReferenceDocument` selector for each definition row, and the selected `sourceId` SHALL be included in the saved payload.

#### Scenario: User selects a source for a definition
- **WHEN** the user opens the definition editor and selects a reference document from the source dropdown of a definition row
- **THEN** the row stores the selected `sourceId` and the backend receives it on save
