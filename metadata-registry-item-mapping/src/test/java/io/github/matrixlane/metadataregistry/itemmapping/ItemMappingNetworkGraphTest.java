package io.github.matrixlane.metadataregistry.itemmapping;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService.NetworkGraphResult;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService.NetworkNode;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService.NetworkEdge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the network graph query logic in ItemMappingService.
 *
 * <p>Covers: getNetworkGraph(), computeDepths(), inner records,
 * depth clamping, type filtering, node truncation at 500.
 */
@ExtendWith(MockitoExtension.class)
class ItemMappingNetworkGraphTest {

    @Mock
    private ItemMappingRepository repository;

    private ItemMappingService service;

    @BeforeEach
    void setUp() {
        service = new ItemMappingService(repository);
    }

    // ---- Helper: create a persisted-style mapping with a real UUID ----

    private ItemMapping createMapping(UUID subjectId, UUID objectId, MappingType type) {
        return createMappingWithDesc(subjectId, objectId, type, null);
    }

    private ItemMapping createMappingWithDesc(UUID subjectId, UUID objectId,
                                               MappingType type, String desc) {
        ItemMapping mapping = new ItemMapping(
                ItemMappingSubject.forItem(subjectId),
                ItemMappingObject.forItem(objectId),
                type, desc
        );
        // Must set a real ID because the network graph code uses
        // HashSet.add(mapping.getId()) to deduplicate mappings.
        // With null IDs, only one null goes into the set
        // (subsequent nulls appear as duplicates) — the very first
        // mapping processed per BFS round wins, silently skipping others.
        setEntityId(mapping, UUID.randomUUID());
        return mapping;
    }

    /**
     * Use reflection to set the auto-generated UUID id so that
     * our mock / in-memory test entities have distinct non-null IDs.
     */
    private void setEntityId(ItemMapping mapping, UUID id) {
        try {
            Field idField = ItemMapping.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(mapping, id);
        } catch (Exception e) {
            throw new RuntimeException("Cannot set mapping id via reflection", e);
        }
    }

    // ---- Test: single node, no edges ----

