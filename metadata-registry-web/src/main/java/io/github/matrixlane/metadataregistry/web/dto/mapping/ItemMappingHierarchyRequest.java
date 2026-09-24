package io.github.matrixlane.metadataregistry.web.dto.mapping;

import java.util.UUID;

/**
 * 映射层级维护请求（mapping_hierarchy）。
 *
 * @param supersetMappingId 上层（superset）映射 UUID；null 表示清除现有上层关系
 */
public record ItemMappingHierarchyRequest(
        UUID supersetMappingId
) {}
