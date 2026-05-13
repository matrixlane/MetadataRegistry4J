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
public class ExtensionSuggestion {

    private UUID localItemId;
    private String localItemName;
    private String localItemType;

    /**
     * Suggested Extension URL following FHIR conventions.
     */
    private String suggestedUrl;

    /**
     * Suggested FHIR type for the extension value.
     */
    private String suggestedFhirType;

    /**
     * Human-readable rationale for this extension suggestion.
     */
    private String reason;
}
