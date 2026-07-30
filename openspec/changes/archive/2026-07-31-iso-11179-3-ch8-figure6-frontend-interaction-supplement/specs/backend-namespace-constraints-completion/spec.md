## ADDED Requirements

### Requirement: Enforce one_name_per_item_indicator
The system SHALL reject namespace updates that violate `one_name_per_item_indicator = TRUE` if more than one designation in the namespace refers to the same item.

#### Scenario: Save namespace with duplicate item names
- **WHEN** a namespace has `oneNamePerItemIndicator = TRUE` and two designations with the same `item_id` exist in the namespace
- **THEN** the save operation returns 400 Bad Request with a clear message

### Requirement: Enforce one_item_per_name_indicator
The system SHALL reject namespace updates that violate `one_item_per_name_indicator = TRUE` if the same designation sign is used for more than one item in the namespace.

#### Scenario: Save namespace with ambiguous name
- **WHEN** a namespace has `oneItemPerNameIndicator = TRUE` and two items share the same designation sign in the namespace
- **THEN** the save operation returns 400 Bad Request with a clear message

### Requirement: Enforce designation conformance under mandatory naming convention
When a namespace has `mandatoryNamingConventionIndicator = TRUE`, the system SHALL require that all designations associated with that namespace are also associated with the same naming convention.

#### Scenario: Save designation that does not conform to mandatory convention
- **WHEN** a namespace has `mandatoryNamingConventionIndicator = TRUE` and a designation associated with the namespace is associated with a different naming convention
- **THEN** the operation returns 400 Bad Request with a clear message

#### Scenario: Apply non-conforming designation to an item in a mandatory namespace
- **WHEN** designations are applied to an item bound to a namespace with `mandatoryNamingConventionIndicator = TRUE` and a designation is not associated with the namespace's naming convention
- **THEN** the apply operation returns 400 Bad Request with a clear message
