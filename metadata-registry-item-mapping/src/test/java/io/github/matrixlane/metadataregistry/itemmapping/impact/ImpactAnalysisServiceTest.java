package io.github.matrixlane.metadataregistry.itemmapping.impact;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    // ---- Helper methods ----

    private ItemMapping createMapping(UUID id, UUID subjectId, UUID objectId, MappingType type) {
        ItemMappingSubject sub = ItemMappingSubject.forItem(subjectId);
        sub.setId(UUID.randomUUID());
        ItemMappingObject obj = ItemMappingObject.forItem(objectId);
        obj.setId(UUID.randomUUID());
        ItemMapping mapping = new ItemMapping(sub, obj, type);
        mapping.setId(id);
        return mapping;
    }

    // ---- analyzeItemChange tests ----

    @Test
    void analyzeItemChange_noMappings_shouldReturnEmpty() {
        UUID itemId = UUID.randomUUID();
        when(repository.findByItemId(itemId)).thenReturn(List.of());

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

        when(repository.findByItemId(center))
                .thenReturn(List.of(createMapping(mappingId, center, neighbor, MappingType.DERIVED)));
        when(repository.findByItemId(neighbor)).thenReturn(List.of());

        ImpactAnalysisResult result = service.analyzeItemChange(center, "UPDATED");

        assertThat(result.impactedItems()).hasSize(1);
        assertThat(result.impactedItems().get(0).itemId()).isEqualTo(neighbor);
        assertThat(result.impactedItems().get(0).impactLevel()).isEqualTo(ImpactLevel.DIRECT);
        assertThat(result.impactedItems().get(0).impactDirection()).isEqualTo(ImpactDirection.TARGET_AFFECTED);
        assertThat(result.totalImpacted()).isEqualTo(1);
    }

    @Test
    void analyzeItemChange_twoLevelCascade_shouldReturnLevel2() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.DERIVED);
        ItemMapping bc = createMapping(UUID.randomUUID(), b, c, MappingType.DERIVED);

        when(repository.findByItemId(a)).thenReturn(List.of(ab));
        when(repository.findByItemId(b)).thenReturn(List.of(ab, bc));
        when(repository.findByItemId(c)).thenReturn(List.of(bc));

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
    }

    @Test
    void analyzeItemChange_threeLevelCascade_shouldReturnLevel3() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();

        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.DERIVED);
        ItemMapping bc = createMapping(UUID.randomUUID(), b, c, MappingType.DERIVED);
        ItemMapping cd = createMapping(UUID.randomUUID(), c, d, MappingType.DERIVED);

        when(repository.findByItemId(a)).thenReturn(List.of(ab));
        when(repository.findByItemId(b)).thenReturn(List.of(ab, bc));
        when(repository.findByItemId(c)).thenReturn(List.of(bc, cd));
        // findByItemId(d) is not called because d is reached at depth 3 (MAX_DEPTH) and traversal stops

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

        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.DERIVED);
        ItemMapping bc = createMapping(UUID.randomUUID(), b, c, MappingType.DERIVED);
        ItemMapping cd = createMapping(UUID.randomUUID(), c, d, MappingType.DERIVED);
        ItemMapping de = createMapping(UUID.randomUUID(), d, e, MappingType.DERIVED);

        when(repository.findByItemId(a)).thenReturn(List.of(ab));
        when(repository.findByItemId(b)).thenReturn(List.of(ab, bc));
        when(repository.findByItemId(c)).thenReturn(List.of(bc, cd));
        // d is queued at depth 3 (MAX_DEPTH) so its mappings are never traversed, and e is never reached

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        // e should NOT appear (depth 4 exceeds max)
        boolean hasE = result.impactedItems().stream()
                .anyMatch(i -> i.itemId().equals(e));
        assertThat(hasE).isFalse();
    }

    @Test
    void analyzeItemChange_equivalentMapping_shouldReturnBothDirection() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.EQUIVALENT);

        when(repository.findByItemId(a)).thenReturn(List.of(ab));
        when(repository.findByItemId(b)).thenReturn(List.of(ab));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        assertThat(result.impactedItems()).hasSize(1);
        assertThat(result.impactedItems().get(0).impactDirection()).isEqualTo(ImpactDirection.BOTH);
    }

    @Test
    void analyzeItemChange_shouldNotCountChangedItemAsImpacted() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        // Circular: a→b, b→a
        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.EQUIVALENT);
        ItemMapping ba = createMapping(UUID.randomUUID(), b, a, MappingType.EQUIVALENT);

        when(repository.findByItemId(a)).thenReturn(List.of(ab, ba));
        when(repository.findByItemId(b)).thenReturn(List.of(ab, ba));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        // a should not appear as impacted by itself
        boolean selfImpacted = result.impactedItems().stream()
                .anyMatch(i -> i.itemId().equals(a));
        assertThat(selfImpacted).isFalse();
    }

    // ---- analyzeMappingChange tests ----

    @Test
    void analyzeMappingChange_mappingNotFound_shouldReturnEmpty() {
        UUID mappingId = UUID.randomUUID();
        when(repository.findById(mappingId)).thenReturn(Optional.empty());

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

        ItemMapping changed = createMapping(mappingId, a, b, MappingType.DERIVED);
        ItemMapping bc = createMapping(UUID.randomUUID(), b, c, MappingType.DERIVED);

        when(repository.findById(mappingId)).thenReturn(Optional.of(changed));
        when(repository.findByItemId(a)).thenReturn(List.of(changed));
        when(repository.findByItemId(b)).thenReturn(List.of(changed, bc));
        when(repository.findByItemId(c)).thenReturn(List.of(bc));

        ImpactAnalysisResult result = service.analyzeMappingChange(mappingId, "DELETED");

        // Should have b (level1 from a) and c (level2 from b), but not the changed mapping itself
        assertThat(result.impactedItems()).hasSize(2);
        assertThat(result.impactedItems().stream().map(ImpactedItem::itemId))
                .containsExactlyInAnyOrder(b, c);
    }

    @Test
    void analyzeMappingChange_shouldReturnSuggestions() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();

        when(repository.findByItemId(center))
                .thenReturn(List.of(createMapping(UUID.randomUUID(), center, neighbor, MappingType.EQUIVALENT)));
        when(repository.findByItemId(neighbor)).thenReturn(List.of());

        ImpactAnalysisResult result = service.analyzeItemChange(center, "DELETED");

        assertThat(result.impactedItems().get(0).suggestedAction())
                .contains("deleted")
                .contains("Verify equivalence");
    }

    @Test
    void analyzeItemChange_externalMapping_shouldBeSkipped() {
        UUID center = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        ItemMappingSubject sub = ItemMappingSubject.forItem(center);
        sub.setId(UUID.randomUUID());
        ItemMappingObject obj = ItemMappingObject.forExternalUri("https://example.org/ext");
        obj.setId(UUID.randomUUID());
        ItemMapping extMapping = new ItemMapping(sub, obj, MappingType.DERIVED);
        extMapping.setId(mappingId);

        when(repository.findByItemId(center)).thenReturn(List.of(extMapping));

        ImpactAnalysisResult result = service.analyzeItemChange(center, "UPDATED");

        assertThat(result.impactedItems()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void analyzeItemChange_truncatedAtMaxNodes_shouldSetTruncatedFlag() {
        UUID center = UUID.randomUUID();

        // Create 502 neighbors to exceed MAX_NODES (500)
        List<ItemMapping> directMappings = IntStream.range(0, 502)
                .mapToObj(i -> createMapping(UUID.randomUUID(), center, UUID.randomUUID(), MappingType.DERIVED))
                .toList();

        when(repository.findByItemId(center)).thenReturn(directMappings);

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

        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.DERIVED);
        ItemMapping ac = createMapping(UUID.randomUUID(), a, c, MappingType.DERIVED);

        when(repository.findByItemId(a)).thenReturn(List.of(ab, ac));
        when(repository.findByItemId(b)).thenReturn(List.of(ab));
        when(repository.findByItemId(c)).thenReturn(List.of(ac));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        assertThat(result.impactedItems()).hasSize(2);
        assertThat(result.impactedItems().stream().map(ImpactedItem::itemId))
                .containsExactlyInAnyOrder(b, c);
    }

    @Test
    void analyzeItemChange_aggregatedMapping_shouldIncludeCorrectSuggestion() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.AGGREGATED);

        when(repository.findByItemId(a)).thenReturn(List.of(ab));
        when(repository.findByItemId(b)).thenReturn(List.of(ab));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "DELETED");

        assertThat(result.impactedItems()).hasSize(1);
        assertThat(result.impactedItems().get(0).suggestedAction())
                .contains("aggregation integrity");
    }

    @Test
    void analyzeItemChange_subdividedMapping_shouldIncludeCorrectSuggestion() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.SUBDIVIDED);

        when(repository.findByItemId(a)).thenReturn(List.of(ab));
        when(repository.findByItemId(b)).thenReturn(List.of(ab));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "MODIFIED");

        assertThat(result.impactedItems()).hasSize(1);
        assertThat(result.impactedItems().get(0).suggestedAction())
                .contains("subdivision");
    }

    @Test
    void analyzeItemChange_maxDepthReached_shouldStopTraversal() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        UUID e = UUID.randomUUID();

        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.DERIVED);
        ItemMapping bc = createMapping(UUID.randomUUID(), b, c, MappingType.DERIVED);
        ItemMapping cd = createMapping(UUID.randomUUID(), c, d, MappingType.DERIVED);
        ItemMapping de = createMapping(UUID.randomUUID(), d, e, MappingType.DERIVED);

        when(repository.findByItemId(a)).thenReturn(List.of(ab));
        when(repository.findByItemId(b)).thenReturn(List.of(ab, bc));
        when(repository.findByItemId(c)).thenReturn(List.of(bc, cd));

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

        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.DERIVED);
        ItemMapping bc = createMapping(UUID.randomUUID(), b, c, MappingType.DERIVED);
        ItemMapping ca = createMapping(UUID.randomUUID(), c, a, MappingType.DERIVED);

        when(repository.findByItemId(a)).thenReturn(List.of(ab, ca));
        when(repository.findByItemId(b)).thenReturn(List.of(ab, bc));
        when(repository.findByItemId(c)).thenReturn(List.of(bc, ca));

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

        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.DERIVED);
        ItemMapping ac = createMapping(UUID.randomUUID(), a, c, MappingType.DERIVED);
        ItemMapping bc = createMapping(UUID.randomUUID(), b, c, MappingType.DERIVED);

        when(repository.findByItemId(a)).thenReturn(List.of(ab, ac));
        when(repository.findByItemId(b)).thenReturn(List.of(ab, bc));
        when(repository.findByItemId(c)).thenReturn(List.of(ac, bc));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        // c is reachable via a->c (DIRECT) and a->b->c (INDIRECT) - different paths produce different entries
        long cCount = result.impactedItems().stream()
                .filter(i -> i.itemId().equals(c))
                .count();
        assertThat(cCount).isEqualTo(2);
    }

    @Test
    void analyzeMappingChange_equivalentMapping_shouldExcludeCorrectly() {
        UUID mappingId = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        ItemMapping changed = createMapping(mappingId, a, b, MappingType.EQUIVALENT);
        ItemMapping bc = createMapping(UUID.randomUUID(), b, c, MappingType.DERIVED);

        when(repository.findById(mappingId)).thenReturn(Optional.of(changed));
        when(repository.findByItemId(a)).thenReturn(List.of(changed));
        when(repository.findByItemId(b)).thenReturn(List.of(changed, bc));
        when(repository.findByItemId(c)).thenReturn(List.of(bc));

        ImpactAnalysisResult result = service.analyzeMappingChange(mappingId, "MODIFIED");

        // b should be DIRECT with BOTH direction due to equivalent
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
    void analyzeMappingChange_externalMapping_shouldReturnEmpty() {
        UUID mappingId = UUID.randomUUID();
        ItemMappingSubject sub = ItemMappingSubject.forExternalUri("https://example.org/ext1");
        sub.setId(UUID.randomUUID());
        ItemMappingObject obj = ItemMappingObject.forExternalUri("https://example.org/ext2");
        obj.setId(UUID.randomUUID());
        ItemMapping extMapping = new ItemMapping(sub, obj, MappingType.DERIVED);
        extMapping.setId(mappingId);

        when(repository.findById(mappingId)).thenReturn(Optional.of(extMapping));

        ImpactAnalysisResult result = service.analyzeMappingChange(mappingId, "DELETED");

        assertThat(result.impactedItems()).isEmpty();
        assertThat(result.totalImpacted()).isZero();
    }

    @Test
    void analyzeItemChange_externalMappingInChain_shouldBeSkipped() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.DERIVED);
        ItemMappingSubject subExt = ItemMappingSubject.forItem(b);
        subExt.setId(UUID.randomUUID());
        ItemMappingObject objExt = ItemMappingObject.forExternalUri("https://example.org/ext");
        objExt.setId(UUID.randomUUID());
        ItemMapping ext = new ItemMapping(subExt, objExt, MappingType.DERIVED);
        ext.setId(UUID.randomUUID());
        ItemMapping bc = createMapping(UUID.randomUUID(), b, c, MappingType.DERIVED);

        when(repository.findByItemId(a)).thenReturn(List.of(ab));
        when(repository.findByItemId(b)).thenReturn(List.of(ab, bc, ext));
        when(repository.findByItemId(c)).thenReturn(List.of(bc));

        ImpactAnalysisResult result = service.analyzeItemChange(a, "UPDATED");

        // a->b->ext should be skipped; a->b->c should proceed
        assertThat(result.impactedItems().stream().map(ImpactedItem::itemId))
                .contains(b, c)
                .doesNotContainNull();
    }

    @Test
    void analyzeItemChange_unknownChangeType_shouldUseDefaultSuggestion() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();

        when(repository.findByItemId(center))
                .thenReturn(List.of(createMapping(UUID.randomUUID(), center, neighbor, MappingType.DERIVED)));
        when(repository.findByItemId(neighbor)).thenReturn(List.of());

        ImpactAnalysisResult result = service.analyzeItemChange(center, "UNKNOWN_ACTION");

        assertThat(result.impactedItems()).hasSize(1);
        assertThat(result.impactedItems().get(0).suggestedAction())
                .contains("Related mapping changed")
                .contains("Review derivation rule");
    }
}
