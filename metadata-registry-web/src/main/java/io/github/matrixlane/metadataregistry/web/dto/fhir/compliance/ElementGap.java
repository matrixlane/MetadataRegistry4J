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
public class ElementGap {

    private UUID fhirElementId;
    private String fhirPath;
    private String elementType;
    private String shortDescription;
    private Integer min;
    private String max;

    /**
     * True if this element is required (min > 0).
     */
    private boolean required;

    /**
     * True if this element is marked as summary (proxy for must-support until full parser).
     */
    private boolean summary;

    /**
     * Suggested priority for addressing this gap.
     */
    private String priority;
}
