package io.github.matrixlane.metadataregistry.web.dto.classification;

import java.util.List;
import java.util.UUID;

/**
 * 分类导入结果，包含成功/失败状态、统计信息和错误明细列表。
 */
public record ClassificationImportResult(
        boolean success,
        UUID schemeId,
        String schemeName,
        int totalRows,
        int importedNodes,
        int errorCount,
        List<ImportErrorDetail> errors
) {
}
