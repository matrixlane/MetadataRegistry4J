package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactAnalysisResult;
import io.github.matrixlane.metadataregistry.itemmapping.impact.ImpactAnalysisService;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.mapping.ImpactAnalysisResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for mapping impact analysis (WI-011-3).
 *
 * <p>Provides endpoints to analyze the cascading impact of metadata item
 * or mapping relationship changes.
 */
@RestController
@RequestMapping("/api/v1/mappings/impact")
@RequiredArgsConstructor
@Tag(name = "11. 映射影响分析", description = "ISO/IEC 11179 Clause 11 — 映射变更影响分析（WI-011-3）")
public class MappingImpactController {

    private final ImpactAnalysisService impactAnalysisService;

    @GetMapping("/item")
    @Operation(summary = "分析元数据项变更的影响范围",
            description = "当某个元数据项发生变更时，查询所有直接关联和级联（最多3层）受影响的映射关系。")
    public ApiResponse<ImpactAnalysisResponse> analyzeItemImpact(
            @RequestParam UUID itemId,
            @RequestParam(defaultValue = "UPDATED") String changeType) {

        ImpactAnalysisResult result = impactAnalysisService.analyzeItemChange(itemId, changeType);
        return ApiResponse.ok(toResponse(result));
    }

    @GetMapping("/mapping")
    @Operation(summary = "分析映射关系变更的影响范围",
            description = "当某个映射关系被修改或删除时，查询其下游级联影响（最多3层深度）。")
    public ApiResponse<ImpactAnalysisResponse> analyzeMappingImpact(
            @RequestParam UUID mappingId,
            @RequestParam(defaultValue = "MODIFIED") String changeType) {

        ImpactAnalysisResult result = impactAnalysisService.analyzeMappingChange(mappingId, changeType);
        return ApiResponse.ok(toResponse(result));
    }

    private ImpactAnalysisResponse toResponse(ImpactAnalysisResult result) {
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
                                i.suggestedAction()
                        ))
                        .toList(),
                result.totalImpacted(),
                result.truncated()
        );
    }
}
