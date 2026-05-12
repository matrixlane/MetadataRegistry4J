package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingNetworkResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for mapping network graph visualization (WI-011-2-1).
 *
 * <p>Provides endpoints to query the local mapping network around
 * a center administered item, supporting depth control and type
 * filtering for the frontend graph visualization.
 */
@RestController
@RequestMapping("/api/v1/mappings/network")
@RequiredArgsConstructor
@Tag(name = "11. 映射网络可视化", description = "IS/IEC 11179 Clause 11 — 映射网络图查询（WI-011-2-1）")
public class MappingNetworkController {

    private final ItemMappingService mappingService;

    @GetMapping
    @Operation(summary = "查询局部映射网络图",
            description = "以指定元数据项为中心，查询其 N 层内的映射关系网络。" +
                    "支持按映射类型过滤，节点上限 500 个。")
    public ApiResponse<MappingNetworkResponse> getNetwork(
            @RequestParam UUID centerId,
            @RequestParam(defaultValue = "3") int depth,
            @RequestParam(required = false) List<MappingType> filterTypes) {

        ItemMappingService.NetworkGraphResult result =
                mappingService.getNetworkGraph(centerId, depth, filterTypes);

        // Convert to response DTO
        Set<MappingNetworkResponse.NetworkNode> nodes = result.nodes().stream()
                .map(n -> new MappingNetworkResponse.NetworkNode(
                        n.itemId(), n.depth(), n.isCenter()))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        List<MappingNetworkResponse.NetworkEdge> edges = result.edges().stream()
                .map(e -> new MappingNetworkResponse.NetworkEdge(
                        e.mappingId(), e.sourceId(), e.targetId(),
                        e.mappingType(), e.description()))
                .toList();

        MappingNetworkResponse response = new MappingNetworkResponse(
                nodes, edges, result.truncated(), result.totalNodeCount());

        return ApiResponse.ok(response);
    }
}
