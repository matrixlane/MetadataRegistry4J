## ADDED Requirements

### Requirement: Pairing context is resolved in relationship endpoint
The `ItemRelationshipController` SHALL resolve the context label for each pairing returned in the relationship endpoint.

#### Scenario: Request relationship data with pairing context
- **WHEN** the client requests `/api/v1/items/{itemId}/designation-definition-relationships`
- **THEN** each pairing in the response includes a resolved `contextLabel` when `contextId` is present

### Requirement: Relationship endpoint integration tests
The system SHALL provide integration tests covering the relationship endpoint.

#### Scenario: Controller returns relationship data
- **WHEN** the integration test creates an item with designations, definitions, contexts, and a pairing
- **THEN** the endpoint returns all relationships with correct labels and counts
