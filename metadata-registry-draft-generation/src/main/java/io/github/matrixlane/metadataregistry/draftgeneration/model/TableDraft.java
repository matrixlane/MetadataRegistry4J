package io.github.matrixlane.metadataregistry.draftgeneration.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 表级别的元数据草稿。
 */
@Data
@Builder
public class TableDraft {

    /**
     * 原始表名。
     */
    private String sourceTableName;

    /**
     * 建议表名（经命名规范化处理）。
     */
    private String suggestedTableName;

    /**
     * 表备注。
     */
    private String remarks;

    /**
     * Schema 提取时间戳。
     */
    private LocalDateTime extractedAt;

    /**
     * 列草稿列表。
     */
    @Builder.Default
    private List<ColumnDraft> columnDrafts = new ArrayList<>();

    /**
     * 命名建议。
     */
    private NamingSuggestion namingSuggestion;
}
