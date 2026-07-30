## ADDED Requirements

### Requirement: Metadata detail view shows designation relationships
The system SHALL display a dedicated tab in the metadata detail view that lists all designations of the item, including language, value, source, contexts, and acceptability.

#### Scenario: View designations with contexts
- **WHEN** a user opens a metadata item detail page and clicks the "Designations" tab
- **THEN** the system shows each designation with its language, value, source reference, and a list of associated contexts with their acceptability levels

### Requirement: Metadata detail view shows definition relationships
The system SHALL display a dedicated tab in the metadata detail view that lists all definitions of the item, including language, value, source, contexts, and acceptability.

#### Scenario: View definitions with contexts
- **WHEN** a user opens a metadata item detail page and clicks the "Definitions" tab
- **THEN** the system shows each definition with its language, value, source reference, and a list of associated contexts with their acceptability levels

### Requirement: Metadata detail view shows designation-definition pairings
The system SHALL display a dedicated tab in the metadata detail view that lists all `DesignationDefinitionPairing` records for the item, showing the linked designation, definition, context, and acceptability.

#### Scenario: View pairings
- **WHEN** a user opens a metadata item detail page and clicks the "Pairings" tab
- **THEN** the system shows each pairing with the designation value, definition value, context label, and acceptability badge

### Requirement: Relationship data is loaded on demand
The system SHALL load relationship data only when the user switches to a relationship tab, to avoid unnecessary requests for the overview tab.

#### Scenario: Lazy load relationship tabs
- **WHEN** a user opens the detail page and initially sees the overview tab
- **THEN** no relationship data is fetched until the user clicks one of the Designations, Definitions, Contexts, or Pairings tabs
