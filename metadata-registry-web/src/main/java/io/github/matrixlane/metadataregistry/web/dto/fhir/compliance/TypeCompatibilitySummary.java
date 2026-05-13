package io.github.matrixlane.metadataregistry.web.dto.fhir.compliance;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TypeCompatibilitySummary {

    private int totalChecked;
    private int compatibleCount;
    private int incompatibleCount;
    private int unknownCount;

    /**
     * Detailed list of type incompatibilities.
     */
    private List<TypeIncompatibility> incompatibleDetails;
}
