## ADDED Requirements

### Requirement: Browser supports filtering by context
The `BrowserView` advanced filters SHALL include a context selector, and applying it SHALL send a `contextId` parameter to the backend search endpoint; the results SHALL only include items that have at least one designation, definition, or pairing in that context.

#### Scenario: User filters by context
- **WHEN** the user selects a context from the advanced filter and clicks search
- **THEN** the search request includes `contextId` and the result list only contains items related to that context

### Requirement: Browser supports filtering by acceptability
The `BrowserView` advanced filters SHALL include an acceptability multi-select, and applying it SHALL send an `acceptability` parameter to the backend search endpoint; the results SHALL only include items with designations, definitions, or pairings matching one of the selected ratings.

#### Scenario: User filters by acceptability
- **WHEN** the user selects `PREFERRED` and `ADMITTED` acceptability ratings and clicks search
- **THEN** the search request includes `acceptability=PREFERRED,ADMITTED` and the result list only contains items with matching ratings
