## ADDED Requirements

### Requirement: Search filters by context
The `/metadata/search` endpoint SHALL accept an optional `contextId` parameter and return only items whose designations, definitions, or pairings reference that context.

#### Scenario: Search with a context filter
- **WHEN** a client searches with a `contextId` parameter
- **THEN** only items with at least one designation, definition, or pairing referencing that context are returned

### Requirement: Search filters by acceptability
The `/metadata/search` endpoint SHALL accept an optional `acceptability` parameter and return only items carrying that acceptability rating on a designation, definition, context association, or pairing.

#### Scenario: Search with an acceptability filter
- **WHEN** a client searches with `acceptability=PREFERRED`
- **THEN** only items with at least one preferred designation, definition, or pairing are returned

#### Scenario: Combined context and acceptability filters
- **WHEN** a client searches with both `contextId` and `acceptability` parameters
- **THEN** only items matching both filters are returned
