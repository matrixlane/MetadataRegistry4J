package io.github.matrixlane.metadataregistry.web.dto.compliancereport;

/**
 * 缺失元数据项清单行记录.
 *
 * <p>对应 WI-013-3 需求 2：未匹配的互联互通数据元清单。
 */
public record MissingEntry(
    String deIdentifier,
    String deName,
    String deDefinition,
    String dataSetCode,
    String dataSetName,
    String suggestedAction
) {}
