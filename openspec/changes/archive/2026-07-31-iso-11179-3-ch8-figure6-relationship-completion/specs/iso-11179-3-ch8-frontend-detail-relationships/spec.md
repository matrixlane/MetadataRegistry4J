## ADDED Requirements

### Requirement: Metadata detail page displays relationship tabs
The `MetadataDetailView` SHALL provide four lazy-loaded tabs: 「指称」, 「定义」, 「上下文」, and 「配对」, in addition to the existing 「概览」 and 「版本历史」 tabs.

#### Scenario: User navigates to a metadata detail page
- **WHEN** the user opens the detail page of an administered item
- **THEN** the tab bar contains the four relationship tabs and clicking a tab loads its relationship data

### Requirement: Relationship tabs show structured relationship tables
Each relationship tab SHALL display a table containing the relevant entities and their associated attributes, including language, source, context, and acceptability.

#### Scenario: User views the designations tab
- **WHEN** the user clicks the 「指称」 tab
- **THEN** the system calls the aggregated relationship endpoint and displays a table with sign, language, source, contexts, and acceptability

### Requirement: Relationship endpoint exposes context and naming convention references
The backend `ItemRelationshipController` SHALL return, in addition to designations, definitions, and pairings, the list of related contexts and naming conventions referenced by the item.

#### Scenario: Relationship endpoint is queried
- **WHEN** the frontend requests `/api/v1/items/{itemId}/designation-definition-relationships`
- **THEN** the response includes `contexts` and `namingConventions` arrays alongside `designations`, `definitions`, and `pairings`
