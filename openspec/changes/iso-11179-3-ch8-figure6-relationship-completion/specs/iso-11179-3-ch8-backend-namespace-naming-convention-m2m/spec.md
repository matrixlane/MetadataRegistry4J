## ADDED Requirements

### Requirement: Namespace supports multiple naming conventions
The `Namespace` entity SHALL support a many-to-many association with `NamingConvention` via a `namespace_naming_convention` join table, and the API SHALL accept and return a list of `namingConventionIds`.

#### Scenario: API creates a namespace with multiple naming conventions
- **WHEN** a client sends a `POST /api/v1/namespaces` request with `namingConventionIds` containing two IDs
- **THEN** the namespace is created and the join table contains two rows linking the namespace to those conventions

### Requirement: Mandatory naming convention indicator is enforced
When `mandatoryNamingConventionIndicator` is true, the system SHALL reject any namespace that has zero or more than one associated naming convention with a `400 Bad Request` error.

#### Scenario: Mandatory namespace without naming convention is rejected
- **WHEN** a client creates a namespace with `mandatoryNamingConventionIndicator=true` and an empty `namingConventionIds` list
- **THEN** the API returns `400 Bad Request` with a constraint violation message

### Requirement: Designations in a mandatory namespace conform to the same naming convention
When `mandatoryNamingConventionIndicator` is true, every designation associated with that namespace through `designation_namespace` SHALL also be associated with at least one of the namespace's naming conventions through `naming_convention_conformance`.

#### Scenario: Designation in mandatory namespace fails conformance check
- **WHEN** a client saves a designation linked to a mandatory namespace and the designation is not linked to any of the namespace's naming conventions
- **THEN** the API returns `400 Bad Request` with a message indicating the naming convention conformance violation