    @Test
    void getNetworkGraph_singleNode_shouldReturnOnlyCenter() {
        UUID centerId = UUID.randomUUID();
        when(repository.findByItemId(centerId)).thenReturn(List.of());

        NetworkGraphResult result = service.getNetworkGraph(centerId, 3, null);

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.nodes()).allMatch(n -> n.isCenter() && n.itemId().equals(centerId));
        assertThat(result.edges()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    // ---- Test: BFS traversal with 1 level depth ----

    @Test
    void getNetworkGraph_depth1_shouldReturnCenterAndDirectNeighbors() {
        UUID center = UUID.randomUUID();
        UUID neighborA = UUID.randomUUID();
        UUID neighborB = UUID.randomUUID();

        ItemMapping m1 = createMapping(center, neighborA, MappingType.EQUIVALENT);
        ItemMapping m2 = createMapping(center, neighborB, MappingType.DERIVED);

        when(repository.findByItemId(center)).thenReturn(List.of(m1, m2));

        // Query depth 1: only direct neighbors (center's mappings)
        NetworkGraphResult result = service.getNetworkGraph(center, 1, null);

        // Center + 2 neighbors = 3 nodes
        assertThat(result.nodes()).hasSize(3);
        assertThat(result.nodes().stream()
                .filter(NetworkNode::isCenter)
                .findFirst().orElseThrow()
                .itemId()).isEqualTo(center);
        // Check neighbor depths
        assertThat(result.nodes().stream()
                .filter(n -> !n.isCenter())
                .map(NetworkNode::depth))
                .allMatch(d -> d == 1);
        // 2 edges
        assertThat(result.edges()).hasSize(2);
        assertThat(result.truncated()).isFalse();
    }

    // ---- Test: BFS traversal with 3 level depth ----

    @Test
    void getNetworkGraph_depth3_shouldReturnMultiLevelNeighbors() {
        UUID center = UUID.randomUUID();
        UUID lvl1 = UUID.randomUUID();
        UUID lvl2 = UUID.randomUUID();
        UUID lvl3 = UUID.randomUUID();

        // center → lvl1 (EQUIVALENT)
        ItemMapping m1 = createMapping(center, lvl1, MappingType.EQUIVALENT);
        // lvl1 → lvl2 (DERIVED)
        ItemMapping m2 = createMapping(lvl1, lvl2, MappingType.DERIVED);
        // lvl2 → lvl3 (AGGREGATED)
        ItemMapping m3 = createMapping(lvl2, lvl3, MappingType.AGGREGATED);

        when(repository.findByItemId(center)).thenReturn(List.of(m1));
        when(repository.findByItemId(lvl1)).thenReturn(List.of(m2));
        when(repository.findByItemId(lvl2)).thenReturn(List.of(m3));
        // lvl3 is at max depth (3) — findByItemId is never called

        NetworkGraphResult result = service.getNetworkGraph(center, 3, null);

        // Center + 3 levels = 4 nodes
        assertThat(result.nodes()).hasSize(4);
        assertThat(result.edges()).hasSize(3);
        assertThat(result.truncated()).isFalse();

        // Verify depths
        for (var node : result.nodes()) {
            if (node.itemId().equals(center)) {
                assertThat(node.depth()).isZero();
            } else if (node.itemId().equals(lvl1)) {
                assertThat(node.depth()).isEqualTo(1);
            } else if (node.itemId().equals(lvl2)) {
                assertThat(node.depth()).isEqualTo(2);
            } else if (node.itemId().equals(lvl3)) {
                assertThat(node.depth()).isEqualTo(3);
            }
        }
    }

    // ---- Test: depth clamping (1-3) ----

    @Test
    void getNetworkGraph_depthClamped_toMax3() {
        UUID center = UUID.randomUUID();
        UUID lvl1 = UUID.randomUUID();
        UUID lvl2 = UUID.randomUUID();
        UUID lvl3 = UUID.randomUUID();
        UUID lvl4 = UUID.randomUUID();

        ItemMapping m1 = createMapping(center, lvl1, MappingType.EQUIVALENT);
        ItemMapping m2 = createMapping(lvl1, lvl2, MappingType.DERIVED);
        ItemMapping m3 = createMapping(lvl2, lvl3, MappingType.DERIVED);
        ItemMapping m4 = createMapping(lvl3, lvl4, MappingType.DERIVED);

        // Depth 10 should be clamped to 3
        when(repository.findByItemId(center)).thenReturn(List.of(m1));
        when(repository.findByItemId(lvl1)).thenReturn(List.of(m2));
        when(repository.findByItemId(lvl2)).thenReturn(List.of(m3));
        // lvl3 and lvl4 are at or past max depth — queries never triggered

        NetworkGraphResult result = service.getNetworkGraph(center, 10, null);

        // 4 levels loaded (center + 3), lvl4 should NOT be included
        assertThat(result.nodes()).hasSize(4);
        assertThat(result.nodes().stream()
                .map(NetworkNode::itemId))
                .doesNotContain(lvl4);
    }

    @Test
    void getNetworkGraph_depth0_clampedToMin1() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();

        ItemMapping m1 = createMapping(center, neighbor, MappingType.EQUIVALENT);

        when(repository.findByItemId(center)).thenReturn(List.of(m1));

        // Depth 0 should be clamped to 1, so we still get direct neighbors
        NetworkGraphResult result = service.getNetworkGraph(center, 0, null);

        assertThat(result.nodes()).hasSize(2);
        assertThat(result.edges()).hasSize(1);
    }

    // ---- Test: type filtering ----

