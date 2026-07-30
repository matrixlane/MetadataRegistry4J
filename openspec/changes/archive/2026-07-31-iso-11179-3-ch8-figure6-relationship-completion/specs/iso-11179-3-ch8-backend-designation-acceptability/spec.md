## ADDED Requirements

### Requirement: Designation has an optional direct acceptability attribute
The `Designation` entity SHALL have an optional `acceptability` attribute of type `Acceptability`. When absent, the system SHALL derive the effective acceptability from the associated `DesignationContext` entries.

#### Scenario: API creates a designation with direct acceptability
- **WHEN** a client saves a designation with `acceptability=PREFERRED`
- **THEN** the entity is persisted with `PREFERRED` and the detail endpoint reflects it

### Requirement: Definition has an optional direct acceptability attribute
The `Definition` entity SHALL have an optional `acceptability` attribute of type `Acceptability`. When absent, the system SHALL derive the effective acceptability from the associated `DefinitionContext` entries.

#### Scenario: API creates a definition with direct acceptability
- **WHEN** a client saves a definition with `acceptability=ADMITTED`
- **THEN** the entity is persisted with `ADMITTED` and the detail endpoint reflects it
