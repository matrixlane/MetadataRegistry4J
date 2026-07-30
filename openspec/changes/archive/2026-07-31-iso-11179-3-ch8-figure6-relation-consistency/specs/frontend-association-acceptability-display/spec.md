## ADDED Requirements

### Requirement: Context tags show association acceptability
`RelationshipTable.vue` SHALL render each context tag in the designation/definition table as `contextLabel + acceptabilityLabel`, reflecting the `acceptability` attribute on the `Designation_Context` / `Definition_Context` association class.

#### Scenario: Context association has PREFERRED acceptability
- **WHEN** a designation row has a context association with `acceptability = PREFERRED`
- **THEN** the table shows a tag such as “业务上下文 · 首选” with success styling

#### Scenario: Context association has ADMITTED acceptability
- **WHEN** a context association has `acceptability = ADMITTED`
- **THEN** the tag shows “业务上下文 · 认可” with info styling

### Requirement: Use AcceptabilityBadge for context association acceptability
The context acceptability portion of the tag SHALL use the existing `AcceptabilityBadge` component.

#### Scenario: Row renders with badge
- **WHEN** the relationship table renders a context tag with acceptability
- **THEN** the `AcceptabilityBadge` component is used for the acceptability part of the tag
