## ADDED Requirements

### Requirement: Context usages endpoint
The system SHALL provide a `/api/v1/contexts/{id}/usages` endpoint that returns all `Designation_Context`, `Definition_Context`, and `Designation_Definition_Pairing` records referencing the given context. Each usage row SHALL include a usage type discriminator, the owning item identifier and label, the designation or definition value, and the acceptability rating where applicable.

#### Scenario: Request usages for a referenced context
- **WHEN** a client requests `/api/v1/contexts/{id}/usages` for a context referenced by designations, definitions, and pairings
- **THEN** the response contains one usage row per reference with a type discriminator (`DESIGNATION`, `DEFINITION`, or `PAIRING`), the owning item label, the text value, and the acceptability

#### Scenario: Request usages for an unreferenced context
- **WHEN** a client requests usages for a context that nothing references
- **THEN** the endpoint returns 200 OK with an empty list

### Requirement: Usages result is bounded
The usages endpoint SHALL bound the returned result set and indicate truncation, so that heavily reused contexts do not produce unbounded responses.

#### Scenario: Context with more usages than the limit
- **WHEN** a context is referenced by more records than the configured limit (default 100)
- **THEN** the response contains at most the limited number of usages and indicates that the result was truncated
