## 1. Backend relationship endpoint completion

- [ ] 1.1 Extend `DesignationDefinitionPairingService` to load context label alongside pairing responses.
- [ ] 1.2 Update `ItemRelationshipController` so every pairing returns a non-null `contextLabel` when `contextId` is present.
- [ ] 1.3 Add integration test for `ItemRelationshipController` covering designations, definitions, contexts, and pairings.

## 2. Backend namespace constraints completion

- [ ] 2.1 Add repository queries in `NamespaceConstraintValidator` to check `one_name_per_item_indicator` and `one_item_per_name_indicator` violations.
- [ ] 2.2 Add designation-to-naming-convention conformance check when `mandatoryNamingConventionIndicator` is TRUE.
- [ ] 2.3 Wire `NamespaceConstraintValidator` into `ItemAdministrationService.applyDesignations` for items bound to a mandatory namespace.
- [ ] 2.4 Add unit tests for all three constraint scenarios and the conformance scenario.

## 3. Frontend source reference selector

- [ ] 3.1 Add `ReferenceDocument` select to `MultilingualTextEditor.vue` per row, bound to `row.sourceId`.
- [ ] 3.2 Load `referenceDocumentApi` options in `onMounted` and handle errors gracefully.
- [ ] 3.3 Ensure `ItemFormDialog` serializes `sourceId` into designation/definition payloads.
- [ ] 3.4 Add a component test verifying the source selector renders and updates the model.

## 4. Frontend metadata detail relationship tabs

- [ ] 4.1 Add tabs "指称", "定义", "上下文", "配对" to `MetadataDetailView.vue` alongside "概览" and "版本历史".
- [ ] 4.2 Implement lazy loading for relationship tabs using `itemRelationshipApi.get(itemId)` and cache results.
- [ ] 4.3 Render designation/definition tables with language, value, source, context list, and acceptability badges.
- [ ] 4.4 Render pairing table with designation value, definition value, context label, and acceptability badge.
- [ ] 4.5 Add navigation links from relationship rows to related items/contexts.
- [ ] 4.6 Add E2E test covering tab navigation and relationship visibility.

## 5. Frontend context reverse browsing

- [ ] 5.1 Add a "查看引用" action in `ContextList.vue` rows.
- [ ] 5.2 Implement `ContextService.findUsages(contextId)` and expose `/api/v1/contexts/{id}/usages` as a bounded endpoint (default limit 100, truncation indicated).
- [ ] 5.3 Create `ContextUsageDialog.vue` showing paginated usages grouped by item type.
- [ ] 5.4 Add `contextApi.usages` method in `resources.ts`.
- [ ] 5.5 Add links from each usage row to the corresponding metadata detail page.
- [ ] 5.6 Add component test for the usage dialog.

## 6. Frontend acceptability visualization and search

- [ ] 6.1 Add acceptability badge column in `MetadataDetailView` relationship tables using agreed color mapping.
- [ ] 6.2 Show a `PREFERRED` badge in `BrowserView` result rows when applicable.
- [ ] 6.3 Add advanced filters in `BrowserView` for context (single select) and acceptability (multi select).
- [ ] 6.4 Update `metadataSearchApi` query building to include `contextId` and `acceptability`.
- [ ] 6.5 Extend backend `/metadata/search` to support `contextId` and `acceptability` filters, and keep the non-blocking UI fallback tip for robustness.
- [ ] 6.6 Add component test for filter rendering and badge display.

## 7. Frontend pairing editor improvements

- [ ] 7.1 Improve dropdown labels in `DesignationDefinitionPairingEditor.vue` to show language badge and value.
- [ ] 7.2 Add duplicate detection for same designationId + definitionId + contextId and highlight conflicting rows.
- [ ] 7.3 (Optional) Implement temporary client IDs for designations/definitions so pairings can be created before the parent item is saved.
- [ ] 7.4 Add component tests for duplicate validation and label rendering.

## 8. Frontend namespace governance

- [ ] 8.1 Add multi-select naming convention selector in `NamespaceList.vue` form.
- [ ] 8.2 Add indicator switches for `mandatoryNamingConventionIndicator`, `oneNamePerItemIndicator`, and `oneItemPerNameIndicator`.
- [ ] 8.3 Wire switches to `namespaceApi.create/update` payload fields.
- [ ] 8.4 Display backend constraint violation messages in the form.
- [ ] 8.5 Add component test for namespace governance form fields.

## 9. Documentation and verification

- [ ] 9.1 Update `ISO_11179-3_2023_Comparison_Report.md` chapter 8 section to mark Namespace constraints, NamingConvention attributes, and frontend relationship coverage as aligned.
- [ ] 9.2 Add or update UI i18n strings for new labels (Reference Document, Context Usages, Pairings, etc.).
- [ ] 9.3 Install `metadata-registry-ui` dependencies and run `npm run build` to verify no TypeScript/Vue errors.
- [ ] 9.4 Run `mvn test` for the full backend and fix any regressions.
- [ ] 9.5 Run `openspec validate --all` and ensure all changes pass.
