## ADDED Requirements

### Requirement: NamingConvention is a first-class registry item
The system SHALL provide a `NamingConvention` domain entity that inherits from `Item` and is therefore identifiable, designable, definable, and administrable within the registry.

#### Scenario: Create a registry-stored naming convention
- **WHEN** a registrar creates a `NamingConvention` with a designation, a definition, and a set of rules
- **THEN** the system persists it as an `Item` subclass with its own identifier, registration state, and administered metadata

#### Scenario: Retrieve naming convention via registration API
- **WHEN** a client queries the registry for a `NamingConvention` by its scoped identifier
- **THEN** the system returns the entity along with its designations and definitions

### Requirement: Namespace references a NamingConvention entity
The system SHALL allow a `Namespace` to reference zero or one `NamingConvention` through a JPA association, replacing the existing bare `UUID namingConventionId` field semantics.

#### Scenario: Assign a naming convention to a namespace
- **WHEN** a registrar sets `Namespace.namingConvention` to a persisted `NamingConvention`
- **THEN** the database stores the foreign key in `namespace.naming_convention_id` and enforces referential integrity

#### Scenario: Namespace with mandatory naming convention indicator
- **WHEN** `Namespace.mandatoryNamingConventionIndicator` is `true` and no `NamingConvention` is assigned
- **THEN** the system rejects the save operation with a validation error

### Requirement: Designation declares conformance to naming conventions
The system SHALL allow a `Designation` to be associated with zero or more `NamingConvention` instances via a `designation_naming_convention` association table.

#### Scenario: Link designation to naming conventions
- **WHEN** a registrar associates a `Designation` with two `NamingConvention` entities
- **THEN** the system persists both associations and returns them on retrieval

#### Scenario: Query designations by naming convention
- **WHEN** a client queries for all designations that conform to a given `NamingConvention`
- **THEN** the system returns only designations linked to that convention

### Requirement: Runtime validation can use registry-stored naming conventions
The system SHALL allow `NamingConventionEngine` to load rules from a persisted `NamingConvention` entity and apply them to a designation sign value.

#### Scenario: Validate a designation against its registered naming convention
- **WHEN** a `Designation` has an associated `NamingConvention` and its sign value is evaluated
- **THEN** the engine loads the stored rules, executes them, and reports success or failure consistent with the existing rule result format

#### Scenario: Fallback when no registered naming convention is found
- **WHEN** the engine is asked to evaluate a value for a `NamingConvention` identifier that no longer exists
- **THEN** it returns a failure result indicating the missing convention

### Requirement: Database migration preserves existing namespace data
The system SHALL provide Flyway migrations that convert the `namespace.naming_convention_id` column from a bare UUID to a foreign key referencing `naming_convention.id`, preserving valid references and clearing invalid ones with a logged warning.

#### Scenario: Migration on existing database
- **GIVEN** a database with existing `namespace` rows containing `naming_convention_id` values
- **WHEN** the migration runs
- **THEN** valid UUIDs that exist in `naming_convention` remain linked, invalid UUIDs are set to NULL, and the migration is recorded as successful
