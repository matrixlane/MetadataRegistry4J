## 1. Backend Service & Endpoint

- [x] 1.1 Create `FactoryResetService` implementing password validation and dynamic SQL-based database clearing logic (disabling/enabling foreign key constraints, executing DELETE, resetting identity sequences, and re-seeding the default `admin` user)
- [x] 1.2 Create `SystemController` (or add to `InitWizardController`) providing `POST /api/v1/system/factory-reset` endpoint, guarded by `ROLE_ADMIN` authorization and requiring password verification
- [x] 1.3 Update Flyway migration script if any table or setting changes are needed (none expected, but verify)

## 2. Frontend UI Configuration

- [x] 2.1 Add a "恢复出厂设置" (Factory Reset) section under the System Management/Settings page in `metadata-registry-ui` (danger zone)
- [x] 2.2 Create a double-confirmation modal displaying clear risk warnings and a password input field
- [x] 2.3 Implement frontend state clearing (localStorage, Pinia authentication tokens/user stores) and trigger a redirect to `/init-wizard` upon successful reset response

## 3. Testing & Verification

- [x] 3.1 Write unit tests for `FactoryResetService` validating incorrect password rejection and mock database interaction
- [x] 3.2 Write integration tests calling the reset endpoint, verifying that all business metadata (Data Elements, Conceptual Domains, etc.) are deleted, and that subsequent API requests are rejected as unauthorized
- [x] 3.3 Write Playwright E2E tests simulating the admin logging in, navigating to system settings, performing a factory reset, and confirming the immediate redirection to the initialization wizard
