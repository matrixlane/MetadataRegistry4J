package io.github.matrixlane.metadataregistry.web.dto.mapping;

import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactAnalysisResult;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 映射网络/影响面结果 → 响应 DTO 的共享转换（add-mcp-server-support 评审 MAJOR-B3）。
 *
 * <p>此前 {@code MappingNetworkController} 与 MCP {@code GetMappingNetworkTool}、
 * {@code MappingImpactController} 与 {@code GetMappingImpactTool} 各自维护几乎
 * 逐行相同的转换逻辑；本类收敛为唯一事实源，REST 与 MCP 两个入口复用，
 * 任一方调整不再产生漂移。
 */
public final class MappingDtoConverters {

    private MappingDtoConverters() {
    }

    public static MappingNetworkResponse toNetworkResponse(ItemMappingService.NetworkGraphResult result) {
        var nodes = result.nodes().stream()
                .map(n -> new MappingNetworkResponse.NetworkNode(n.itemId(), n.depth(), n.isCenter()))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        var edges = result.edges().stream()
                .map(e -> new MappingNetworkResponse.NetworkEdge(
                        e.mappingId(), e.sourceId(), e.targetId(), e.degree(), e.description()))
                .toList();
        return new MappingNetworkResponse(nodes, edges, result.truncated(), result.totalNodeCount());
    }

    public static ImpactAnalysisResponse toImpactResponse(ImpactAnalysisResult result) {
        return new ImpactAnalysisResponse(
                result.changedItemId(),
                result.changeType(),
                result.impactedItems().stream()
                        .map(i -> new ImpactAnalysisResponse.ImpactedItemDto(
                                i.itemId(),
                                i.impactLevel(),
                                i.impactDirection(),
                                i.mappingId(),
                                i.degree(),
                                i.suggestedAction()))
                        .toList(),
                result.totalImpacted(),
                result.truncated());
    }

    /** 空网络（未给 centerId 等业务面语义）。 */
    public static MappingNetworkResponse emptyNetwork() {
        return new MappingNetworkResponse(
                java.util.Collections.emptySet(), List.of(), false, 0);
    }
}
