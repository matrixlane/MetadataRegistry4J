## ADDED Requirements

### Requirement: Data dictionary supports filtering by context
The system SHALL provide an advanced filter in the data dictionary browser that allows users to filter items by a selected context.

#### Scenario: Filter items by context
- **WHEN** a user selects a context from the advanced filter
- **THEN** the result list shows only items whose designations, definitions, or pairings reference that context

### Requirement: Data dictionary supports filtering by acceptability
The system SHALL provide an advanced filter in the data dictionary browser that allows users to filter items by a selected acceptability level.

#### Scenario: Filter items by preferred acceptability
- **WHEN** a user selects "PREFERRED" from the acceptability filter
- **THEN** the result list shows only items that have at least one designation or definition marked as PREFERRED in any context

### Requirement: Search results show acceptability hints
The system SHALL display an acceptability badge next to the primary label in search results when the matching designation or definition has a specific acceptability.

#### Scenario: Preferred badge in search results
- **WHEN** a search result item has a PREFERRED designation
- **THEN** the result card shows a "首选" badge next to the item name
