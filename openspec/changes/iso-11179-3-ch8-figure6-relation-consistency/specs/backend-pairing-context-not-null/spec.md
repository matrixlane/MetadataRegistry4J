## ADDED Requirements

### Requirement: Database enforces non-null pairing context
The `designation_definition_pairing.context_id` column SHALL be `NOT NULL` after the migration is applied.

#### Scenario: Migration is applied to an empty column table
- **WHEN** Flyway applies the new migration on a database where all existing pairings already have a context
- **THEN** the column becomes `NOT NULL` and the application starts successfully

#### Scenario: Migration handles existing null rows
- **GIVEN** some existing pairings have a null context
- **WHEN** the migration runs
- **THEN** it assigns a designated “default registry context” id to those rows before applying `NOT NULL`
- **AND** the migration is wrapped in a transaction so it can be rolled back on failure

### Requirement: Service rejects null pairing context
`DesignationDefinitionPairingService` SHALL throw `BusinessException` when attempting to create or update a pairing with a null `context`.

#### Scenario: Service creates a pairing without context
- **WHEN** the service receives a pairing form with `contextId` null
- **THEN** it throws `BusinessException` with code `VALIDATION_FAILED` and returns HTTP 400
