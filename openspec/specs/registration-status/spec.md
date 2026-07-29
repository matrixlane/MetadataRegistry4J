# registration-status Specification

## Purpose
TBD - created by archiving change iso-11179-3-chapter-9-registration-package. Update Purpose after archive.
## Requirements
### Requirement: RegistrationStatus covers ISO/IEC 11179-3:2023 states
The system SHALL provide a `RegistrationStatus` enumeration that includes all statuses defined in ISO/IEC 11179-3:2023, 9.6.2: `INCOMPLETE`, `SUBMITTED`, `RECORDED`, `QUALIFIED`, `STANDARD`, `PREFERRED`, `SUPERSEDED`, `RETIRED`, `HISTORICAL`, and `APPLICATION`.

#### Scenario: Application state is selectable
- **WHEN** an administrator creates or updates a `Registration_State`
- **THEN** the system accepts `APPLICATION` as a valid registration status

#### Scenario: State transition does not reject Application
- **GIVEN** an administered item is in a non-terminal status
- **WHEN** the administrator transitions it to `APPLICATION`
- **THEN** the transition is persisted without validation errors

