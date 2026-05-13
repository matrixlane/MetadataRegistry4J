package io.github.matrixlane.metadataregistry.web.dto.compliancereport;

/**
 * 互联互通元数据对照表行记录.
 *
 * <p>对应 WI-013-3 需求 1：互联互通元数据对照表的一行。
 */
public record MappingEntry(
    int sequence,
    String interopIdentifier,
    String interopName,
    String interopDefinition,
    String localIdentifier,
    String localName,
    String matchStatus,
    String remarks
) {}
