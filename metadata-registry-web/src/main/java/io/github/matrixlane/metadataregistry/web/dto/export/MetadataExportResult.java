package io.github.matrixlane.metadataregistry.web.dto.export;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 元数据导出结果包装器
 *
 * @param exportMetadata 导出元信息
 * @param items          导出的注册项列表
 */
public record MetadataExportResult(
        ExportMetadata exportMetadata,
        List<AdministeredItemExport> items
) {

    public record ExportMetadata(
            String schemaVersion,
            LocalDateTime exportedAt,
            int totalCount,
            Map<String, Object> appliedFilters
    ) {
    }
}
