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
public class TypeIncompatibility {

    private UUID fhirElementId;
    private String fhirPath;
    private String fhirType;
    private UUID localItemId;
    private String localItemName;
    private String localType;
    private String reason;
}
