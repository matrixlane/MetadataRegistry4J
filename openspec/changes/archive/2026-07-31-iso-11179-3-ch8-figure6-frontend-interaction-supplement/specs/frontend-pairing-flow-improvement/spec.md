## ADDED Requirements

### Requirement: Pairing editor options show readable labels
`DesignationDefinitionPairingEditor` dropdown options SHALL display the language and value of each designation and definition.

#### Scenario: User creates a pairing
- **WHEN** the user opens the pairing editor and views the designation dropdown
- **THEN** each option shows the language code and the designation value

### Requirement: Duplicate pairing detection
The pairing editor SHALL detect duplicate combinations of designationId + definitionId + contextId before emit or save.

#### Scenario: User adds a duplicate pairing
- **WHEN** the user adds a second pairing with the same designation, definition, and context as an existing one
- **THEN** the editor highlights the conflicting rows and disables save until resolved

### Requirement: Temporary ID pairing for new items
When temporary client IDs are enabled, the pairing editor SHALL support pairing designations and definitions that have not yet been saved by using temporary client IDs.

#### Scenario: User creates a new item with a pairing
- **GIVEN** the item form is in create mode
- **WHEN** the user adds a designation, a definition, and a pairing linking them via temporary IDs
- **THEN** the form payload includes the temporary IDs and the backend maps them to real IDs on save
