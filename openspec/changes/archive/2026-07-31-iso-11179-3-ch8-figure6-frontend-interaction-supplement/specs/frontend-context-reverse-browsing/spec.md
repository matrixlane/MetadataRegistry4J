## ADDED Requirements

### Requirement: Context list provides usage browsing
`ContextList` SHALL provide a "View Usages" action for each context row.

#### Scenario: User views context usages
- **WHEN** the user clicks "View Usages" on a context row
- **THEN** a dialog opens showing all `Designation_Context`, `Definition_Context`, and `Designation_Definition_Pairing` records that reference the context

### Requirement: Usage dialog shows readable labels
The usage dialog SHALL display readable item labels, designation/definition values, and acceptability ratings.

#### Scenario: User reviews a usage row
- **WHEN** the usage dialog lists a designation-context usage
- **THEN** the row shows the item name, the designation value, the context label, and the acceptability badge

### Requirement: Usage dialog handles large result sets
The usage dialog SHALL present usages in a paginated or otherwise bounded view and inform the user when the backend result was truncated.

#### Scenario: Context with many usages
- **WHEN** a context has more usages than a single page can display, or the backend indicates truncation
- **THEN** the dialog shows the bounded result and provides pagination or a truncation notice
