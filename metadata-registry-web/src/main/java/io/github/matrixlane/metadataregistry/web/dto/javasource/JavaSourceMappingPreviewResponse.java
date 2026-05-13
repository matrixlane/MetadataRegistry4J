package io.github.matrixlane.metadataregistry.web.dto.javasource;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response containing mapped Administered Item previews from Java source comments.
 */
@Schema(description = "Java 源码注释映射为 Administered Item 的预览结果")
public record JavaSourceMappingPreviewResponse(

        @Schema(description = "映射的数据元概念列表")
        List<MappedDataElementConceptPreviewDto> dataElementConcepts,

        @Schema(description = "映射的数据元列表")
        List<MappedDataElementPreviewDto> dataElements,

        @Schema(description = "映射的值域列表")
        List<MappedValueDomainPreviewDto> valueDomains,

        @Schema(description = "映射的允许值列表")
        List<MappedPermissibleValuePreviewDto> permissibleValues,

        @Schema(description = "警告信息列表")
        List<String> warnings
) {
}
