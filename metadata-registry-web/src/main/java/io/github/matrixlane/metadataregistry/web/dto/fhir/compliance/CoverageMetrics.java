package io.github.matrixlane.metadataregistry.web.dto.fhir.compliance;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CoverageMetrics {

    private int totalElements;
    private int coveredElements;
    private int uncoveredElements;
    private int localExtraFields;

    /**
     * Overall coverage percentage: covered / total * 100
     */
    private double coveragePercentage;

    /**
     * Coverage of required elements (min > 0).
     */
    private double requiredCoveragePercentage;

    /**
     * Number of required elements.
     */
    private int requiredElementCount;

    /**
     * Number of covered required elements.
     */
    private int coveredRequiredCount;
}