    @Test
    void getNetworkGraph_filterByType_shouldOnlyReturnMatchingEdges() {
        UUID center = UUID.randomUUID();
        UUID neighborEq = UUID.randomUUID();
        UUID neighborDer = UUID.randomUUID();
        UUID neighborAgg = UUID.randomUUID();

        ItemMapping mEq = createMapping(center, neighborEq, MappingType.EQUIVALENT);
        ItemMapping mDer = createMapping(center, neighborDer, MappingType.DERIVED);
        ItemMapping mAgg = createMapping(center, neighborAgg, MappingType.AGGREGATED);

        when(repository.findByItemId(center)).thenReturn(List.of(mEq, mDer, mAgg));

        // Filter: only EQUIVALENT + DERIVED
        NetworkGraphResult result = service.getNetworkGraph(
                center, 1, List.of(MappingType.EQUIVALENT, MappingType.DERIVED));

        assertThat(result.edges()).hasSize(2);
        assertThat(result.edges().stream()
                .map(NetworkEdge::mappingType))
                .containsOnly(MappingType.EQUIVALENT, MappingType.DERIVED);

        // 3 nodes: center + 2 filtered neighbors
        assertThat(result.nodes()).hasSize(3);
    }

    @Test
    void getNetworkGraph_filterWithEmptyList_shouldReturnAll() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();

        ItemMapping m1 = createMapping(center, neighbor, MappingType.EQUIVALENT);

        when(repository.findByItemId(center)).thenReturn(List.of(m1));

        // Empty filter list = no filter applied (behaves like null)
        NetworkGraphResult result = service.getNetworkGraph(center, 1, List.of());

