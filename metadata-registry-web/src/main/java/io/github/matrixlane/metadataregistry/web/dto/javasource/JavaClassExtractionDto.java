package io.github.matrixlane.metadataregistry.web.dto.javasource;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO representing extracted information from a Java class.
 */
@Schema(description = "提取的 Java 类信息")
public record JavaClassExtractionDto(

        @Schema(description = "包名", example = "com.example.model")
        String packageName,

        @Schema(description = "类名", example = "Patient")
        String className,

        @Schema(description = "全限定名", example = "com.example.model.Patient")
        String qualifiedName,

        @Schema(description = "类注释文本")
        String comment,

        @Schema(description = "是否为枚举")
        boolean isEnum,

        @Schema(description = "是否为接口")
        boolean isInterface,

        @Schema(description = "是否已弃用")
        boolean deprecated,

        @Schema(description = "弃用原因")
        String deprecatedReason,

        @Schema(description = "字段列表")
        List<JavaFieldExtractionDto> fields,

        @Schema(description = "枚举值列表（仅当 isEnum=true 时）")
        List<JavaEnumValueExtractionDto> enumValues,

        @Schema(description = "@see 标签列表")
        List<String> seeTags
) {
}
