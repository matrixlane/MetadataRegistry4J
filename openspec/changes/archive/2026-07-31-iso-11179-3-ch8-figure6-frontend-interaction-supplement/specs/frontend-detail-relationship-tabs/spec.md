## ADDED Requirements

### Requirement: Metadata detail page shows relationship tabs
`MetadataDetailView` SHALL provide tabs for Designations, Definitions, Contexts, Pairings, and Version History in addition to Overview.

#### Scenario: User views an item's designations
- **WHEN** the user navigates to a metadata item detail page and clicks the "Designations" tab
- **THEN** the page loads the aggregated relationship data lazily and displays a table with language, value, source, contexts, and acceptability

### Requirement: Relationship tabs support navigation
Each relationship table SHALL provide links to related items or contexts where applicable.

#### Scenario: User clicks a context in a designation row
- **WHEN** the user clicks a context label in the designations table
- **THEN** the application navigates to the context detail or list view filtered to that context
