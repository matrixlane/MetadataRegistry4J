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
public class ProfileSuggestionRequest {

    /**
     * Target FHIR Resource ID to generate profile for.
     */
    private UUID resourceId;

    /**
     * Optional compliance report ID to base the profile on.
     */
    private UUID reportId;

    /**
     * If true, include Extension definitions for local extra fields.
     */
    @Builder.Default
    private boolean includeExtensions = true;

    /**
     * Base profile URL to derive from (defaults to the resource's own StructureDefinition URL).
     */
    private String baseProfileUrl;
}
