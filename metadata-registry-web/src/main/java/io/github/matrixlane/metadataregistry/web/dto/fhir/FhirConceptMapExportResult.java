package io.github.matrixlane.metadataregistry.web.dto.fhir;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * FHIR ConceptMap 导出结果 DTO。
 *
 * <p>包含导出的 ConceptMap JSON、验证结果、统计信息。
 */
@Schema(description = "FHIR ConceptMap 导出结果")
public record FhirConceptMapExportResult(

        @Schema(description = "导出任务 ID（异步导出时存在）")
        UUID taskId,

        @Schema(description = "导出是否成功")
        boolean success,

        @Schema(description = "导出的 FHIR ConceptMap JSON 内容")
        String conceptMapJson,

        @Schema(description = "FHIR R4 合规验证是否通过")
        boolean validationPassed,

        @Schema(description = "验证错误列表（验证失败时）")
        List<FhirValidationError> validationErrors,

        @Schema(description = "包含的映射条目数")
        int mappingCount,

        @Schema(description = "包含的元素（element）数")
        int elementCount,

        @Schema(description = "导出时间")
        OffsetDateTime exportedAt,

        @Schema(description = "错误消息（导出失败时）")
        String errorMessage,

        @Schema(description = "是否异步导出")
        boolean async
) {

    /**
     * 创建同步导出成功结果。
     */
    public static FhirConceptMapExportResult syncSuccess(
            String conceptMapJson, boolean validationPassed,
            List<FhirValidationError> validationErrors,
            int mappingCount, int elementCount) {
        return new FhirConceptMapExportResult(
                null, true, conceptMapJson, validationPassed,
                validationErrors, mappingCount, elementCount,
                OffsetDateTime.now(), null, false
        );
    }

    /**
     * 创建异步导出任务已提交的结果。
     */
    public static FhirConceptMapExportResult asyncSubmitted(UUID taskId) {
        return new FhirConceptMapExportResult(
                taskId, true, null, false,
                List.of(), 0, 0,
                OffsetDateTime.now(), null, true
        );
    }

    /**
     * 创建导出失败结果。
     */
    public static FhirConceptMapExportResult failure(String errorMessage) {
        return new FhirConceptMapExportResult(
                null, false, null, false,
                List.of(), 0, 0,
                OffsetDateTime.now(), errorMessage, false
        );
    }
}
