package io.github.matrixlane.metadataregistry.draftgeneration.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 草稿生成结果汇总。
 */
@Data
@Builder
public class DraftGenerationResult {

    /**
     * 生成的表草稿列表。
     */
    @Builder.Default
    private List<TableDraft> tableDrafts = new ArrayList<>();

    /**
     * 总表数。
     */
    private int totalTables;

    /**
     * 总列数。
     */
    private int totalColumns;

    /**
     * 低置信度映射的数量（需人工确认）。
     */
    private int lowConfidenceCount;

    /**
     * 生成耗时（毫秒）。
     */
    private long generationTimeMs;

    /**
     * 原始数据库名称。
     */
    private String sourceDatabaseName;

    /**
     * 原始 Schema 名称。
     */
    private String sourceSchemaName;
}
