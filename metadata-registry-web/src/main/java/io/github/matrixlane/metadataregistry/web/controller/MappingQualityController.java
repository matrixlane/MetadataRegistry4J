package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.itemmapping.quality.MappingQualityReport;
import io.github.matrixlane.metadataregistry.itemmapping.quality.MappingQualityService;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.mapping.MappingQualityReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for mapping quality evaluation (WI-011-3).
 *
 * <p>Provides endpoints to generate quality reports for mapping schemes,
 * including coverage, consistency, completeness metrics and improvement tips.
 */
@RestController
@RequestMapping("/api/v1/mapping-schemes/{schemeId}/quality")
@RequiredArgsConstructor
@Tag(name = "11. 映射质量评估", description = "ISO/IEC 11179 Clause 11 — 映射质量评估与报告（WI-011-3）")
public class MappingQualityController {

    private final MappingQualityService qualityService;

    @GetMapping("/report")
    @Operation(summary = "生成映射方案质量报告",
            description = "生成包含覆盖率、一致性、完整度评分及改进建议的质量报告。")
    public ApiResponse<MappingQualityReportResponse> getQualityReport(
            @PathVariable UUID schemeId) {

        MappingQualityReport report = qualityService.generateReport(schemeId);
        return ApiResponse.ok(toResponse(report));
    }

    private MappingQualityReportResponse toResponse(MappingQualityReport report) {
        return new MappingQualityReportResponse(
                report.schemeId(),
                report.schemeName(),
                new MappingQualityReportResponse.QualityMetricsDto(
                        report.metrics().coverageScore(),
                        report.metrics().descriptionRate(),
                        report.metrics().transformationRate(),
                        report.metrics().consistencyScore(),
                        report.metrics().overallScore(),
                        report.metrics().totalMappings(),
                        report.metrics().mappedElements(),
                        report.metrics().targetElements(),
                        report.metrics().issuesFound()
                ),
                report.highRiskMappings(),
                report.issues().stream()
                        .map(i -> new MappingQualityReportResponse.QualityIssueDto(
                                i.mappingId(),
                                i.severity(),
                                i.issueType(),
                                i.description(),
                                i.mappingType()
                        ))
                        .toList(),
                report.improvementTips(),
                report.generatedAt()
        );
    }
}
