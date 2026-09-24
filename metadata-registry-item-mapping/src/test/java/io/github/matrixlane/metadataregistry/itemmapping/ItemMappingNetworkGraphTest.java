package io.github.matrixlane.metadataregistry.itemmapping;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService.NetworkEdge;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService.NetworkGraphResult;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService.NetworkNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the network graph query logic in ItemMappingService.
 *
 * <p>Covers: getNetworkGraph() with scalar projections (mapping ids / degree rows /
 * subject & object rows / findById for description), depth clamping, degree
 * filtering, node truncation at 500, cartesian edge expansion for
 * multi-subject × multi-object mappings.
 */
@ExtendWith(MockitoExtension.class)
class ItemMappingNetworkGraphTest {

    @Mock
    private ItemMappingRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ItemMappingService service;

    @BeforeEach
    void setUp() {
        service = new ItemMappingService(repository, null, eventPublisher);
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

    /** Stub the full projection chain for one mapping. */
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

    // ---- Test: single node, no edges ----

    @Test
    void getNetworkGraph_singleNode_shouldReturnOnlyCenter() {
        UUID centerId = UUID.randomUUID();
        doReturn(List.of()).when(repository).findMappingIdsByItemId(centerId);

        NetworkGraphResult result = service.getNetworkGraph(centerId, 3, null);

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.nodes()).allMatch(n -> n.isCenter() && n.itemId().equals(centerId));
        assertThat(result.edges()).isEmpty();
        assertThat(result.truncated()).isFalse();
        assertThat(result.totalNodeCount()).isEqualTo(1);
    }

    // ---- Test: BFS traversal with 1 level depth ----

    @Test
    void getNetworkGraph_depth1_shouldReturnCenterAndDirectNeighbors() {
        UUID center = UUID.randomUUID();
        UUID neighborA = UUID.randomUUID();
        UUID neighborB = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();

        doReturn(List.of(m1, m2)).when(repository).findMappingIdsByItemId(center);
        stubMapping(m1, MappingDegree.SAME_AS, List.of(center), List.of(neighborA));
        stubMapping(m2, MappingDegree.DERIVED_FROM, List.of(center), List.of(neighborB));

        NetworkGraphResult result = service.getNetworkGraph(center, 1, null);

        // Center + 2 neighbors = 3 nodes
        assertThat(result.nodes()).hasSize(3);
        assertThat(result.nodes().stream()
                .filter(NetworkNode::isCenter)
                .findFirst().orElseThrow()
                .itemId()).isEqualTo(center);
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
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        UUID m3 = UUID.randomUUID();

        doReturn(List.of(m1)).when(repository).findMappingIdsByItemId(center);
        doReturn(List.of(m2)).when(repository).findMappingIdsByItemId(lvl1);
        doReturn(List.of(m3)).when(repository).findMappingIdsByItemId(lvl2);
        // lvl3 is at max depth (3) — its mapping ids are never queried

        stubMapping(m1, MappingDegree.SAME_AS, List.of(center), List.of(lvl1));
        stubMapping(m2, MappingDegree.DERIVED_FROM, List.of(lvl1), List.of(lvl2));
        stubMapping(m3, MappingDegree.CONTAINS, List.of(lvl2), List.of(lvl3));

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
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();
        UUID m3 = UUID.randomUUID();

        doReturn(List.of(m1)).when(repository).findMappingIdsByItemId(center);
        doReturn(List.of(m2)).when(repository).findMappingIdsByItemId(lvl1);
        doReturn(List.of(m3)).when(repository).findMappingIdsByItemId(lvl2);
        // lvl3 and lvl4 are at or past max depth — queries never triggered

        stubMapping(m1, MappingDegree.SAME_AS, List.of(center), List.of(lvl1));
        stubMapping(m2, MappingDegree.DERIVED_FROM, List.of(lvl1), List.of(lvl2));
        stubMapping(m3, MappingDegree.DERIVED_FROM, List.of(lvl2), List.of(lvl3));

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
        UUID m1 = UUID.randomUUID();

        doReturn(List.of(m1)).when(repository).findMappingIdsByItemId(center);
        stubMapping(m1, MappingDegree.SAME_AS, List.of(center), List.of(neighbor));

        // Depth 0 should be clamped to 1, so we still get direct neighbors
        NetworkGraphResult result = service.getNetworkGraph(center, 0, null);

        assertThat(result.nodes()).hasSize(2);
        assertThat(result.edges()).hasSize(1);
    }

    // ---- Test: degree filtering ----

