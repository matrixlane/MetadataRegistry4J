## ADDED Requirements

### Requirement: Comparison report reflects supplement alignment
`ISO_11179-3_2023_Comparison_Report.md` SHALL be updated in the chapter 8 section to reflect the status delivered by this supplement change.

#### Scenario: Report includes namespace constraint enforcement
- **WHEN** a reader checks the rows for `one_name_per_item_indicator`, `one_item_per_name_indicator`, and designation-to-naming-convention conformance
- **THEN** those rows show **Aligned** and reference change `iso-11179-3-ch8-figure6-frontend-interaction-supplement`

#### Scenario: Report includes frontend relationship coverage
- **WHEN** a reader checks the rows for designation/definition source reference, context reverse browsing, relationship detail tabs, acceptability visualization, and search by context or acceptability
- **THEN** those rows show **Aligned** and reference the relevant frontend components and backend endpoints

#### Scenario: Report lists remaining gaps
- **WHEN** a reader reviews the chapter 8 section after the update
- **THEN** any items still not aligned are explicitly listed with their status and a short rationale
