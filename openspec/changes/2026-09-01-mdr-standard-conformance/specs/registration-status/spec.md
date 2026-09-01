## MODIFIED Requirements

### Requirement: RegistrationStatus covers ISO/IEC 11179-3:2023 states
The system SHALL provide a `RegistrationStatus` enumeration whose values correspond exactly to the statuses defined in ISO/IEC 11179-3:2023, Table 30 (9.6.2) and ISO/IEC 11179-6:2023, Table 1 (4.3.2): `INCOMPLETE`, `CANDIDATE`, `RECORDED`, `QUALIFIED`, `STANDARD`, `PREFERRED_STANDARD`, `SUPERSEDED`, `RETIRED`, `HISTORICAL`, and `APPLICATION`. The Java enum constant `PREFERRED_STANDARD` corresponds to the standard status name "Preferred Standard"; `CANDIDATE` corresponds to "Candidate". The status `SUBMITTED` does not exist in the standard and SHALL NOT appear.

The persistence layer SHALL read string-persisted registration status case-insensitively and tolerate the standard's written form (including the space in "Preferred Standard"), mapping it to the corresponding Java enum constant, so that data imported in standard written form does not raise an enum resolution error.

#### Scenario: Candidate state is selectable
- **WHEN** an administrator creates or updates a `Registration_State`
- **THEN** the system accepts `CANDIDATE` as a valid registration status

#### Scenario: Preferred Standard persisted in standard written form is readable
- **WHEN** the database column `registration_state.registration_status` contains the standard written value `Preferred Standard`
- **THEN** the system reads it back as the enum constant `PREFERRED_STANDARD` without raising `No enum constant`

#### Scenario: Submitted is not a valid status
- **WHEN** any code path attempts to resolve `SUBMITTED` as a `RegistrationStatus`
- **THEN** the resolution fails, because `SUBMITTED` is not defined in the standard

#### Scenario: State transition does not reject Candidate
- **GIVEN** an administered item is in `INCOMPLETE` status
- **WHEN** the administrator transitions it to `CANDIDATE`
- **THEN** the transition is persisted without validation errors