        assertThat(result.edges()).hasSize(1);
    }

    // ---- Test: truncation at 500 nodes ----

    @Test
    void getNetworkGraph_truncated_whenNodeCountExceeds500() {
        UUID center = UUID.randomUUID();

        // Star topology: center + 500 neighbors
        // visited starts with {center} (size 1)
        // 500th neighbor: visited.size() = 500 → truncated, skip
        ArrayList<ItemMapping> mappings = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            mappings.add(createMapping(center, UUID.randomUUID(), MappingType.EQUIVALENT));
        }

        when(repository.findByItemId(center)).thenReturn(mappings);

        NetworkGraphResult result = service.getNetworkGraph(center, 1, null);

        // 500th edge triggers truncation — visited.size() = 500 at that point
        assertThat(result.nodes()).hasSize(500);
        assertThat(result.truncated()).isTrue();
        assertThat(result.totalNodeCount()).isEqualTo(500);
    }

    @Test
    void getNetworkGraph_notTruncated_whenNodeCountUnder500() {
        UUID center = UUID.randomUUID();
        ArrayList<ItemMapping> mappings = new ArrayList<>();
        for (int i = 0; i < 499; i++) {
            mappings.add(createMapping(center, UUID.randomUUID(), MappingType.EQUIVALENT));
        }

        when(repository.findByItemId(center)).thenReturn(mappings);

        NetworkGraphResult result = service.getNetworkGraph(center, 1, null);

        // Center + 499 neighbors = 500 nodes, not truncated
        assertThat(result.nodes()).hasSize(500);
        assertThat(result.truncated()).isFalse();
    }

    // ---- Test: edge details (description) ----

    @Test
    void getNetworkGraph_edgeShouldContainDescription() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();

        ItemMapping m = createMappingWithDesc(center, neighbor,
                MappingType.DERIVED, "Derived for FHIR transformation");

        when(repository.findByItemId(center)).thenReturn(List.of(m));

        NetworkGraphResult result = service.getNetworkGraph(center, 1, null);

        assertThat(result.edges()).hasSize(1);
        NetworkEdge edge = result.edges().get(0);
        assertThat(edge.mappingType()).isEqualTo(MappingType.DERIVED);
        assertThat(edge.description()).isEqualTo("Derived for FHIR transformation");
        assertThat(edge.sourceId()).isEqualTo(center);
        assertThat(edge.targetId()).isEqualTo(neighbor);
    }

    // ---- Test: bidirectional edges (EQUIVALENT) ----

    @Test
    void getNetworkGraph_equivalentMapping_shouldIncludeBothDirections() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();

        // Center → neighbor (EQUIVALENT)
        ItemMapping m1 = createMapping(center, neighbor, MappingType.EQUIVALENT);
        // Neighbor → center (same pair, different mapping)
        ItemMapping m2 = createMapping(neighbor, center, MappingType.EQUIVALENT);

        when(repository.findByItemId(center)).thenReturn(List.of(m1, m2));

        NetworkGraphResult result = service.getNetworkGraph(center, 1, null);

        // Both mappings should be included as edges (different IDs)
        assertThat(result.edges()).hasSize(2);
        // 2 unique nodes (center + neighbor)
        assertThat(result.nodes()).hasSize(2);
    }

    // ---- Test: reverse-direction traversal ----

    @Test
    void getNetworkGraph_reverseMapping_shouldStillFindNeighbor() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();

        // Mapping is from neighbor → center
        ItemMapping m = createMapping(neighbor, center, MappingType.DERIVED);

        when(repository.findByItemId(center)).thenReturn(List.of(m));
        // neighbor is at depth 1 (== maxDepth), so findByItemId(neighbor) is never called

        NetworkGraphResult result = service.getNetworkGraph(center, 1, null);

        // Should still find the neighbor through the reverse mapping
        assertThat(result.nodes()).hasSize(2);
        assertThat(result.nodes().stream()
                .map(NetworkNode::itemId))
                .contains(neighbor);
        assertThat(result.edges()).hasSize(1);
        // Edge should preserve direction from the data model
        assertThat(result.edges().get(0).sourceId()).isEqualTo(neighbor);
        assertThat(result.edges().get(0).targetId()).isEqualTo(center);
    }

    // ---- Test: NetworkNode record ----

    @Test
    void networkNodeRecord_shouldStoreFields() {
        UUID id = UUID.randomUUID();
        NetworkNode node = new NetworkNode(id, 2, false);

        assertThat(node.itemId()).isEqualTo(id);
        assertThat(node.depth()).isEqualTo(2);
        assertThat(node.isCenter()).isFalse();
    }

    // ---- Test: NetworkEdge record ----

    @Test
    void networkEdgeRecord_shouldStoreFields() {
        UUID mappingId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        NetworkEdge edge = new NetworkEdge(
                mappingId, sourceId, targetId,
                MappingType.SUBDIVIDED, "Subdivision description"
        );

        assertThat(edge.mappingId()).isEqualTo(mappingId);
        assertThat(edge.sourceId()).isEqualTo(sourceId);
        assertThat(edge.targetId()).isEqualTo(targetId);
        assertThat(edge.mappingType()).isEqualTo(MappingType.SUBDIVIDED);
        assertThat(edge.description()).isEqualTo("Subdivision description");
    }

    // ---- Test: NetworkGraphResult record ----

    @Test
    void networkGraphResultRecord_shouldStoreFields() {
        UUID centerId = UUID.randomUUID();
        NetworkNode center = new NetworkNode(centerId, 0, true);
        NetworkNode neighbor = new NetworkNode(UUID.randomUUID(), 1, false);
        Set<NetworkNode> nodes = Set.of(center, neighbor);

        NetworkEdge edge = new NetworkEdge(
                UUID.randomUUID(), centerId, neighbor.itemId(),
                MappingType.EQUIVALENT, null
        );
        List<NetworkEdge> edges = List.of(edge);

        NetworkGraphResult result = new NetworkGraphResult(
                nodes, edges, true, 501
        );

        assertThat(result.nodes()).hasSize(2);
        assertThat(result.edges()).hasSize(1);
        assertThat(result.truncated()).isTrue();
        assertThat(result.totalNodeCount()).isEqualTo(501);
    }

    // ---- Test: skip external mappings ----

    @Test
    void getNetworkGraph_shouldSkipExternalMappings() {
        UUID center = UUID.randomUUID();
        String externalUri = "https://example.org/fhir/Patient/123";

        ItemMapping extMapping = new ItemMapping(
                ItemMappingSubject.forItem(center),
                ItemMappingObject.forExternalUri(externalUri),
                MappingType.DERIVED
        );
        setEntityId(extMapping, UUID.randomUUID());

        when(repository.findByItemId(center)).thenReturn(List.of(extMapping));

        NetworkGraphResult result = service.getNetworkGraph(center, 1, null);

        // External mappings produce no edges or neighbor nodes
        assertThat(result.nodes()).hasSize(1); // center only
        assertThat(result.edges()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }
}
