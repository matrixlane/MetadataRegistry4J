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
public class LocalExtraField {

    private UUID localItemId;
    private String localItemName;
    private String localItemType;

    /**
     * Explanation of why this field is considered "extra".
     */
    private String reason;

    /**
     * Suggested FHIR Extension URL if this field were to be mapped.
     */
    private String suggestedExtensionUrl;
}
