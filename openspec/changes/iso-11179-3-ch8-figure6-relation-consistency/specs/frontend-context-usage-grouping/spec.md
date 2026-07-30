## ADDED Requirements

### Requirement: Context usage dialog groups by owning item
`ContextUsageDialog.vue` SHALL group usages by the owning item, showing an expandable/collapsible group per item.

#### Scenario: Context is used by multiple items
- **WHEN** the user opens the context usages dialog for a context referenced by three different items
- **THEN** the dialog shows three groups, each with the item label as the header
- **AND** expanding a group reveals the designation, definition, and pairing rows that reference the context

### Requirement: Show association type and acceptability in usage rows
Each usage row SHALL display the type (`Designation_Context`, `Definition_Context`, `Designation_Definition_Pairing`), the line value, and the acceptability rating.

#### Scenario: User reviews a designation-context usage
- **WHEN** the group for item X is expanded
- **THEN** a row shows “指称：客户名称 | 可接受性：首选”

#### Scenario: User reviews a pairing usage
- **WHEN** a pairing references the context
- **THEN** a row shows “配对：客户名称 ↔ 客户定义 | 上下文：业务上下文 | 可接受性：认可”
