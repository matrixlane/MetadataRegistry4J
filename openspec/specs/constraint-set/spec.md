# constraint-set Specification

## Purpose
TBD - created by archiving change iso-11179-3-chapter-9-registration-package. Update Purpose after archive.
## Requirements
### Requirement: Constraint_Set class exists
The system SHALL provide a `Constraint_Set` entity class that is a subclass of `Item`, as specified in ISO/IEC 11179-3:2023, 9.4.5.

#### Scenario: Model conformance
- **WHEN** inspecting the common-facilities domain model
- **THEN** `Constraint_Set` extends `Item` and is mapped as a JPA entity

### Requirement: Constraint_Set attributes
The system SHALL store the three boolean attributes required by ISO/IEC 11179-3:2023, 9.4.5.4: `item_shall_be_designated_indicator`, `item_shall_be_defined_indicator`, and `item_shall_be_classified_indicator`.

#### Scenario: Default constraint indicators
- **WHEN** a new `Constraint_Set` is created without explicit values
- **THEN** all three indicators default to `true`

### Requirement: Constraint_Set linked to RegisteredItem
The system SHALL allow zero or one `Constraint_Set` to be linked to zero or more `RegisteredItem` instances via the `registered_item_constraint_set` association (ISO/IEC 11179-3:2023, 9.5.3).

#### Scenario: Assign a constraint set to a registered item
- **WHEN** an administrator assigns a constraint set to a registered item
- **THEN** the registered item persists the reference to the selected constraint set

### Requirement: Constraint_Set REST API
The system SHALL expose CRUD endpoints under `/api/v1/constraint-sets` for managing constraint sets, including list, create, read, update, and delete operations.

#### Scenario: List constraint sets
- **WHEN** a client sends `GET /api/v1/constraint-sets`
- **THEN** the system returns a paginated list of constraint sets

#### Scenario: Create constraint set
- **WHEN** a client sends `POST /api/v1/constraint-sets` with a valid JSON payload
- **THEN** the system creates a new `Constraint_Set` and returns its identifier

