package io.github.matrixlane.metadataregistry.web.dto.fhir;

import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingEquivalence;
import io.github.matrixlane.metadataregistry.itemmapping.enums.FhirMappingType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for a local AdministeredItem ↔ FHIR Element mapping.
 *
 * <p>Per WI-012-2 — returns full mapping details including conflict warnings.
 */
@Schema(description = "本地元数据项与 FHIR Element 映射响应")
public record FhirElementMappingResponse(

        @Schema(description = "映射 ID")
        UUID id,

        @Schema(description = "本地 AdministeredItem ID")
        UUID localItemId,

        @Schema(description = "FHIR Element 路径")
        String fhirPath,

        @Schema(description = "FHIR Resource 类型")
        String fhirResourceType,

        @Schema(description = "FHIR equivalence 代码")
        FhirMappingEquivalence fhirEquivalence,

        @Schema(description = "映射类型")
        FhirMappingType mappingType,

        @Schema(description = "映射变量名")
        String mappingVariable,

        @Schema(description = "映射描述")
        String description,

        @Schema(description = "所属映射方案 ID")
        UUID mappingSchemeId,

        @Schema(description = "创建人 ID")
        UUID createdBy,

        @Schema(description = "更新人 ID")
        UUID updatedBy,

        @Schema(description = "创建时间")
        LocalDateTime createdAt,

        @Schema(description = "更新时间")
        LocalDateTime updatedAt,

        @Schema(description = "乐观锁版本号")
        Long version
) {
}
