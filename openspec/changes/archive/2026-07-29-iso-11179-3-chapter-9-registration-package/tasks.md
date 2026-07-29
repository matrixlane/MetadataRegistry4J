## 1. Domain Model

- [x] 1.1 Create `ConstraintSet` entity in `metadata-registry-common-facilities` with `itemShallBeDesignatedIndicator`, `itemShallBeDefinedIndicator`, `itemShallBeClassifiedIndicator` boolean attributes.
- [x] 1.2 Create `RegistrySpecification` entity in `metadata-registry-common-facilities` with all Table 28 attributes mapped to existing JPA types.
- [x] 1.3 Create `DegreeOfConformance` enum (`CONFORMING`, `STRICTLY_CONFORMING`) in `metadata-registry-common-facilities`.
- [x] 1.4 Add `constraintSet` `@ManyToOne` field to `RegisteredItem` and create the `registered_item_constraint_set` association.
- [x] 1.5 Add `referenceDocuments` `@ManyToMany` field to `RegisteredItem` and create the `registered_item_reference_document` join table.
- [x] 1.6 Add `APPLICATION` value to `RegistrationStatus` enum and update its Javadoc.

## 2. Data Access Layer

- [x] 2.1 Create `ConstraintSetRepository` extending `JpaRepository`.
- [x] 2.2 Create `RegistrySpecificationRepository` extending `JpaRepository`.
- [x] 2.3 Add query method `findByConstraintSetId` to `RegisteredItemRepository` if needed.
- [x] 2.4 Add query method `findByReferenceDocumentsId` to `RegisteredItemRepository` if needed.

## 3. Service Layer

- [x] 3.1 Create `ConstraintSetService` with CRUD operations.
- [x] 3.2 Create `RegistrySpecificationService` with CRUD operations.
- [x] 3.3 Create `RegisteredItemReferenceDocumentService` to add/remove reference documents for a registered item.
- [x] 3.4 Update `RegistrationStateService` to accept `APPLICATION` status in transitions.

## 4. REST API and DTOs

- [x] 4.1 Create request/response DTO records for `ConstraintSet` and `RegistrySpecification`.
- [x] 4.2 Create `ConstraintSetController` under `/api/v1/constraint-sets`.
- [x] 4.3 Create `RegistrySpecificationController` under `/api/v1/registry-specifications`.
- [x] 4.4 Add endpoints `POST /api/v1/registered-items/{id}/reference-documents` and `DELETE /api/v1/registered-items/{id}/reference-documents`.
- [x] 4.5 Verify Swagger UI lists all new endpoints.

## 5. Frontend

- [x] 5.1 Create `metadata-registry-ui/src/views/registry-spec/RegistrySpecificationSettings.vue` component for managing registry specifications.
- [x] 5.2 Add `src/api/registrySpecification.ts` API client for `GET /api/v1/registry-specifications`, `POST`, `PUT`, `DELETE` endpoints.
- [x] 5.3 Add i18n entries for `nav.registrySpecification` in supported locales (or verify existing entries work).
- [x] 5.4 Verify `/registry-specifications` route renders the new component without 404 / build error.
- [x] 5.5 (Optional) Create a placeholder or list page for `ConstraintSet` under `/constraint-sets` if UI management is required.

## 6. Seed Data and Migration

- [x] 6.1 Update `DataInitializer` to create a default `ConstraintSet` and a default `RegistrySpecification`.
- [x] 6.2 Ensure the default `ConstraintSet` requires designation, definition, and classification indicators set to `true`.
- [x] 6.3 (Optional) Add Flyway migration `V20260724__Add_registration_package_entities.sql` for production-grade schema tracking.

## 7. Tests

- [x] 7.1 Add unit tests for `ConstraintSetService` covering create, update, and delete.
- [x] 7.2 Add unit tests for `RegistrySpecificationService` covering create and update.
- [x] 7.3 Add integration tests for `ConstraintSetController` and `RegistrySpecificationController` via `@WebMvcTest` or `@SpringBootTest`.
- [x] 7.4 Add test verifying `APPLICATION` status is accepted in `RegistrationState` transitions.
- [x] 7.5 Run `mvn test` and ensure no regressions in existing registration tests.

## 8. Documentation and OpenSpec

- [x] 8.1 Update `ISO_11179-3_2023_Comparison_Report.md` to mark `Constraint_Set`, `Registry_Specification`, `reference` association, and `APPLICATION` status as planned/supplemented.
- [x] 8.2 Run `openspec validate --changes --json` and resolve any warnings.
- [x] 8.3 Clean up temporary analysis files created during this investigation.
