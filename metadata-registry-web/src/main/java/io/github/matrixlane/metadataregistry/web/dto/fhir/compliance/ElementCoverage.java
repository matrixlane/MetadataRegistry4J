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
public class ElementCoverage {

    private UUID fhirElementId;
    private String fhirPath;
    private String elementType;
    private String shortDescription;

    private UUID localItemId;
    private String localItemName;
    private String localItemType;

    /**
     * True if the local type is considered compatible with the FHIR type.
     */
    private Boolean typeCompatible;

    /**
     * Human-readable explanation of type compatibility assessment.
     */
    private String compatibilityNote;
}
