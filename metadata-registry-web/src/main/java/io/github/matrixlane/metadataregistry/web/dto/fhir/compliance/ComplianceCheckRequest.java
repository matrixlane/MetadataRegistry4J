package io.github.matrixlane.metadataregistry.web.dto.fhir.compliance;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComplianceCheckRequest {

    /**
     * Target FHIR Resource ID to check compliance against.
     */
    private UUID resourceId;

    /**
     * Optional mapping scheme ID to scope the local items.
     */
    private UUID schemeId;

    /**
     * If true, focus evaluation on must-support elements.
     */
    @Builder.Default
    private boolean mustSupportOnly = false;
}
