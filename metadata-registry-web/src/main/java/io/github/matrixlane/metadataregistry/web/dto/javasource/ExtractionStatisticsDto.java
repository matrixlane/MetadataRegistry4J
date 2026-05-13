package io.github.matrixlane.metadataregistry.web.dto.javasource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO containing extraction quality statistics.
 */
@Schema(description = "提取统计信息")
public record ExtractionStatisticsDto(

        @Schema(description = "处理的文件总数")
        int totalFilesProcessed,

        @Schema(description = "提取的类总数")
        int totalClasses,

        @Schema(description = "有注释的类数量")
        int commentedClasses,

        @Schema(description = "类注释覆盖率 (0.0-1.0)")
        double classCommentRate,

        @Schema(description = "提取的字段总数")
        int totalFields,

        @Schema(description = "有注释的字段数量")
        int commentedFields,

        @Schema(description = "字段注释覆盖率 (0.0-1.0)")
        double fieldCommentRate,

        @Schema(description = "提取的枚举类型数")
        int totalEnums,

        @Schema(description = "有注释的枚举类型数")
        int commentedEnums,

        @Schema(description = "枚举类型注释覆盖率")
        double enumCommentRate,

        @Schema(description = "提取的枚举值总数")
        int totalEnumValues,

        @Schema(description = "有注释的枚举值数量")
        int commentedEnumValues,

        @Schema(description = "枚举值注释覆盖率")
        double enumValueCommentRate,

        @Schema(description = "错误数量")
        int errorCount
) {
}
