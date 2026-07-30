## ADDED Requirements

### Requirement: Pairing editor prevents duplicate pairings
The `DesignationDefinitionPairingEditor` SHALL validate that the same `(designationId, definitionId, contextId)` combination does not already exist in the current item and display a duplicate warning before save.

#### Scenario: User attempts to create a duplicate pairing
- **WHEN** the user selects a designation, definition, and context that are already paired
- **THEN** the editor disables the save action and shows a duplicate pairing error message

### Requirement: Pairing editor shows readable labels for options
The designation and definition dropdowns in the pairing editor SHALL display the value, language code, and context tags for each option so that users can distinguish rows with identical text.

#### Scenario: User selects a designation in the pairing editor
- **WHEN** the user opens the designation dropdown
- **THEN** each option shows the sign text, language code, and abbreviated context list

### Requirement: Pairing can be created within the same item creation dialog (optional)
When the optional temporary-ID feature is enabled, the `ItemFormDialog` SHALL allow users to create pairings for designations and definitions added in the same dialog before the item is first saved, and the backend SHALL map temporary IDs to persisted IDs on save.

#### Scenario: User creates an item and a pairing in one dialog
- **WHEN** the user adds a designation and a definition in the new-item dialog, then creates a pairing between them, and finally saves the item
- **THEN** the backend persists the item, the designation, the definition, and the pairing with correct persisted IDs
