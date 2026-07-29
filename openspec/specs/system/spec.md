# system Specification

## Purpose
TBD - created by archiving change registry-init-wizard. Update Purpose after archive.
## Requirements
### Requirement: System Mode Persistence
The system MUST persist the system run mode (Single Registry vs Multi Registry) and initialization status (Initialized vs Uninitialized) in the database.

#### Scenario: Verify system initialization status is read
- **WHEN** the system boots up
- **THEN** it reads the system initialization status and run mode from the database to determine whether to redirect incoming admin requests to the wizard

### Requirement: Namespace Operation Access Control
The system MUST intercept all write operations (create, update, delete) on Namespace objects, Scoped Identifier objects, or items within namespaces.
- The system SHALL verify if the authenticated user has write permission for the target namespace according to the Namespace Access Permission Hierarchy.
- If the user does not have write permission, the system MUST return a 403 Forbidden response.

#### Scenario: User without write permission attempts to create item in namespace
- **WHEN** a user who has only read-only access to Namespace N attempts to register a new Data Element inside Namespace N
- **THEN** the system intercepts the request and returns a 403 Forbidden error

