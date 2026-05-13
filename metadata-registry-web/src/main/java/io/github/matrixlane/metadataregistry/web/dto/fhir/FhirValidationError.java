package io.github.matrixlane.metadataregistry.web.dto.fhir;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * FHIR 验证错误详情 DTO。
 *
 * <p>当导出的 ConceptMap JSON 未通过 HAPI FHIR 合规验证时，
 * 以字段级粒度报告错误。
 */
@Schema(description = "FHIR 字段级验证错误")
public record FhirValidationError(

        @Schema(description = "错误字段路径（如 ConceptMap.element[0].target[0].equivalence）")
        String fieldPath,

        @Schema(description = "错误严重级别（error / warning / information）")
        String severity,

        @Schema(description = "错误代码（如 VALUE_INVALID）")
        String errorCode,

        @Schema(description = "错误描述")
        String description,

        @Schema(description = "错误所在行号（JSON 层级）")
        int lineNumber
) {

    /**
     * 创建验证错误。
     *
     * @param fieldPath   错误字段路径
     * @param severity    严重级别
     * @param description 错误描述
     */
    public FhirValidationError {
        // Normalize empty strings to null
        if (errorCode == null || errorCode.isBlank()) {
            errorCode = "VALIDATION_ERROR";
        }
    }

    public static FhirValidationError of(String fieldPath, String severity, String description) {
        return new FhirValidationError(fieldPath, severity, "VALIDATION_ERROR", description, 0);
    }

    public static FhirValidationError of(String fieldPath, String severity,
                                          String errorCode, String description) {
        return new FhirValidationError(fieldPath, severity, errorCode, description, 0);
    }
}
