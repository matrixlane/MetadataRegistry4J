package io.github.matrixlane.metadataregistry.web.dto.javasource;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to extract JavaDoc comments from uploaded Java source content.
 */
@Schema(description = "Java 源码注释提取请求")
public record JavaSourceExtractionRequest(

        @Schema(description = "提取模式: FILE / DIRECTORY / ZIP / TEXT", example = "TEXT")
        @NotBlank
        String mode,

        @Schema(description = "当 mode=TEXT 时，直接传入 Java 源码字符串")
        String sourceContent,

        @Schema(description = "当 mode=FILE/DIRECTORY/ZIP 时，传入文件路径或 ZIP 路径")
        String sourcePath
) {
}