    @Test
    void getNetworkGraph_filterByDegree_shouldOnlyReturnMatchingEdges() {
        UUID center = UUID.randomUUID();
        UUID neighborSame = UUID.randomUUID();
        UUID neighborDerived = UUID.randomUUID();
        UUID neighborContains = UUID.randomUUID();
        UUID mSame = UUID.randomUUID();
        UUID mDerived = UUID.randomUUID();
        UUID mContains = UUID.randomUUID();

        doReturn(List.of(mSame, mDerived, mContains))
                .when(repository).findMappingIdsByItemId(center);

        // Degree rows are looked up for every mapping before filtering
        when(repository.findDegreesByMappingIds(List.of(mSame)))
                .thenReturn(rows(degreeRow(mSame, MappingDegree.SAME_AS)));
        when(repository.findDegreesByMappingIds(List.of(mDerived)))
                .thenReturn(rows(degreeRow(mDerived, MappingDegree.DERIVED_FROM)));
        when(repository.findDegreesByMappingIds(List.of(mContains)))
                .thenReturn(rows(degreeRow(mContains, MappingDegree.CONTAINS)));

        // Subject/object rows only for the accepted mappings
        stubMapping(mSame, MappingDegree.SAME_AS, List.of(center), List.of(neighborSame));
        stubMapping(mDerived, MappingDegree.DERIVED_FROM, List.of(center), List.of(neighborDerived));

        // Filter: only SAME_AS + DERIVED_FROM
        NetworkGraphResult result = service.getNetworkGraph(
                center, 1, List.of(MappingDegree.SAME_AS, MappingDegree.DERIVED_FROM));

        assertThat(result.edges()).hasSize(2);
        assertThat(result.edges().stream()
                .map(NetworkEdge::degree))
                .containsOnly(MappingDegree.SAME_AS, MappingDegree.DERIVED_FROM);

        // 3 nodes: center + 2 filtered neighbors
        assertThat(result.nodes()).hasSize(3);
    }

    @Test
    void getNetworkGraph_filterWithEmptyList_shouldReturnAll() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();

        doReturn(List.of(m1)).when(repository).findMappingIdsByItemId(center);
        stubMapping(m1, MappingDegree.SAME_AS, List.of(center), List.of(neighbor));

        // Empty filter list = no filter applied (behaves like null)
        NetworkGraphResult result = service.getNetworkGraph(center, 1, List.of());

