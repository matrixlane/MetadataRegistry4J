# initialization-wizard Specification

## Purpose
TBD - created by archiving change registry-init-wizard. Update Purpose after archive.
## Requirements
### Requirement: Super Admin Run Mode Choice
Upon first login, the system MUST require the Super Administrator to configure the system run mode before allowing access to any other functions.
The system SHALL support two run modes:
1. **Single Registry Mode** (Default; allows only one Registration Authority to be created).
2. **Multi Registry Mode** (Allows multiple Registration Authorities; TBD, currently disabled/unavailable in the UI).

#### Scenario: Admin first login system run mode configuration
- **WHEN** the default Admin user logs in for the first time
- **THEN** the system redirects the user to the initialization wizard and prompts them to choose a run mode, where "Single Registry Mode" is enabled and "Multi Registry Mode" is disabled

### Requirement: Default Registration Authority Creation
When Single Registry Mode is chosen, the initialization wizard MUST require the Super Administrator to create a default Registration Authority.

#### Scenario: Admin creates default Registration Authority
- **WHEN** the Super Administrator selects Single Registry Mode and enters valid details for the default Registration Authority
- **THEN** the system creates the Registration Authority and proceeds to the registrar assignment step

### Requirement: Super Admin Registrar Self Assignment
After creating the Registration Authority, the initialization wizard MUST ask the Super Administrator if they want to assign themselves as the Registrar (Registration Administrator) for the newly created Registration Authority.
If yes:
- The system SHALL create a Registrar record associated with the Registration Authority.
- The system SHALL create a corresponding standard User (or use the current user's profile) and link it to the Registrar via the parent class Contact.
- The system SHALL query if the user wants to create a Namespace.
If no:
- The system SHALL proceed without creating a Registrar. The Super Administrator can assign themselves or other users as Registrars later.

#### Scenario: Super Admin chooses to become Registrar
- **WHEN** the Super Administrator answers "Yes" to becoming the Registrar during the wizard
- **THEN** the system creates a Registrar entity linked to the Super Admin User and the Registration Authority, and shows the Namespace creation prompt

#### Scenario: Super Admin chooses not to become Registrar
- **WHEN** the Super Administrator answers "No" to becoming the Registrar during the wizard
- **THEN** the system skips Registrar creation and allows completion of the initialization flow without Registrar creation

### Requirement: Namespace Creation by Registrar
When no Namespace exists in a Registration Authority, any Registrar of that Registration Authority MUST have the authority to create a Namespace.
- The naming authority (`naming_authority`) for this Namespace MUST default to the Registration Authority and SHALL NOT be editable.

#### Scenario: Registrar creates Namespace in empty Registration Authority
- **WHEN** a Registrar creates a Namespace for their Registration Authority when none exists
- **THEN** the system creates the Namespace and sets its naming authority to the Registration Authority, disabling editing of the naming authority field

### Requirement: Namespace Deletion Warning
A Registrar MUST have the authority to delete a Namespace. However, if any Scoped Identifier is already associated with this Namespace, the system MUST show a warning warning the user of potential data integrity issues before allowing them to confirm the deletion.

#### Scenario: Registrar deletes Namespace with associated Scoped Identifier
- **WHEN** a Registrar attempts to delete a Namespace that has one or more Scoped Identifiers associated with it
- **THEN** the system displays a warnings dialogue explaining that data is associated with it, and requires explicit double-confirmation before deleting

### Requirement: External Namespace Creation
A Registrar MUST be able to create an external Namespace.
- The external Namespace SHALL NOT be associated with any Registration Authority.
- The Registrar SHALL be allowed to specify an external Organization as its naming authority.
- The system MUST support either automatically creating a new Organization or choosing an existing Organization as the naming authority.

#### Scenario: Registrar creates external Namespace with new Organization
- **WHEN** a Registrar creates an external Namespace, inputs a new Organization name, and saves
- **THEN** the system automatically creates the new Organization object, links it as the naming authority for the external Namespace, and leaves the registration authority empty

#### Scenario: Registrar creates external Namespace with existing Organization
- **WHEN** a Registrar creates an external Namespace, selects an existing Organization, and saves
- **THEN** the system links the existing Organization as the naming authority for the external Namespace, and leaves the registration authority empty

### Requirement: Organization Addition
A Registrar MUST have the ability to create and add new Organization objects to the system.

#### Scenario: Registrar adds an Organization
- **WHEN** a Registrar submits a valid name and details for a new Organization
- **THEN** the system creates the Organization object in the registry

### Requirement: Contact Batch Configuration
A Registrar MUST have the ability to register other registered users as Contacts, assign them to a specific Organization, and set their predefined roles (e.g., Submitter, Steward, etc.). The system MUST support batch additions and settings.

#### Scenario: Registrar batch adds Contacts with roles
- **WHEN** a Registrar selects multiple registered users, selects an Organization, assigns the "Steward" role, and submits
- **THEN** the system creates Contact records for each selected user, linking them to the Organization and the Steward role

### Requirement: Default Read-Only Access
Any user who is not registered as a Contact, or is registered as a Contact but has no role assigned, MUST have default read-only access to all registry namespaces.

#### Scenario: Non contact user views registry
- **WHEN** a logged-in user who is not a Contact attempts to read data or write data
- **THEN** the system allows reading but blocks any write actions with a permission-denied error

### Requirement: Namespace Access Permission Hierarchy
A Registrar MUST be able to configure permissions (Access Control List) for organizations and users on different namespaces. The access validation MUST follow this hierarchy:
1. If a user belongs to an organization (Contact), the user SHALL inherit the organization's permissions by default.
2. If the organization has no permissions configured, the default organization permission SHALL be read-only access to all namespaces.
3. If a user belongs to an organization, any permissions explicitly configured on the user level MUST override the organization's permissions.
4. If a user does not belong to any organization, and the user has no explicit permissions set, the user SHALL default to read-only access to all namespaces.

#### Scenario: User inherits organization permissions
- **WHEN** a user belongs to Organization A, which has write access to Namespace N, and the user has no user-specific permissions
- **THEN** the system allows the user to write to Namespace N

#### Scenario: User permission overrides organization permission
- **WHEN** a user belongs to Organization A (which has write access to Namespace N), but the user is explicitly set to read-only access on Namespace N
- **THEN** the system blocks the user from writing to Namespace N

#### Scenario: Non contact user default permissions
- **WHEN** a user who does not belong to any organization has no explicit permissions configured
- **THEN** the system grants the user read-only access to all namespaces and blocks any write requests

