package io.github.matrixlane.metadataregistry.itemmapping.impact;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpactAnalysisServiceTest {

    @Mock
    private ItemMappingRepository repository;

    private ImpactAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new ImpactAnalysisService(repository);
    }

    // ---- Helpers ----

    /** [mappingId, degree] row. */
    private Object[] degreeRow(UUID mappingId, MappingDegree degree) {
        return new Object[]{mappingId, degree};
    }

    /** [mappingId, itemId, mappingVariableName] binding row. */
    private Object[] bindingRow(UUID mappingId, UUID itemId) {
        return new Object[]{mappingId, itemId, null};
    }

    private List<Object[]> rows(Object[]... rows) {
        return java.util.Arrays.asList(rows);
    }

    /** Stub the full projection chain for one mapping (as loadContext does). */
    private void stubMapping(UUID mappingId, MappingDegree degree,
                             List<UUID> subjectIds, List<UUID> objectIds) {
        when(repository.findDegreesByMappingIds(List.of(mappingId)))
                .thenReturn(rows(degreeRow(mappingId, degree)));
        List<Object[]> subjectRows = new ArrayList<>();
        for (UUID id : subjectIds) {
            subjectRows.add(bindingRow(mappingId, id));
        }
        when(repository.findSubjectRowsByMappingIds(List.of(mappingId)))
                .thenReturn(subjectRows);
        List<Object[]> objectRows = new ArrayList<>();
        for (UUID id : objectIds) {
            objectRows.add(bindingRow(mappingId, id));
        }
        when(repository.findObjectRowsByMappingIds(List.of(mappingId)))
                .thenReturn(objectRows);
    }

    // ---- analyzeItemChange tests ----

    @Test
    void analyzeItemChange_noMappings_shouldReturnEmpty() {
        UUID itemId = UUID.randomUUID();
        doReturn(List.of()).when(repository).findMappingIdsByItemId(itemId);

        ImpactAnalysisResult result = service.analyzeItemChange(itemId, "UPDATED");

        assertThat(result.changedItemId()).isEqualTo(itemId);
        assertThat(result.changeType()).isEqualTo("UPDATED");
        assertThat(result.impactedItems()).isEmpty();
        assertThat(result.totalImpacted()).isZero();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void analyzeItemChange_directMapping_shouldReturnLevel1() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(center);
        doReturn(List.of()).when(repository).findMappingIdsByItemId(neighbor);
        stubMapping(mappingId, MappingDegree.DERIVED_FROM, List.of(center), List.of(neighbor));

        ImpactAnalysisResult result = service.analyzeItemChange(center, "UPDATED");

        assertThat(result.impactedItems()).hasSize(1);
        ImpactedItem impacted = result.impactedItems().get(0);
        assertThat(impacted.itemId()).isEqualTo(neighbor);
        assertThat(impacted.impactLevel()).isEqualTo(ImpactLevel.DIRECT);
        assertThat(impacted.impactDirection()).isEqualTo(ImpactDirection.TARGET_AFFECTED);
        assertThat(impacted.mappingId()).isEqualTo(mappingId);
        assertThat(impacted.degree()).isEqualTo(MappingDegree.DERIVED_FROM);
        assertThat(result.totalImpacted()).isEqualTo(1);
    }

    @Test
    void analyzeItemChange_reverseDirection_shouldReturnSourceAffected() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        // Mapping is neighbor → center: center is the object
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(center);
        doReturn(List.of()).when(repository).findMappingIdsByItemId(neighbor);
        stubMapping(mappingId, MappingDegree.DERIVED_FROM, List.of(neighbor), List.of(center));

        ImpactAnalysisResult result = service.analyzeItemChange(center, "UPDATED");

        assertThat(result.impactedItems()).hasSize(1);
        assertThat(result.impactedItems().get(0).itemId()).isEqualTo(neighbor);
        assertThat(result.impactedItems().get(0).impactDirection())
                .isEqualTo(ImpactDirection.SOURCE_AFFECTED);
    }

    @Test
    void analyzeItemChange_twoLevelCascade_shouldReturnLevel2() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID mAb = UUID.randomUUID();
        UUID mBc = UUID.randomUUID();

        doReturn(List.of(mAb)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mAb, mBc)).when(repository).findMappingIdsByItemId(b);
        doReturn(List.of(mBc)).when(repository).findMappingIdsByItemId(c);

        stubMapping(mAb, MappingDegree.DERIVED_FROM, List.of(a), List.of(b));
        stubMapping(mBc, MappingDegree.DERIVED_FROM, List.of(b), List.of(c));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "DELETED");

        // Should have: b (level1), c (level2)
        assertThat(result.impactedItems()).hasSize(2);

        List<ImpactedItem> level1 = result.impactedItems().stream()
                .filter(i -> i.impactLevel() == ImpactLevel.DIRECT)
                .toList();
        List<ImpactedItem> level2 = result.impactedItems().stream()
                .filter(i -> i.impactLevel() == ImpactLevel.INDIRECT)
                .toList();

        assertThat(level1).hasSize(1);
        assertThat(level1.get(0).itemId()).isEqualTo(b);

        assertThat(level2).hasSize(1);
        assertThat(level2.get(0).itemId()).isEqualTo(c);
        assertThat(level2.get(0).impactDirection()).isEqualTo(ImpactDirection.TARGET_AFFECTED);
        assertThat(level2.get(0).degree()).isEqualTo(MappingDegree.DERIVED_FROM);
    }

    @Test
    void analyzeItemChange_threeLevelCascade_shouldReturnLevel3() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        UUID mAb = UUID.randomUUID();
        UUID mBc = UUID.randomUUID();
        UUID mCd = UUID.randomUUID();

        doReturn(List.of(mAb)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mAb, mBc)).when(repository).findMappingIdsByItemId(b);
        doReturn(List.of(mBc, mCd)).when(repository).findMappingIdsByItemId(c);
        // findMappingIdsByItemId(d) is not called because d is reached at depth 3 (MAX_DEPTH)

        stubMapping(mAb, MappingDegree.DERIVED_FROM, List.of(a), List.of(b));
        stubMapping(mBc, MappingDegree.DERIVED_FROM, List.of(b), List.of(c));
        stubMapping(mCd, MappingDegree.DERIVED_FROM, List.of(c), List.of(d));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        List<ImpactedItem> level3 = result.impactedItems().stream()
                .filter(i -> i.impactLevel() == ImpactLevel.CASCADE)
                .toList();

        assertThat(level3).hasSize(1);
        assertThat(level3.get(0).itemId()).isEqualTo(d);
    }

    @Test
    void analyzeItemChange_shouldStopAtMaxDepth3() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        UUID e = UUID.randomUUID();
        UUID mAb = UUID.randomUUID();
        UUID mBc = UUID.randomUUID();
        UUID mCd = UUID.randomUUID();

        doReturn(List.of(mAb)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mAb, mBc)).when(repository).findMappingIdsByItemId(b);
        doReturn(List.of(mBc, mCd)).when(repository).findMappingIdsByItemId(c);
        // d is queued at depth 3 (MAX_DEPTH) so its mappings are never traversed, and e is never reached

        stubMapping(mAb, MappingDegree.DERIVED_FROM, List.of(a), List.of(b));
        stubMapping(mBc, MappingDegree.DERIVED_FROM, List.of(b), List.of(c));
        stubMapping(mCd, MappingDegree.DERIVED_FROM, List.of(c), List.of(d));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        // e should NOT appear (depth 4 exceeds max)
        boolean hasE = result.impactedItems().stream()
                .anyMatch(i -> i.itemId().equals(e));
        assertThat(hasE).isFalse();
    }

    @Test
    void analyzeItemChange_symmetricMapping_shouldReturnBothDirection() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(b);
        stubMapping(mappingId, MappingDegree.SAME_AS, List.of(a), List.of(b));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        assertThat(result.impactedItems()).hasSize(1);
        ImpactedItem impacted = result.impactedItems().get(0);
        assertThat(impacted.impactDirection()).isEqualTo(ImpactDirection.BOTH);
        assertThat(impacted.degree()).isEqualTo(MappingDegree.SAME_AS);
    }

    @Test
    void analyzeItemChange_semanticallyEquivalent_shouldReturnBothDirection() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(b);
        stubMapping(mappingId, MappingDegree.SEMANTICALLY_EQUIVALENT, List.of(a), List.of(b));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        assertThat(result.impactedItems()).hasSize(1);
        assertThat(result.impactedItems().get(0).impactDirection()).isEqualTo(ImpactDirection.BOTH);
    }

    @Test
    void analyzeItemChange_sameSideSubjects_shouldBeBothDirection() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        // Multi-subject mapping: a and b are both subjects, c is the object
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(b);
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(c);
        stubMapping(mappingId, MappingDegree.DERIVED_FROM, List.of(a, b), List.of(c));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        // b is a fellow subject (same side) → conservative BOTH
        ImpactedItem fellowSubject = result.impactedItems().stream()
                .filter(i -> i.itemId().equals(b))
                .findFirst().orElseThrow();
        assertThat(fellowSubject.impactDirection()).isEqualTo(ImpactDirection.BOTH);

        // c is the object → TARGET_AFFECTED
        ImpactedItem object = result.impactedItems().stream()
                .filter(i -> i.itemId().equals(c))
                .findFirst().orElseThrow();
        assertThat(object.impactDirection()).isEqualTo(ImpactDirection.TARGET_AFFECTED);

        assertThat(result.totalImpacted()).isEqualTo(2);
    }

    @Test
    void analyzeItemChange_shouldNotCountChangedItemAsImpacted() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID mAb = UUID.randomUUID();
        UUID mBa = UUID.randomUUID();

        // Circular: a→b, b→a (two distinct mappings between the same pair)
        doReturn(List.of(mAb, mBa)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mAb, mBa)).when(repository).findMappingIdsByItemId(b);
        stubMapping(mAb, MappingDegree.SAME_AS, List.of(a), List.of(b));
        stubMapping(mBa, MappingDegree.SAME_AS, List.of(b), List.of(a));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        // a should not appear as impacted by itself; b is impacted via both mappings
        boolean selfImpacted = result.impactedItems().stream()
                .anyMatch(i -> i.itemId().equals(a));
        assertThat(selfImpacted).isFalse();
        assertThat(result.totalImpacted()).isEqualTo(1);
        assertThat(result.impactedItems().stream().map(ImpactedItem::itemId))
                .containsOnly(b);
    }

    // ---- analyzeMappingChange tests ----

    @Test
    void analyzeMappingChange_mappingNotFound_shouldReturnEmpty() {
        UUID mappingId = UUID.randomUUID();
        when(repository.findDegreesByMappingIds(List.of(mappingId)))
                .thenReturn(List.of());

        ImpactAnalysisResult result = service.analyzeMappingChange(mappingId, "DELETED");

        assertThat(result.impactedItems()).isEmpty();
        assertThat(result.totalImpacted()).isZero();
    }

    @Test
    void analyzeMappingChange_shouldExcludeChangedMapping() {
        UUID mappingId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID mBc = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mappingId, mBc)).when(repository).findMappingIdsByItemId(b);
        doReturn(List.of(mBc)).when(repository).findMappingIdsByItemId(c);

        stubMapping(mappingId, MappingDegree.DERIVED_FROM, List.of(a), List.of(b));
        stubMapping(mBc, MappingDegree.DERIVED_FROM, List.of(b), List.of(c));

        ImpactAnalysisResult result = service.analyzeMappingChange(mappingId, "DELETED");

        // Should have b (DIRECT, the object end) and c (INDIRECT via b), but no duplicates
        assertThat(result.impactedItems()).hasSize(2);
        assertThat(result.impactedItems().stream().map(ImpactedItem::itemId))
                .containsExactlyInAnyOrder(b, c);

        ImpactedItem direct = result.impactedItems().stream()
                .filter(i -> i.itemId().equals(b)).findFirst().orElseThrow();
        assertThat(direct.impactLevel()).isEqualTo(ImpactLevel.DIRECT);
        assertThat(direct.impactDirection()).isEqualTo(ImpactDirection.TARGET_AFFECTED);
        assertThat(direct.degree()).isEqualTo(MappingDegree.DERIVED_FROM);
    }

    @Test
    void analyzeMappingChange_equivalentMapping_shouldBeBothDirection() {
        UUID mappingId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID mBc = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mappingId, mBc)).when(repository).findMappingIdsByItemId(b);
        doReturn(List.of(mBc)).when(repository).findMappingIdsByItemId(c);

        stubMapping(mappingId, MappingDegree.SAME_AS, List.of(a), List.of(b));
        stubMapping(mBc, MappingDegree.DERIVED_FROM, List.of(b), List.of(c));

        ImpactAnalysisResult result = service.analyzeMappingChange(mappingId, "MODIFIED");

        // b should be DIRECT with BOTH direction due to symmetric degree
        assertThat(result.impactedItems().stream()
                .filter(i -> i.itemId().equals(b))
                .findFirst())
                .isPresent()
                .hasValueSatisfying(i -> {
                    assertThat(i.impactLevel()).isEqualTo(ImpactLevel.DIRECT);
                    assertThat(i.impactDirection()).isEqualTo(ImpactDirection.BOTH);
                });
    }

    @Test
    void analyzeMappingChange_multiObject_shouldMarkAllObjectsDirect() {
        UUID mappingId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(b);
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(c);

        // One subject, two objects
        stubMapping(mappingId, MappingDegree.DERIVED_FROM, List.of(a), List.of(b, c));

        ImpactAnalysisResult result = service.analyzeMappingChange(mappingId, "DELETED");

        // Both object items are DIRECT impact items
        assertThat(result.impactedItems()).hasSize(2);
        for (ImpactedItem impacted : result.impactedItems()) {
            assertThat(impacted.impactLevel()).isEqualTo(ImpactLevel.DIRECT);
            assertThat(impacted.impactDirection()).isEqualTo(ImpactDirection.TARGET_AFFECTED);
        }
        assertThat(result.impactedItems().stream().map(ImpactedItem::itemId))
                .containsExactlyInAnyOrder(b, c);
    }

    // ---- suggestion content ----

    @Test
    void analyzeItemChange_deletedEquivalent_shouldMentionVerifyEquivalence() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(center);
        doReturn(List.of()).when(repository).findMappingIdsByItemId(neighbor);
        stubMapping(mappingId, MappingDegree.SAME_AS, List.of(center), List.of(neighbor));

        ImpactAnalysisResult result = service.analyzeItemChange(center, "DELETED");

        assertThat(result.impactedItems()).hasSize(1);
        assertThat(result.impactedItems().get(0).suggestedAction())
                .contains("deleted")
                .contains("Verify equivalence");
    }

    @Test
    void analyzeItemChange_updatedDerived_shouldMentionReviewDerivationRule() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(center);
        doReturn(List.of()).when(repository).findMappingIdsByItemId(neighbor);
        stubMapping(mappingId, MappingDegree.DERIVED_FROM, List.of(center), List.of(neighbor));

        ImpactAnalysisResult result = service.analyzeItemChange(center, "UPDATED");

        assertThat(result.impactedItems().get(0).suggestedAction())
                .contains("updated")
                .contains("Review derivation rule");
    }

    @Test
    void analyzeItemChange_unknownChangeType_shouldUseDefaultSuggestion() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(center);
        doReturn(List.of()).when(repository).findMappingIdsByItemId(neighbor);
        stubMapping(mappingId, MappingDegree.DERIVED_FROM, List.of(center), List.of(neighbor));

        ImpactAnalysisResult result = service.analyzeItemChange(center, "UNKNOWN_ACTION");

        assertThat(result.impactedItems()).hasSize(1);
        assertThat(result.impactedItems().get(0).suggestedAction())
                .contains("Related mapping changed")
                .contains("Review derivation rule");
    }

    @Test
    void analyzeItemChange_containmentDegree_shouldIncludeCorrectSuggestion() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(b);
        stubMapping(mappingId, MappingDegree.CONTAINS, List.of(a), List.of(b));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "DELETED");

        assertThat(result.impactedItems()).hasSize(1);
        assertThat(result.impactedItems().get(0).suggestedAction())
                .contains("containment");
    }

    @Test
    void analyzeItemChange_hierarchyDegree_shouldIncludeCorrectSuggestion() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(b);
        stubMapping(mappingId, MappingDegree.CHILD_OF, List.of(a), List.of(b));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "MODIFIED");

        assertThat(result.impactedItems()).hasSize(1);
        assertThat(result.impactedItems().get(0).suggestedAction())
                .contains("hierarchical");
    }

    @Test
    void analyzeItemChange_instanceDegree_shouldIncludeCorrectSuggestion() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(b);
        stubMapping(mappingId, MappingDegree.INSTANCE_OF, List.of(a), List.of(b));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        assertThat(result.impactedItems()).hasSize(1);
        assertThat(result.impactedItems().get(0).suggestedAction())
                .contains("instance");
    }

    // ---- traversal robustness ----

    @Test
    void analyzeItemChange_truncatedAtMaxNodes_shouldSetTruncatedFlag() {
        UUID center = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        // One mapping with 502 object bindings to exceed MAX_NODES (500)
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(center);
        when(repository.findDegreesByMappingIds(List.of(mappingId)))
                .thenReturn(rows(degreeRow(mappingId, MappingDegree.DERIVED_FROM)));
        when(repository.findSubjectRowsByMappingIds(List.of(mappingId)))
                .thenReturn(rows(bindingRow(mappingId, center)));
        List<Object[]> objectRows = IntStream.range(0, 502)
                .mapToObj(i -> bindingRow(mappingId, UUID.randomUUID()))
                .toList();
        when(repository.findObjectRowsByMappingIds(List.of(mappingId)))
                .thenReturn(objectRows);

        ImpactAnalysisResult result = service.analyzeItemChange(center, "UPDATED");

        assertThat(result.truncated()).isTrue();
        // All 502 direct neighbors are in impactedItems, but BFS traversal beyond them is truncated
        assertThat(result.totalImpacted()).isEqualTo(502);
    }

    @Test
    void analyzeItemChange_multipleNeighborsFromSameNode_shouldBeIncluded() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID mAb = UUID.randomUUID();
        UUID mAc = UUID.randomUUID();

        doReturn(List.of(mAb, mAc)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mAb)).when(repository).findMappingIdsByItemId(b);
        doReturn(List.of(mAc)).when(repository).findMappingIdsByItemId(c);

        stubMapping(mAb, MappingDegree.DERIVED_FROM, List.of(a), List.of(b));
        stubMapping(mAc, MappingDegree.DERIVED_FROM, List.of(a), List.of(c));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        assertThat(result.impactedItems()).hasSize(2);
        assertThat(result.impactedItems().stream().map(ImpactedItem::itemId))
                .containsExactlyInAnyOrder(b, c);
    }

    @Test
    void analyzeItemChange_maxDepthReached_shouldStopTraversal() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        UUID e = UUID.randomUUID();
        UUID mAb = UUID.randomUUID();
        UUID mBc = UUID.randomUUID();
        UUID mCd = UUID.randomUUID();

        doReturn(List.of(mAb)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mAb, mBc)).when(repository).findMappingIdsByItemId(b);
        doReturn(List.of(mBc, mCd)).when(repository).findMappingIdsByItemId(c);

        stubMapping(mAb, MappingDegree.DERIVED_FROM, List.of(a), List.of(b));
        stubMapping(mBc, MappingDegree.DERIVED_FROM, List.of(b), List.of(c));
        stubMapping(mCd, MappingDegree.DERIVED_FROM, List.of(c), List.of(d));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        // MAX_DEPTH=3: a(0) -> b(1) -> c(2) -> d(3, as neighbor) -> e not reached
        // a is the changed item and is not included in impactedItems
        assertThat(result.impactedItems().stream().map(ImpactedItem::itemId))
                .contains(b, c, d)
                .doesNotContain(a, e);
    }

    @Test
    void analyzeItemChange_circularReference_shouldNotLoop() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID mAb = UUID.randomUUID();
        UUID mBc = UUID.randomUUID();
        UUID mCa = UUID.randomUUID();

        doReturn(List.of(mAb, mCa)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mAb, mBc)).when(repository).findMappingIdsByItemId(b);
        doReturn(List.of(mBc, mCa)).when(repository).findMappingIdsByItemId(c);

        stubMapping(mAb, MappingDegree.DERIVED_FROM, List.of(a), List.of(b));
        stubMapping(mBc, MappingDegree.DERIVED_FROM, List.of(b), List.of(c));
        stubMapping(mCa, MappingDegree.DERIVED_FROM, List.of(c), List.of(a));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        // Should not infinite loop, should have b and c only (a is the changed item)
        assertThat(result.totalImpacted()).isEqualTo(2);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void analyzeItemChange_duplicateMappingViaDifferentPaths_shouldProduceMultipleEntries() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID mAb = UUID.randomUUID();
        UUID mAc = UUID.randomUUID();
        UUID mBc = UUID.randomUUID();

        doReturn(List.of(mAb, mAc)).when(repository).findMappingIdsByItemId(a);
        doReturn(List.of(mAb, mBc)).when(repository).findMappingIdsByItemId(b);
        doReturn(List.of(mAc, mBc)).when(repository).findMappingIdsByItemId(c);

        stubMapping(mAb, MappingDegree.DERIVED_FROM, List.of(a), List.of(b));
        stubMapping(mAc, MappingDegree.DERIVED_FROM, List.of(a), List.of(c));
        stubMapping(mBc, MappingDegree.DERIVED_FROM, List.of(b), List.of(c));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        // c is reachable via a->c (DIRECT) and a->b->c (INDIRECT) - different paths produce different entries
        long cCount = result.impactedItems().stream()
                .filter(i -> i.itemId().equals(c))
                .count();
        assertThat(cCount).isEqualTo(2);
    }
}
