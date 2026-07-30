## ADDED Requirements

### Requirement: Comparison report marks new consistency items
`ISO_11179-3_2023_Comparison_Report.md` SHALL be updated in the chapter 8 section to reflect the status of the new capabilities.

#### Scenario: Report includes pairing context mandatory
- **WHEN** a reader opens the report
- **THEN** the row for “8.6.1 context_for_pairing 恰好一个 Context” shows status **Aligned** and references change `iso-11179-3-ch8-figure6-relation-consistency`

#### Scenario: Report includes minimum context/pairing warning
- **WHEN** a reader checks 8.4.1.4 and 8.4.2.4
- **THEN** both rows show **Aligned** with a note that enforcement is frontend-warning + user exception for legacy cases

#### Scenario: Report includes PREFERRED uniqueness
- **WHEN** a reader checks 8.7.1.3
- **THEN** the row shows **Aligned** with a note that the UI detects and highlights conflicts

### Requirement: Report includes namespace and naming convention display
The report SHALL mark `designation_namespace` (8.6.2) and `naming_convention_conformance` (8.6.5) as **Aligned** once the frontend can view and edit them.

#### Scenario: Reader checks association visibility
- **WHEN** a reader checks 8.6.2 and 8.6.5
- **THEN** both rows show **Aligned** and reference the relevant frontend and backend components
