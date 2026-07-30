## 1. Backend: Namespace ↔ NamingConvention many-to-many

- [x] 1.1 Add `List<NamingConvention> namingConventions` to `Namespace` entity with `@ManyToMany` and join table `namespace_naming_convention`; keep existing `namingConvention` single reference as read-only compatibility.
- [x] 1.2 Add `namingConventionIds` to `NamespaceRequest` and `NamespaceResponse` DTOs.
- [x] 1.3 Update `NamespaceService` to persist and load the many-to-many association.
- [x] 1.4 Add Flyway migration `V20260724003__Namespace_naming_convention_many_to_many.sql` to create join table and copy existing `naming_convention_id` values.
- [x] 1.5 Update `NamespaceControllerTest` and `NamespaceServiceTest` to cover multiple naming conventions and migration.

## 2. Backend: Namespace constraint validation

- [x] 2.1 Create `NamespaceConstraintValidator` to enforce `mandatoryNamingConventionIndicator`, `oneNamePerItemIndicator`, and `oneItemPerNameIndicator` constraints.
- [x] 2.2 Wire validator into `NamespaceService.create/update` and `ItemAdministrationService.applyDesignations` (when namespace is mandatory).
- [x] 2.3 Return `400 Bad Request` with clear messages for each constraint violation.
- [x] 2.4 Add unit tests for each constraint scenario.

## 3. Backend: NamingConvention standard attributes

- [x] 3.1 Add `scopeRule`, `authorityRule`, `semanticRule`, `syntacticRule`, `lexicalRule` columns/fields to `NamingConvention` entity.
- [x] 3.2 Add fields to `NamingConventionRequest`/`NamingConventionResponse`.
- [x] 3.3 Extend `NamingConventionEngine` to parse standard attributes into executable rules when `rulesJson` is absent.
- [x] 3.4 Add Flyway migration `V20260724004__Naming_convention_standard_attrs.sql`.
- [x] 3.5 Add tests for engine rule generation from standard attributes.

## 4. Backend: Direct acceptability for Designation and Definition

- [x] 4.1 Add optional `Acceptability acceptability` field to `Designation` and `Definition` entities with DB columns.
- [x] 4.2 Include `acceptability` in `TextLineResponse` and map from entity or context fallback.
- [x] 4.3 Add Flyway migration `V20260724005__Designation_definition_acceptability.sql`.
- [x] 4.4 Add JPA tests to verify persistence and mapping.

## 5. Backend: Relationship endpoint enrichment

- [x] 5.1 Extend `ItemRelationshipResponse` with `contexts` and `namingConventions` arrays.
- [x] 5.2 Update `ItemRelationshipController` to collect related contexts and naming conventions for the item.
- [x] 5.3 Add integration test for `ItemRelationshipController`.

## 6. Frontend: Source selector in MultilingualTextEditor

- [x] 6.1 Add a `ReferenceDocument` `el-select` per row in `MultilingualTextEditor.vue`, bound to `row.sourceId`.
- [x] 6.2 Load `referenceDocumentApi.list` in `onMounted` and provide options.
- [x] 6.3 Ensure `ItemFormDialog` includes `sourceId` in the payload for designations and definitions.
- [ ] 6.4 Add a unit test verifying the source selector renders and emits.

## 7. Frontend: Relationship tabs in MetadataDetailView

- [x] 7.1 Add 「指称」「定义」「上下文」「配对」 lazy-loaded tabs to `MetadataDetailView.vue`.
- [x] 7.2 Call `itemRelationshipApi.get(itemId)` on tab switch and cache results.
- [x] 7.3 Render tables for designations, definitions, contexts, and pairings with acceptability badges.
- [x] 7.4 Add navigation links from relationship rows to related items/contexts.

## 8. Frontend: Context reverse browsing

- [x] 8.1 Add a 「查看引用」 action in `ContextList.vue` rows.
- [x] 8.2 Implement `contextApi.findUsages(contextId)` call and a dialog to display paginated results.
- [x] 8.3 Display readable labels (item, designation, definition, acceptability) in the usage dialog.
- [x] 8.4 Add backend `ContextService.findUsages` method and endpoint if not already present.

## 9. Frontend: Acceptability badges and search filters

- [x] 9.1 Add an `Acceptability` badge column in relationship tables using the agreed color mapping.
- [x] 9.2 Show a `PREFERRED` badge in `BrowserView` list rows and `MetadataDetailView` header.
- [x] 9.3 Add advanced filters in `BrowserView` for context (single select) and acceptability (multi select).
- [x] 9.4 Update `metadataSearchApi` query building to include `contextId` and `acceptability`.
- [x] 9.5 Extend backend `/metadata/search` to support context and acceptability filters (or show UI fallback if not yet supported).

## 10. Frontend: Pairing editor improvements

- [x] 10.1 Add duplicate-pairing validation in `DesignationDefinitionPairingEditor.vue` before emit/save.
- [x] 10.2 Enhance designation/definition dropdown options to display language and context tags.
- [ ] 10.3 Implement optional temporary-ID pairing flow in `ItemFormDialog.vue` and backend ID mapping (phase 2).
- [x] 10.4 Add component tests for duplicate validation and option rendering.

## 11. Frontend: Namespace naming convention selector

- [x] 11.1 Add a multi-select naming convention selector in `NamespaceList.vue` form.
- [x] 11.2 Add indicator switches for `mandatoryNamingConventionIndicator`, `oneNamePerItemIndicator`, and `oneItemPerNameIndicator`.
- [x] 11.3 Ensure the switches are wired to `namespaceApi.create/update` payload fields.
- [x] 11.4 Add field validation messages from backend constraint violations.

## 12. Documentation and verification

- [x] 12.1 Update `ISO_11179-3_2023_Comparison_Report.md` section 8 status: mark Namespace/NamingConvention relationships, constraints, and standard attributes as aligned.
- [x] 12.2 Run `mvn test` and fix failures introduced by new constraints or migrations; 1038 tests passed, 0 failures.
- [x] 12.3 Run `npm run build` in `metadata-registry-ui` and fix TypeScript/Vue errors; build succeeded.
- [x] 12.4 Run `openspec validate --all` and ensure all changes pass; 34 items passed.
