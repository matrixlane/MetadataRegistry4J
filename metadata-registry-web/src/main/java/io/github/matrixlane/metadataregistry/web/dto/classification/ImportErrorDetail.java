package io.github.matrixlane.metadataregistry.web.dto.classification;

/**
 * 导入错误详情，包含行号、列名、错误类型、错误消息和修正建议。
 */
public record ImportErrorDetail(
        int rowNumber,
        String columnName,
        String errorType,
        String errorMessage,
        String suggestion
) {
}
