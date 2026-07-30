## ADDED Requirements

### Requirement: Context list provides a usage browser
The system SHALL provide an action in the context list that opens a usage view showing all designations, definitions, and designation-definition pairings that reference the selected context.

#### Scenario: View context usages from context list
- **WHEN** a registrar clicks "View Usages" on a context row
- **THEN** the system opens a dialog or drawer showing the referencing designations, definitions, and pairings grouped by item type

#### Scenario: Context usage view supports pagination
- **GIVEN** a context is referenced by more than 20 items
- **WHEN** the usage view opens
- **THEN** the results are paginated and the total count is displayed

### Requirement: Context detail shows its own designations and definitions
The system SHALL allow a context item to be viewed with its own designations and definitions, consistent with the metadata detail view for other item types.

#### Scenario: Open context detail
- **WHEN** a user clicks a context identifier or name in the context list
- **THEN** the system navigates to a detail view showing the context's designations, definitions, and registration information
