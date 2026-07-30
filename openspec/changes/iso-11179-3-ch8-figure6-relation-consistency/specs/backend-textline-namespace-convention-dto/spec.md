## ADDED Requirements

### Requirement: TextLineResponse includes namespace and naming convention IDs
`TextLineResponse` SHALL include `namespaceIds` and `namingConventionIds` for designations and `namespaceIds` for definitions.

#### Scenario: Relationship endpoint returns designations
- **WHEN** the client calls `/api/v1/items/{itemId}/designation-definition-relationships`
- **THEN** each designation in the response contains `namespaceIds` and `namingConventionIds`

#### Scenario: Relationship endpoint returns definitions
- **WHEN** the same endpoint returns definitions
- **THEN** each definition contains `namespaceIds`

### Requirement: ItemAdministrationService maps the new fields
`ItemAdministrationService.mapDesignations` and `mapDefinitions` SHALL populate the new fields from the JPA entities.

#### Scenario: Designation has namespaces and naming conventions
- **GIVEN** a designation is associated with two namespaces and one naming convention
- **WHEN** the service maps it to `TextLineResponse`
- **THEN** the response contains two UUIDs in `namespaceIds` and one UUID in `namingConventionIds`
