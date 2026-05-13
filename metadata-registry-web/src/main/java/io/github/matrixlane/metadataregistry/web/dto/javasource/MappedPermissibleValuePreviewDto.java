package io.github.matrixlane.metadataregistry.web.dto.javasource;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Preview of a PermissibleValue mapped from an enum constant.
 */
@Schema(description = "允许值映射预览")
public record MappedPermissibleValuePreviewDto(

        @Schema(description = "名称", example = "MALE")
        String name,

        @Schema(description = "描述")
        String description,

        @Schema(description = "所属值域名称")
        String valueDomainName,

        @Schema(description = "来源枚举类全限定名")
        String sourceEnumClassName,

        @Schema(description = "值含义", example = "MALE")
        String valueMeaning
) {
}
