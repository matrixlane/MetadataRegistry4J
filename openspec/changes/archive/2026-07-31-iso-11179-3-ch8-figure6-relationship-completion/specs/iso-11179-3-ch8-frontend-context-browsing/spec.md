## ADDED Requirements

### Requirement: Context list provides a view-usages action
The `ContextList` view SHALL include an action per row that opens a dialog showing all `DesignationContext`, `DefinitionContext`, and `DesignationDefinitionPairing` usages of the selected context.

#### Scenario: User inspects context usages
- **WHEN** the user clicks the 「查看引用」 action on a context row
- **THEN** a dialog opens and displays paginated usage records grouped by entity type

### Requirement: Context usage dialog shows readable labels
The context usage dialog SHALL display human-readable labels for the related item, designation, definition, and acceptability instead of raw identifiers only.

#### Scenario: User reads a usage record
- **WHEN** the usage dialog lists a `DesignationContext` record
- **THEN** the record shows the item name, designation sign, context name, and acceptability badge
