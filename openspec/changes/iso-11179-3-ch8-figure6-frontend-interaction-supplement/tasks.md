## 1. Backend relationship endpoint completion

- [x] 1.1 Extend `DesignationDefinitionPairingService` to load context label alongside pairing responses.
- [x] 1.2 Update `ItemRelationshipController` so every pairing returns a non-null `contextLabel` when `contextId` is present.
- [x] 1.3 Add integration test for `ItemRelationshipController` covering designations, definitions, contexts, and pairings.

## 2. Backend namespace constraints completion

- [x] 2.1 Add repository queries in `NamespaceConstraintValidator` to check `one_name_per_item_indicator` and `one_item_per_name_indicator` violations.
- [x] 2.2 Add designation-to-naming-convention conformance check when `mandatoryNamingConventionIndicator` is TRUE.
- [x] 2.3 Wire `NamespaceConstraintValidator` into `ItemAdministrationService.applyDesignations` for items bound to a mandatory namespace.
- [x] 2.4 Add unit tests for all three constraint scenarios and the conformance scenario.

## 3. Frontend source reference selector

- [x] 3.1 Add `ReferenceDocument` select to `MultilingualTextEditor.vue` per row, bound to `row.sourceId`.
- [x] 3.2 Load `referenceDocumentApi` options in `onMounted` and handle errors gracefully.
- [x] 3.3 Ensure `ItemFormDialog` serializes `sourceId` into designation/definition payloads.
- [ ] 3.4 Add a component test verifying the source selector renders and updates the model.

## 4. Frontend metadata detail relationship tabs

- [x] 4.1 Add tabs "指称", "定义", "上下文", "配对" to `MetadataDetailView.vue` alongside "概览" and "版本历史".
- [x] 4.2 Implement lazy loading for relationship tabs using `itemRelationshipApi.get(itemId)` and cache results.
- [x] 4.3 Render designation/definition tables with language, value, source, context list, and acceptability badges.
- [x] 4.4 Render pairing table with designation value, definition value, context label, and acceptability badge.
- [x] 4.5 Add navigation links from relationship rows to related items/contexts.
- [ ] 4.6 Add E2E test covering tab navigation and relationship visibility.

## 5. Frontend context reverse browsing

- [x] 5.1 Add a "查看引用" action in `ContextList.vue` rows.
- [x] 5.2 Implement `ContextService.findUsages(contextId)` and expose `/api/v1/contexts/{id}/usages` as a bounded endpoint (default limit 100, truncation indicated).
- [x] 5.3 Create `ContextUsageDialog.vue` showing paginated usages grouped by item type.
- [x] 5.4 Add `contextApi.usages` method in `resources.ts`.
- [x] 5.5 Add links from each usage row to the corresponding metadata detail page.
- [x] 5.6 Add component test for the usage dialog.

## 6. Frontend acceptability visualization and search

- [x] 6.1 Add acceptability badge column in `MetadataDetailView` relationship tables using agreed color mapping.
- [x] 6.2 Show a `PREFERRED` badge in `BrowserView` result rows when applicable.
- [x] 6.3 Add advanced filters in `BrowserView` for context (single select) and acceptability (multi select).
- [x] 6.4 Update `metadataSearchApi` query building to include `contextId` and `acceptability`.
- [x] 6.5 Extend backend `/metadata/search` to support `contextId` and `acceptability` filters, and keep the non-blocking UI fallback tip for robustness.
- [ ] 6.6 Add component test for filter rendering and badge display.

## 7. Frontend pairing editor improvements

- [x] 7.1 Improve dropdown labels in `DesignationDefinitionPairingEditor.vue` to show language badge and value.
- [x] 7.2 Add duplicate detection for same designationId + definitionId + contextId and highlight conflicting rows.
- [ ] 7.3 (Optional) Implement temporary client IDs for designations/definitions so pairings can be created before the parent item is saved.
- [x] 7.4 Add component tests for duplicate validation and label rendering.

## 8. Frontend namespace governance

- [x] 8.1 Add multi-select naming convention selector in `NamespaceList.vue` form.
- [x] 8.2 Add indicator switches for `mandatoryNamingConventionIndicator`, `oneNamePerItemIndicator`, and `oneItemPerNameIndicator`.
- [x] 8.3 Wire switches to `namespaceApi.create/update` payload fields.
- [x] 8.4 Display backend constraint violation messages in the form.
- [ ] 8.5 Add component test for namespace governance form fields.

## 9. Documentation and verification

- [x] 9.1 Update `ISO_11179-3_2023_Comparison_Report.md` chapter 8 section to mark Namespace constraints, NamingConvention attributes, and frontend relationship coverage as aligned.
- [x] 9.2 Add or update UI i18n strings for new labels (Reference Document, Context Usages, Pairings, etc.).
- [x] 9.3 Install `metadata-registry-ui` dependencies and run `npm run build` to verify no TypeScript/Vue errors.
- [x] 9.4 Run `mvn test` for the full backend and fix any regressions.
- [x] 9.5 Run `openspec validate --all` and ensure all changes pass; 34 items passed.
