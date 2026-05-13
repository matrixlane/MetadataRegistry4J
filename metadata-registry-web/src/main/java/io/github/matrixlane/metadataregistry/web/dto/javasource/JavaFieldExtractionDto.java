package io.github.matrixlane.metadataregistry.web.dto.javasource;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO representing extracted information from a Java field.
 */
@Schema(description = "提取的 Java 字段信息")
public record JavaFieldExtractionDto(

        @Schema(description = "字段名", example = "patientName")
        String fieldName,

        @Schema(description = "类型名", example = "String")
        String typeName,

        @Schema(description = "字段注释文本")
        String comment,

        @Schema(description = "@param 标签列表")
        List<String> paramTags,

        @Schema(description = "@see 标签列表")
        List<String> seeTags,

        @Schema(description = "是否已弃用")
        boolean deprecated,

        @Schema(description = "弃用原因")
        String deprecatedReason,

        @Schema(description = "修饰符列表")
        List<String> modifiers
) {
}
