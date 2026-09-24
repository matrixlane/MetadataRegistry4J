package io.github.matrixlane.metadataregistry.web.dto.mapping;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Response DTO for the mapping network graph query.
 *
 * <p>Contains nodes, edges, and truncation info for the frontend
 * network graph visualization.
 *
 * @param nodes      the set of graph nodes (administered items)
 * @param edges      the list of graph edges (mapping relationships)
 * @param truncated  whether the result was truncated (node limit exceeded)
 * @param totalCount total node count before truncation (if truncated)
 */
public record MappingNetworkResponse(
        Set<NetworkNode> nodes,
        List<NetworkEdge> edges,
        boolean truncated,
        int totalCount
) {

    /**
     * A node in the network graph.
     *
     * @param itemId   the UUID of the administered item
     * @param depth    the BFS depth from the center node
     * @param isCenter whether this is the center node
     */
    public record NetworkNode(
            UUID itemId,
            int depth,
            boolean isCenter
    ) {}

    /**
     * An edge in the network graph.
     *
     * @param mappingId the UUID of the mapping
     * @param sourceId  the UUID of the source node (subject)
     * @param targetId  the UUID of the target node (object)
     * @param degree    the mapping degree
     * @param description optional description
     */
    public record NetworkEdge(
            UUID mappingId,
            UUID sourceId,
            UUID targetId,
            MappingDegree degree,
            String description
    ) {}
}
