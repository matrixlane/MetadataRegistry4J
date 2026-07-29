# reference-document-link Specification

## Purpose
TBD - created by archiving change iso-11179-3-chapter-9-registration-package. Update Purpose after archive.
## Requirements
### Requirement: RegisteredItem reference association
The system SHALL support the `reference` association (ISO/IEC 11179-3:2023, 9.5.2) between `RegisteredItem` and `ReferenceDocument`, allowing zero or more reference documents to be linked to zero or more registered items.

#### Scenario: Link reference documents to a registered item
- **WHEN** an administrator assigns one or more reference documents to a registered item
- **THEN** the registered item persists the references and returns them on read

#### Scenario: Query registered items by reference document
- **WHEN** a client requests a reference document
- **THEN** the system returns the list of registered items linked to that document

### Requirement: REST API for reference association
The system SHALL expose endpoints to manage the reference association for a registered item under `/api/v1/registered-items/{id}/reference-documents`.

#### Scenario: Add reference documents
- **WHEN** a client sends `POST /api/v1/registered-items/{id}/reference-documents` with a list of reference document IDs
- **THEN** the system links those reference documents to the specified registered item

#### Scenario: Remove reference documents
- **WHEN** a client sends `DELETE /api/v1/registered-items/{id}/reference-documents` with a list of reference document IDs
- **THEN** the system removes those links without deleting the reference documents themselves

### Requirement: JPA mapping
The system SHALL map the `reference` association as a many-to-many join table named `registered_item_reference_document`.

#### Scenario: Database schema
- **WHEN** the application starts with `ddl-auto=update`
- **THEN** the join table `registered_item_reference_document` is created with foreign keys to `registered_item` and `reference_document`