        assertThat(result.edges()).hasSize(1);
    }

    // ---- Test: truncation at 500 nodes ----

    @Test
    void getNetworkGraph_truncated_whenNodeCountExceeds500() {
        UUID center = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();

        // Star topology: one mapping with 500 object bindings
        // visited starts with {center} (size 1)
        // 500th neighbor: visited.size() = 500 → truncated, skip
        doReturn(List.of(m1)).when(repository).findMappingIdsByItemId(center);
        when(repository.findDegreesByMappingIds(List.of(m1)))
                .thenReturn(rows(degreeRow(m1, MappingDegree.SAME_AS)));
        when(repository.findSubjectRowsByMappingIds(List.of(m1)))
                .thenReturn(rows(bindingRow(m1, center)));
        List<Object[]> objectRows = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            objectRows.add(bindingRow(m1, UUID.randomUUID()));
        }
        when(repository.findObjectRowsByMappingIds(List.of(m1)))
                .thenReturn(objectRows);

        NetworkGraphResult result = service.getNetworkGraph(center, 1, null);

        assertThat(result.nodes()).hasSize(500);
        assertThat(result.truncated()).isTrue();
        assertThat(result.totalNodeCount()).isEqualTo(500);
    }

    @Test
    void getNetworkGraph_notTruncated_whenNodeCountUnder500() {
        UUID center = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();

        doReturn(List.of(m1)).when(repository).findMappingIdsByItemId(center);
        when(repository.findDegreesByMappingIds(List.of(m1)))
                .thenReturn(rows(degreeRow(m1, MappingDegree.SAME_AS)));
        when(repository.findSubjectRowsByMappingIds(List.of(m1)))
                .thenReturn(rows(bindingRow(m1, center)));
        List<Object[]> objectRows = new ArrayList<>();
        for (int i = 0; i < 499; i++) {
            objectRows.add(bindingRow(m1, UUID.randomUUID()));
        }
        when(repository.findObjectRowsByMappingIds(List.of(m1)))
                .thenReturn(objectRows);

        NetworkGraphResult result = service.getNetworkGraph(center, 1, null);

        // Center + 499 neighbors = 500 nodes, not truncated
        assertThat(result.nodes()).hasSize(500);
        assertThat(result.truncated()).isFalse();
    }

    // ---- Test: edge details (description via findById) ----

    @Test
    void getNetworkGraph_edgeShouldContainDescription() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(center);
        stubMapping(mappingId, MappingDegree.DERIVED_FROM, List.of(center), List.of(neighbor));

        ItemMapping mapping = new ItemMapping();
        mapping.setId(mappingId);
        mapping.setDegree(MappingDegree.DERIVED_FROM);
        mapping.setDescription("Derived for FHIR transformation");
        when(repository.findById(mappingId)).thenReturn(Optional.of(mapping));

        NetworkGraphResult result = service.getNetworkGraph(center, 1, null);

        assertThat(result.edges()).hasSize(1);
        NetworkEdge edge = result.edges().get(0);
        assertThat(edge.mappingId()).isEqualTo(mappingId);
        assertThat(edge.degree()).isEqualTo(MappingDegree.DERIVED_FROM);
        assertThat(edge.description()).isEqualTo("Derived for FHIR transformation");
        assertThat(edge.sourceId()).isEqualTo(center);
        assertThat(edge.targetId()).isEqualTo(neighbor);
    }

    @Test
    void getNetworkGraph_mappingWithoutDegreeRow_shouldBeSkipped() {
        UUID center = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();

        doReturn(List.of(m1)).when(repository).findMappingIdsByItemId(center);
        when(repository.findDegreesByMappingIds(List.of(m1)))
                .thenReturn(List.of());

        NetworkGraphResult result = service.getNetworkGraph(center, 1, null);

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.edges()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    // ---- Test: findById miss yields null description ----

    @Test
    void getNetworkGraph_mappingNotFound_shouldYieldNullDescription() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();

        doReturn(List.of(m1)).when(repository).findMappingIdsByItemId(center);
        stubMapping(m1, MappingDegree.RELATED_TO, List.of(center), List.of(neighbor));
        lenient().when(repository.findById(any(UUID.class))).thenReturn(Optional.empty());

        NetworkGraphResult result = service.getNetworkGraph(center, 1, null);

        assertThat(result.edges()).hasSize(1);
        assertThat(result.edges().get(0).description()).isNull();
    }

    // ---- Test: bidirectional edges (two mappings between same pair) ----

    @Test
    void getNetworkGraph_twoMappingsBetweenSamePair_shouldIncludeBothEdges() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();
        UUID m2 = UUID.randomUUID();

        doReturn(List.of(m1, m2)).when(repository).findMappingIdsByItemId(center);
        stubMapping(m1, MappingDegree.SAME_AS, List.of(center), List.of(neighbor));
        stubMapping(m2, MappingDegree.SAME_AS, List.of(neighbor), List.of(center));

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
        UUID mappingId = UUID.randomUUID();

        // Mapping is from neighbor → center
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(center);
        stubMapping(mappingId, MappingDegree.DERIVED_FROM, List.of(neighbor), List.of(center));
        // neighbor is at depth 1 (== maxDepth), so its mapping ids are never queried

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

    // ---- Test: same mapping deduplicated when reached from both ends ----

    @Test
    void getNetworkGraph_sameMappingFromBothEnds_shouldProduceSingleEdge() {
        UUID center = UUID.randomUUID();
        UUID neighbor = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(center);
        doReturn(List.of(mappingId)).when(repository).findMappingIdsByItemId(neighbor);
        stubMapping(mappingId, MappingDegree.SAME_AS, List.of(neighbor), List.of(center));

        // depth 2: center → neighbor, then neighbor's id list contains the same
        // mapping again — visitedMappings must deduplicate it
        NetworkGraphResult result = service.getNetworkGraph(center, 2, null);

        assertThat(result.edges()).hasSize(1);
        assertThat(result.nodes()).hasSize(2);
    }

    // ---- Test: multi-subject × multi-object cartesian expansion ----

    @Test
    void getNetworkGraph_multiSubjectMultiObject_shouldExpandToCartesianEdges() {
        UUID subject1 = UUID.randomUUID();
        UUID subject2 = UUID.randomUUID();
        UUID object1 = UUID.randomUUID();
        UUID object2 = UUID.randomUUID();
        UUID m1 = UUID.randomUUID();

        // Center is one of the subjects
        doReturn(List.of(m1)).when(repository).findMappingIdsByItemId(subject1);
        stubMapping(m1, MappingDegree.SEMANTICALLY_EQUIVALENT,
                List.of(subject1, subject2), List.of(object1, object2));

        NetworkGraphResult result = service.getNetworkGraph(subject1, 1, null);

        // 2 subjects × 2 objects = 4 edges
        assertThat(result.edges()).hasSize(4);
        assertThat(result.edges().stream().map(NetworkEdge::sourceId))
                .containsOnly(subject1, subject2);
        assertThat(result.edges().stream().map(NetworkEdge::targetId))
                .containsOnly(object1, object2);
        for (NetworkEdge edge : result.edges()) {
            assertThat(edge.degree()).isEqualTo(MappingDegree.SEMANTICALLY_EQUIVALENT);
        }

        // center + other subject + 2 objects = 4 nodes
        assertThat(result.nodes()).hasSize(4);
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
                MappingDegree.CONTAINED_IN, "Containment description"
        );

        assertThat(edge.mappingId()).isEqualTo(mappingId);
        assertThat(edge.sourceId()).isEqualTo(sourceId);
        assertThat(edge.targetId()).isEqualTo(targetId);
        assertThat(edge.degree()).isEqualTo(MappingDegree.CONTAINED_IN);
        assertThat(edge.description()).isEqualTo("Containment description");
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
                MappingDegree.SAME_AS, null
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
}
