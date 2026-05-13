package io.github.matrixlane.metadataregistry.web.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FhirValueSetResponse {
    private UUID id;
    private String fhirId;
    private String url;
    private String name;
    private String title;
    private String status;
    private String version;
    private String fhirVersion;
    private String description;
    private String purpose;
    private boolean experimental;
    private String publisher;
    private LocalDateTime importedAt;
}
