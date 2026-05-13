package io.github.matrixlane.metadataregistry.web.dto.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.matrixlane.metadataregistry.web.entity.fhir.FhirResource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FhirResourceResponse {
    private UUID id;
    private String fhirId;
    private String url;
    private String name;
    private String title;
    private String status;
    private String version;
    private String fhirVersion;
    private String kind;
    private Boolean abstractResource;
    private String type;
    private String description;
    private String publisher;
    private String date;
    private LocalDateTime importedAt;
    private UUID administeredItemId;
    private List<FhirElementResponse> elements;

    public static FhirResourceResponse from(FhirResource resource) {
        return FhirResourceResponse.builder()
                .id(resource.getId())
                .fhirId(resource.getFhirId())
                .url(resource.getUrl())
                .name(resource.getName())
                .title(resource.getTitle())
                .status(resource.getStatus())
                .version(resource.getVersion())
                .fhirVersion(resource.getFhirVersion())
                .kind(resource.getKind())
                .abstractResource(resource.getAbstractResource())
                .type(resource.getType())
                .description(resource.getDescription())
                .publisher(resource.getPublisher())
                .date(resource.getDate())
                .importedAt(resource.getImportedAt())
                .administeredItemId(resource.getAdministeredItemId())
                .build();
    }
}
