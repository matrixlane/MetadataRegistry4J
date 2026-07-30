## 1. Backend DTO and data model support

- [x] 1.1 Extend `TextLineResponse` to include `namespaceIds` and `namingConventionIds` (designations) / `namespaceIds` (definitions).
- [x] 1.2 Extend `TextLineContextResponse` to include `acceptability`.
- [x] 1.3 Update `ItemAdministrationService.mapDesignations` and `mapDefinitions` to populate the new fields.
- [x] 1.4 Add `context_id NOT NULL` Flyway migration and data-fix script for existing null pairings.
- [x] 1.5 Add `BusinessException` validation in `DesignationDefinitionPairingService` when `contextId` is null.
- [x] 1.6 Add unit tests for DTO mapping and pairing context validation.

## 2. Frontend model and API types

- [x] 2.1 Update `TextLineForm` and `TextLineContextForm` types in `metadata-registry-ui` to include `namespaceIds`, `namingConventionIds`, and `acceptability`.
- [x] 2.2 Update `PairingForm` type to make `contextId` non-optional.
- [x] 2.3 Add utility functions to detect isolated lines and PREFERRED conflicts.
- [ ] 2.4 Add i18n strings for new labels and warnings (optional; current labels are Chinese hard-coded).

## 3. Pairing editor context mandatory

- [x] 3.1 Remove `clearable` from context selector in `DesignationDefinitionPairingEditor.vue` and show inline error when empty.
- [x] 3.2 Disable save/confirm button until every pairing row has a non-empty `contextId` (enforced by `ItemFormDialog` pre-validation).
- [ ] 3.3 Add component test verifying that a pairing without context cannot be saved (requires Vitest setup).
- [ ] 3.4 Add component test verifying that selecting a context enables save (requires Vitest setup).

## 4. Minimum context or pairing warning

- [x] 4.1 Add computed helper in `MultilingualTextEditor.vue` that determines whether a designation/definition row is isolated (no contexts and not referenced by any pairing).
- [x] 4.2 Render warning icon and message on isolated rows; provide an “允许例外” toggle.
- [x] 4.3 Block `ItemFormDialog` submission when isolated rows exist without exception; show confirmation dialog listing them with a one-click “全部标记为例外并保存” option.
 - [x] 4.4 Add `isolatedException` flag to `TextLine` DTO and propagate it from the frontend.
 - [x] 4.5 Add backend hard validation in `ItemAdministrationService` so every designation/definition line has at least one context or `isolatedException=true`.
 - [x] 4.6 Add backend unit tests for isolated-line validation.
 - [ ] 4.7 Add component tests for isolated-row detection and submission blocking (requires Vitest setup).

## 5. PREFERRED acceptability uniqueness

- [x] 5.1 Implement conflict detection across direct acceptability, context association acceptability, and pairing acceptability within the same item + context scope.
- [x] 5.2 Highlight conflicting rows in `MultilingualTextEditor.vue` and `RelationshipTable.vue`.
- [x] 5.3 Show a global conflict summary in `ItemFormDialog` when PREFERRED conflicts exist.
 - [x] 5.4 Add backend hard validation in `ItemAdministrationService` for direct PREFERRED uniqueness per language and per language+context scope in designations/definitions.
 - [x] 5.5 Add backend hard validation in `DesignationDefinitionPairingService` for at most one PREFERRED pairing per item+context.
 - [x] 5.6 Add backend unit tests for PREFERRED uniqueness validation.
 - [ ] 5.7 Add component tests for conflict detection and rendering (requires Vitest setup).

## 6. Per-line namespace and naming convention

- [x] 6.1 Add an expandable “命名空间与命名约定” section to `MultilingualTextEditor.vue` rows.
- [x] 6.2 Load namespace and naming-convention options and render multi-select selectors.
- [x] 6.3 Emit `namespaceIds` and `namingConventionIds` in the row payload.
- [x] 6.4 Add namespace and naming-convention columns to `MetadataDetailView.vue` relationship tables.
- [ ] 6.5 Add component tests for selector rendering and payload emission (requires Vitest setup).

## 7. Association-class acceptability display

- [x] 7.1 Update `RelationshipTable.vue` context-tag rendering to include `acceptability` next to the context label.
- [x] 7.2 Use `AcceptabilityBadge` for the acceptability portion of the tag.
- [x] 7.3 Ensure color mapping matches existing `AcceptabilityBadge` semantics.
- [ ] 7.4 Add component test verifying tag text and badge type (requires Vitest setup).

## 8. Context usage dialog grouping

- [x] 8.1 Group `ContextUsageDialog.vue` usages by `itemId` using grouped tables.
- [x] 8.2 Display association type badge and acceptability badge in each usage row.
- [x] 8.3 Add navigation links to the owning item in each usage row and in the group header.
- [ ] 8.4 Add component test for grouping and row rendering (requires Vitest setup).

## 9. TypeScript build cleanup

- [x] 9.1 Fix `el-table-column prop="..."` type errors across all `*List.vue` files using the `:(prop="('x.y') as any")` pattern or typed constants.
- [x] 9.2 Extend the `User` type in `src/stores/auth.ts` to include all backend-returned fields (`emergencyLogin`, `forceChangePassword`, `lastLoginAt`, `sysRole`, `isRegistrar`).
- [x] 9.3 Resolve any remaining `AppLayout.vue`, `ClassificationTreeBrowser.vue`, `InteropMappingList.vue`, and `MappingNetworkView.vue` type errors.
- [x] 9.4 Run `npm run build` and verify zero errors.

## 10. Documentation and verification

- [x] 10.1 Update `ISO_11179-3_2023_Comparison_Report.md` chapter 8 to mark pairing-context mandatory, minimum context/pairing warning, PREFERRED uniqueness, namespace/naming-convention display, and association acceptability as aligned.
- [x] 10.2 Run `mvn test -pl metadata-registry-web` and confirm related tests pass; document any unrelated pre-existing failures.
- [x] 10.3 Run `npm run build` and confirm zero errors.
- [x] 10.4 Run `openspec validate --all` and confirm all changes pass.
- [ ] 10.5 (Optional) Add Playwright E2E test covering the new relationship-consistency interactions in `MetadataDetailView.vue`.
