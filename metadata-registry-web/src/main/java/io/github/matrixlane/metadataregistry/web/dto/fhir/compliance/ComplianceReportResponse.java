package io.github.matrixlane.metadataregistry.web.dto.fhir.compliance;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComplianceReportResponse {

    private UUID reportId;
    private UUID resourceId;
    private String resourceName;
    private String resourceUrl;
    private LocalDateTime generatedAt;

    /**
     * Explicit status marker: this report is a draft and requires human review.
     */
    @Builder.Default
    private String status = "DRAFT";

    @Builder.Default
    private String disclaimer = "本报告为自动生成的草稿，需经 FHIR 实施专家人工审核后方可使用。";

    private CoverageMetrics coverageMetrics;
    private TypeCompatibilitySummary typeCompatibilitySummary;

    private List<ElementCoverage> coveredElements;
    private List<ElementGap> uncoveredElements;
    private List<LocalExtraField> localExtraFields;

    /**
     * High-level warnings or recommendations.
     */
    private List<String> warnings;
}
