## 1. Database & Entities Setup

- [x] 1.1 Create `SystemSetting` JPA entity and corresponding repository for persisting running mode and initialization status
- [x] 1.2 Create `NamespacePermission` JPA entity and corresponding repository for access control mapping
- [x] 1.3 Update `Contact` JPA entity by moving `user` association from `Registrar` up to the `Contact` class
- [x] 1.4 Write Flyway migration script `V20260530001__Create_init_wizard_and_permission_tables.sql` to add new tables and modify the contact table structure

## 2. Backend Core Services

- [x] 2.1 Implement `SystemSettingService` to query and save run mode and initialization status
- [x] 2.2 Implement `NamespacePermissionEvaluator` service providing `hasWritePermission(namespaceId, appUserId)` with hierarchical resolution logic
- [x] 2.3 Enhance `RegistrarService` to support auto-creating standard `User` and `Individual` when assigning Super Admin as Registrar
- [x] 2.4 Enhance `NamespaceService` to default `namingAuthority` to the owning Registration Authority and reject edits if it's an internal namespace
- [x] 2.5 Add deletion check logic in `NamespaceService` verifying if `ScopedIdentifier` exists and return details for the deletion dialogue warning

## 3. Web API Controllers

- [x] 3.1 Create `InitWizardController` providing endpoints `/api/v1/init-wizard/status` and `/api/v1/init-wizard/setup`
- [x] 3.2 Add `POST /api/v1/contacts/batch` endpoint in `ContactController` for batch importing and configuring contacts, organizations, and roles
- [x] 3.3 Add external namespace endpoints in `NamespaceController` supporting auto-creation of naming organizations
- [x] 3.4 Create `NamespacePermissionController` to configure organization and user level permission mappings per namespace

## 4. Security & Interceptor Integration

- [x] 4.1 Implement `InitializationWizardInterceptor` that redirects all non-auth and non-wizard APIs to the initialization screen when initialized state is false
- [x] 4.2 Integrate the namespace permission aspect/interceptor to intercept save/update/delete endpoints on namespaces and namespace-scoped entities, returning 403 when permissions are insufficient

## 5. Frontend Initialization Wizard UI

- [x] 5.1 Implement Route Guard in `router/index.ts` to detect initialization status and redirect to `/init-wizard` if uninitialized
- [x] 5.2 Create `/init-wizard` page with step-by-step layout using Element Plus wizard component
- [x] 5.3 Implement Step 1: System Run Mode selection (Single mode active, Multi mode disabled)
- [x] 5.4 Implement Step 2: Default Registration Authority details entry
- [x] 5.5 Implement Step 3: Super Admin registrar self-assignment switch and namespace initialization prompt

## 6. Frontend Organization, Contact & Permission UI

- [x] 6.1 Implement Contact Batch configuration view allowing registrars to upload/add multiple contacts and assign organizations and roles
- [x] 6.2 Implement Namespace deletion warning prompt showing warning modal when namespace is occupied by scoped identifiers
- [x] 6.3 Implement Namespace Permission settings panel in the Namespace management page allowing registrars to configure user/organization ACLs

## 7. Testing & Verification

- [x] 7.1 Write unit tests for `NamespacePermissionEvaluator` verifying user-level, organization-level, and default read-only permissions
- [x] 7.2 Write integration tests for `InitWizardController` setup process and check database states afterwards
- [x] 7.3 Write Playwright End-to-End tests simulating the first login redirect, wizard completion, and namespace creation flow
