## ADDED Requirements

### Requirement: Editor exposes per-line namespace and naming convention
`MultilingualTextEditor.vue` SHALL provide an expandable section on each designation/definition row for selecting the related namespaces and naming conventions, exposing the `designation_namespace` (8.6.2) and `naming_convention_conformance` (8.6.5) associations.

#### Scenario: User selects namespaces for a designation
- **WHEN** the user expands the “命名空间与命名约定” section on a designation row and selects one or more namespaces
- **THEN** the emitted `TextLineForm` includes `namespaceIds` with the selected values

#### Scenario: User selects naming conventions for a designation
- **WHEN** the user selects naming conventions in the same section
- **THEN** the emitted `TextLineForm` includes `namingConventionIds` with the selected values

### Requirement: Relationship table displays namespace and naming convention tags
`MetadataDetailView.vue` SHALL show namespace and naming convention tags for each designation row in the relationship table.

#### Scenario: User views designations tab
- **WHEN** the user opens the “指称” tab
- **THEN** each row displays the designation value, language, source, contexts, acceptability, and tags for namespaces and naming conventions
