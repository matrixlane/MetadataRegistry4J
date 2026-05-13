package io.github.matrixlane.metadataregistry.web.dto.fhir;

import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingEquivalence;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for creating or updating a local AdministeredItem ↔ FHIR Element mapping.
 *
 * <p>Per WI-012-2 — supports one-to-one and one-to-many mapping types.
 */
@Schema(description = "本地元数据项与 FHIR Element 映射请求")
public record FhirElementMappingRequest(

        @Schema(description = "本地 AdministeredItem ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        UUID localItemId,

        @Schema(description = "FHIR Element 路径，如 Patient.name.given", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String fhirPath,

        @Schema(description = "FHIR Resource 类型，如 Patient", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String fhirResourceType,

        @Schema(description = "FHIR equivalence 代码", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        FhirMappingEquivalence fhirEquivalence,

        @Schema(description = "映射类型：ONE_TO_ONE 或 ONE_TO_MANY", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        FhirMappingType mappingType,

        @Schema(description = "映射变量名（一对多映射时必填，如 family / given）")
        String mappingVariable,

        @Schema(description = "映射描述")
        String description,

        @Schema(description = "所属映射方案 ID")
        UUID mappingSchemeId
) {
}
