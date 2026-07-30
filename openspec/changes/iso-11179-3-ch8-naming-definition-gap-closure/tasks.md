## 1. Domain Model

- [x] 1.1 Create `NamingConvention` entity in `metadata-registry-common-facilities/src/main/java/.../designationanddefinition/` extending `Item` with JPA inheritance and a `rules` column.
- [x] 1.2 Add `NamingConvention` ↔ `Designation` many-to-many association via `designation_naming_convention` join table.
- [x] 1.3 Replace `Namespace.namingConventionId` (UUID) with `Namespace.namingConvention` (ManyToOne) while keeping the `naming_convention_id` column name.
- [x] 1.4 Add `Designation.namingConventions` collection and helper `addNamingConvention` / `removeNamingConvention` methods.
- [x] 1.5 Verify `Item` and `Concept` remain abstract (already done); add `NamingConvention` to `ISO_11179-3_2023_Comparison_Report.md` status table.

## 2. Repository & Service Layer

- [x] 2.1 Create `NamingConventionRepository` interface extending `JpaRepository` with query methods for `RegisteredItem` / `AdministeredItem` lookups if needed.
- [x] 2.2 Create `NamingConventionService` to handle creation, update, and rule serialization/deserialization.
- [x] 2.3 Update `NamespaceService` to validate `mandatoryNamingConventionIndicator` consistency when saving a namespace.
- [x] 2.4 Update `DesignationService` to manage `namingConventions` associations on create/update.

## 3. Web Module Integration

- [x] 3.1 Refactor `NamingConventionEngine` to accept a `NamingConvention` entity or its ID and load rules from the entity's `rules` column.
- [x] 3.2 Keep existing rule evaluation API unchanged for backward compatibility.
- [x] 3.3 Add REST endpoint or extend existing DTOs to expose `namingConventionId` for `Namespace` and `Designation` responses.
- [x] 3.4 Add validation error codes for missing mandatory naming convention and missing referenced convention.

## 4. Database Migrations

- [x] 4.1 Create `V20260724001__create_naming_convention.sql` to create `naming_convention` table with inheritance join and `rules` column.
- [x] 4.2 Create `V20260724002__namespace_naming_convention_fk.sql` to convert `namespace.naming_convention_id` to a foreign key and clean invalid values.
- [x] 4.3 Create `V20260724003__designation_naming_convention.sql` to create the association table.
- [x] 4.4 Run Flyway migrate against a local SQL Server test database and verify row counts.

## 5. Tests & Verification

- [x] 5.1 Add `NamingConventionRepositoryTest` covering create, read, update, and inheritance identity.
- [x] 5.2 Add `NamespaceNamingConventionAssociationTest` verifying foreign key migration and mandatory indicator validation.
- [x] 5.3 Add `DesignationNamingConventionAssociationTest` verifying many-to-many persistence and query.
- [x] 5.4 Update `NamingConventionEngineTest` to include a test case loading rules from a `NamingConvention` entity.
- [x] 5.5 Run `mvn test` for affected modules and ensure no regressions.

## 6. Documentation & Report Update

- [x] 6.1 Update `ISO_11179-3_2023_Comparison_Report.md` chapter 8 section to reflect `Naming_Convention` as implemented.
- [ ] 6.2 Document the JSON schema for `NamingConvention.rules` in `docs/` or README.
- [ ] 6.3 Add OpenAPI annotations or JavaDoc for new API fields referencing ISO/IEC 11179-3:2023 clause 8.4.4.
