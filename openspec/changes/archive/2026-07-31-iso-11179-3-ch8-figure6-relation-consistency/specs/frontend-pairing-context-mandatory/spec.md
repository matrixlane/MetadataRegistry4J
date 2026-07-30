## ADDED Requirements

### Requirement: Pairing editor requires context selection
`DesignationDefinitionPairingEditor.vue` SHALL require a non-empty `contextId` for every pairing row before the form can be saved.

#### Scenario: User adds a pairing without context
- **WHEN** the user adds a pairing row and leaves the context selector empty
- **THEN** the row is highlighted in warning state and the save/confirm button is disabled
- **AND** a tooltip or inline message explains “配对必须属于一个上下文（ISO/IEC 11179-3 8.6.1）”

#### Scenario: User selects a context for the pairing
- **WHEN** the user selects a context from the pairing row’s context selector
- **THEN** the warning state disappears and the save/confirm button becomes enabled

### Requirement: Backend rejects pairing without context
The `DesignationDefinitionPairingService` SHALL reject any create or update request with a null `contextId`.

#### Scenario: API receives a pairing with null context
- **WHEN** the backend receives a `DesignationDefinitionPairingForm` with `contextId` null or blank
- **THEN** the service throws `BusinessException` with code `VALIDATION_FAILED` and message “配对必须关联一个上下文”
- **AND** the API returns HTTP 400
