## ADDED Requirements

### Requirement: Detect multiple PREFERRED designations in the same scope
The editor and relationship table SHALL detect and highlight when more than one designation for the same item has `acceptability = PREFERRED` within the same effective scope (same language and same context, or same language and no context).

#### Scenario: Two PREFERRED designations in same language and no context
- **WHEN** an item has two Chinese designations both marked PREFERRED and neither has a context
- **THEN** both rows are highlighted in danger/warning color and a message “同一作用域只能有一个 PREFERRED（ISO/IEC 11179-3 8.7.1.3）” is shown

#### Scenario: PREFERRED designations in different contexts
- **WHEN** an item has two PREFERRED designations but each is bound to a different context
- **THEN** no conflict is reported because the contexts form different sets

### Requirement: Detect multiple PREFERRED definitions in the same scope
The same uniqueness rule SHALL apply to definitions.

#### Scenario: Two PREFERRED definitions in same context
- **WHEN** an item has two definitions both marked PREFERRED and both bound to the same context
- **THEN** both rows are highlighted and a conflict message is shown

### Requirement: Pairing acceptability is included in conflict detection
If a pairing declares `acceptability = PREFERRED`, the corresponding designation and definition SHALL be treated as PREFERRED in the context of that pairing for uniqueness checks.

#### Scenario: PREFERRED pairing conflicts with direct PREFERRED designation
- **WHEN** a designation has direct acceptability PREFERRED and a pairing that includes the same designation in the same context is also marked PREFERRED
- **THEN** the conflict is highlighted because both represent the preferred option in the same set
