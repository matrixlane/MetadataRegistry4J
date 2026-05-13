package io.github.matrixlane.metadataregistry.web.dto.javasource;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response containing extracted JavaDoc comment information.
 */
@Schema(description = "Java 源码注释提取结果")
public record JavaSourceExtractionResponse(

        @Schema(description = "提取的类信息列表")
        List<JavaClassExtractionDto> classes,

        @Schema(description = "统计信息")
        ExtractionStatisticsDto statistics,

        @Schema(description = "错误信息列表")
        List<String> errors
) {
}
