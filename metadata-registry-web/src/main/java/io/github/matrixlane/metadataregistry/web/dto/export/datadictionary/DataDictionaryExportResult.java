package io.github.matrixlane.metadataregistry.web.dto.export.datadictionary;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 数据字典导出结果（同步导出直接返回，异步导出通过任务查询）
 */
public record DataDictionaryExportResult(
        ExportMetadata metadata,
        List<DataDictionaryItemExport> items
) {

    public record ExportMetadata(
            String schemaVersion,
            LocalDateTime exportedAt,
            int totalCount,
            String format,
            Map<String, Object> appliedFilters
    ) {}
}
