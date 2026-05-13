package io.github.matrixlane.metadataregistry.web.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FhirElementResponse {
    private UUID id;
    private UUID parentId;
    private String path;
    private String elementPath;
    private String elementType;
    private Integer min;
    private String max;
    private String shortDescription;
    private String definition;
    private String bindingValueSet;
    private String bindingStrength;
    private String bindingDescription;
    private boolean modifier;
    private boolean summary;
    private List<FhirElementResponse> children;
    private int depth;
}
