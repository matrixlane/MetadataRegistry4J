## ADDED Requirements

### Requirement: Pairing editor validates duplicate combinations
The system SHALL prevent users from creating duplicate `DesignationDefinitionPairing` records (same designation, same definition, same context) and show a clear validation message.

#### Scenario: Attempt duplicate pairing
- **WHEN** a registrar tries to add a pairing that already exists
- **THEN** the editor highlights the duplicate row and prevents saving until the conflict is resolved

### Requirement: Pairing editor shows human-readable labels
The system SHALL display the actual designation sign and definition text in the pairing dropdowns, not just technical IDs.

#### Scenario: Select designation in pairing editor
- **WHEN** a registrar opens the designation dropdown in the pairing editor
- **THEN** each option shows the language badge and the designation value

### Requirement: Inline pairing for new items
The system SHALL allow users to create designations and definitions in the same dialog and then pair them without first saving the parent item, using temporary client-side identifiers that are resolved to real IDs on save.

#### Scenario: Create a new item with a pairing in one dialog
- **WHEN** a registrar creates a new item, adds a designation and a definition, and then adds a pairing in the same dialog
- **THEN** the system saves the item, its designation, its definition, and the pairing in a single request, or guides the user through the minimum required saves
