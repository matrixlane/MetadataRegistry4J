## 1. Backend API Preparation

- [x] 1.1 Add `/api/v1/items/{itemId}/designation-definition-relationships` endpoint that returns designations, definitions, pairings, and their contexts in a single denormalized response.
- [x] 1.2 Add `ContextService.findUsages(UUID contextId)` method with repository queries for `DesignationContext`, `DefinitionContext`, and `DesignationDefinitionPairing`.
- [x] 1.3 Expose a `/api/v1/contexts/{id}/usages` paginated endpoint returning the usage view.
- [x] 1.4 Extend `/metadata/search` to support optional `contextId` and `acceptability` query parameters (backend can return unfiltered with a warning if not yet implemented).
- [x] 1.5 Add integration tests for the new endpoints.

## 2. Reference Source in Multilingual Editor

- [x] 2.1 Add `sourceId` UI field to `MultilingualTextEditor.vue` (one `ReferenceDocument` selector per designation/definition line).
- [x] 2.2 Load `referenceDocumentApi` options on mount.
- [x] 2.3 Include `sourceId` in the emitted `TextLineForm` and ensure `ItemFormDialog` serializes it to the backend payload.
- [ ] 2.4 Add a component test verifying that selecting a source emits the correct model update.

## 3. Metadata Detail Relationship Tabs

- [x] 3.1 Extend `MetadataDetailView.vue` with tabs: Overview, Designations, Definitions, Contexts, Pairings, Version History.
- [x] 3.2 Implement lazy loading so relationship tabs fetch data only when activated.
- [x] 3.3 Add `relationshipsApi` in `resources.ts` calling the new endpoint.
- [x] 3.4 Render designation/definition tables with language, value, source, context list, and acceptability badges.
- [x] 3.5 Render pairing table with designation value, definition value, context label, and acceptability badge.
- [ ] 3.6 Add E2E test covering tab navigation and relationship visibility.

## 4. Context Reverse Browsing

- [x] 4.1 Add a "View Usages" action in `ContextList.vue` rows.
- [x] 4.2 Create a `ContextUsageDialog.vue` component showing paginated usages grouped by item type.
- [x] 4.3 Add `contextApi.usages` method in `resources.ts`.
- [x] 4.4 Add a link from each usage row to the corresponding metadata detail page.
- [x] 4.5 Add component test for the usage dialog.

## 5. Pairing Editor Improvements

- [x] 5.1 Improve dropdown labels in `DesignationDefinitionPairingEditor.vue` to show language badge + value.
- [x] 5.2 Add duplicate detection (same designationId + definitionId + contextId) and highlight conflicting rows.
- [ ] 5.3 (Optional) Implement temporary client IDs for designations/definitions so pairings can be created before the parent item is saved.
- [x] 5.4 Add component tests for duplicate validation and label rendering.

## 6. Acceptability Visualization and Search

- [x] 6.1 Add a PREFERRED badge next to the primary label in `BrowserView.vue` result rows when applicable.
- [x] 6.2 Add acceptability badges in detail relationship tables.
- [x] 6.3 Add advanced filters in `BrowserView.vue` for context and acceptability (UI ready; backend support via Task 1.4).
- [x] 6.4 Show a warning/info tip when backend does not yet support context/acceptability filtering.
- [ ] 6.5 Add component test for filter rendering and badge display.

## 7. Documentation and Regression

- [x] 7.1 Update `ISO_11179-3_2023_Comparison_Report.md` chapter 8 section to reflect that the frontend now exposes all Figure 6 relationships.
- [x] 7.2 Add or update UI i18n strings for new labels (Reference Document, Context Usages, Pairings, etc.).
- [ ] 7.3 Run the full UI dev server and verify no console errors on the data dictionary, detail, and context pages.
- [ ] 7.4 Run `mvn test` and Playwright E2E tests for affected flows to ensure no regressions.
- [x] 7.5 Validate both OpenSpec changes (`iso-11179-3-ch8-naming-definition-gap-closure` and `iso-11179-3-ch8-frontend-interaction-alignment`) with `openspec validate`.
