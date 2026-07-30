## ADDED Requirements

### Requirement: Acceptability badges in relationship tables
Relationship tables in `MetadataDetailView` SHALL display an `Acceptability` badge for each designation, definition, and pairing row.

#### Scenario: User sees a preferred designation
- **WHEN** a designation has acceptability `PREFERRED`
- **THEN** the table row shows a green "首选" badge

### Requirement: Browser list highlights preferred designation
`BrowserView` SHALL display a `PREFERRED` badge next to the primary label of each row when applicable.

#### Scenario: User scans the data dictionary list
- **WHEN** a row has at least one preferred designation
- **THEN** a green "首选" badge appears next to the item name

### Requirement: Advanced filters for context and acceptability
`BrowserView` SHALL provide advanced filters for context and acceptability.

#### Scenario: User filters by context
- **WHEN** the user selects a context from the advanced filter panel
- **THEN** the list refreshes to show only items whose designations, definitions, or pairings reference that context
- **AND** if the backend does not yet support the filter, a non-blocking tip is displayed

#### Scenario: User filters by acceptability
- **WHEN** the user selects one or more acceptability ratings in the advanced filter panel
- **THEN** the list refreshes to show only items carrying a matching acceptability rating on a designation, definition, or pairing
