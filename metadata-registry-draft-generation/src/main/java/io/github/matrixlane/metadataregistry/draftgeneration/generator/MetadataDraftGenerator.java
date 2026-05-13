package io.github.matrixlane.metadataregistry.draftgeneration.generator;

import io.github.matrixlane.metadataregistry.draftgeneration.mapping.DataTypeMapper;
import io.github.matrixlane.metadataregistry.draftgeneration.mapping.SqlServerDataTypeMapper;
import io.github.matrixlane.metadataregistry.draftgeneration.model.ColumnDraft;
import io.github.matrixlane.metadataregistry.draftgeneration.model.DraftGenerationResult;
import io.github.matrixlane.metadataregistry.draftgeneration.model.MappingConfidence;
import io.github.matrixlane.metadataregistry.draftgeneration.model.NamingSuggestion;
import io.github.matrixlane.metadataregistry.draftgeneration.model.TableDraft;
import io.github.matrixlane.metadataregistry.draftgeneration.naming.NamingRuleEngine;
import io.github.matrixlane.metadataregistry.schemaextraction.model.ColumnInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.model.DatabaseSchema;
import io.github.matrixlane.metadataregistry.schemaextraction.model.TableInfo;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 元数据草稿生成器。
 *
 * <p>基于已提取的数据库 Schema，自动生成元数据草稿，包括：
 * <ul>
 *   <li>数据类型映射（带置信度）</li>
 *   <li>命名规范化建议</li>
 *   <li>来源追踪</li>
 * </ul>
 */
@Slf4j
public class MetadataDraftGenerator {

    private final DataTypeMapper dataTypeMapper;
    private final NamingRuleEngine namingRuleEngine;

    /**
     * 缓存已生成的草稿，以 (database+schema+table) 为键。
     */
    private final Map<String, TableDraft> draftCache = new ConcurrentHashMap<>();

    public MetadataDraftGenerator() {
        this(new SqlServerDataTypeMapper(), new NamingRuleEngine());
    }

    public MetadataDraftGenerator(DataTypeMapper dataTypeMapper, NamingRuleEngine namingRuleEngine) {
        this.dataTypeMapper = dataTypeMapper != null ? dataTypeMapper : new SqlServerDataTypeMapper();
        this.namingRuleEngine = namingRuleEngine != null ? namingRuleEngine : new NamingRuleEngine();
    }

    /**
     * 从数据库 Schema 生成元数据草稿。
     *
     * @param databaseSchema 已提取的数据库 Schema
     * @return 草稿生成结果
     */
    public DraftGenerationResult generate(DatabaseSchema databaseSchema) {
        long startTime = System.currentTimeMillis();

        if (databaseSchema == null || databaseSchema.getTables() == null || databaseSchema.getTables().isEmpty()) {
            log.warn("Empty or null database schema provided, returning empty result");
            return DraftGenerationResult.builder()
                    .totalTables(0)
                    .totalColumns(0)
                    .lowConfidenceCount(0)
                    .generationTimeMs(System.currentTimeMillis() - startTime)
                    .sourceDatabaseName(databaseSchema != null ? databaseSchema.getDatabaseProductName() : null)
                    .sourceSchemaName(databaseSchema != null ? databaseSchema.getSchema() : null)
                    .build();
        }

        List<TableDraft> tableDrafts = new ArrayList<>();
        int totalColumns = 0;
        int lowConfidenceCount = 0;

        for (TableInfo tableInfo : databaseSchema.getTables()) {
            TableDraft tableDraft = generateTableDraft(tableInfo);
            tableDrafts.add(tableDraft);
            totalColumns += tableDraft.getColumnDrafts().size();
            lowConfidenceCount += countLowConfidence(tableDraft);
        }

        long generationTimeMs = System.currentTimeMillis() - startTime;

        log.info("Generated {} table drafts with {} columns in {} ms ({} low confidence mappings)",
                tableDrafts.size(), totalColumns, generationTimeMs, lowConfidenceCount);

        return DraftGenerationResult.builder()
                .tableDrafts(tableDrafts)
                .totalTables(tableDrafts.size())
                .totalColumns(totalColumns)
                .lowConfidenceCount(lowConfidenceCount)
                .generationTimeMs(generationTimeMs)
                .sourceDatabaseName(databaseSchema.getDatabaseProductName())
                .sourceSchemaName(databaseSchema.getSchema())
                .build();
    }

    /**
     * 为单个表生成草稿。
     *
     * @param tableInfo 表信息
     * @return 表草稿
     */
    public TableDraft generateTableDraft(TableInfo tableInfo) {
        if (tableInfo == null) {
            throw new IllegalArgumentException("TableInfo cannot be null");
        }

        String sourceTableName = tableInfo.getTableName();
        NamingSuggestion tableNamingSuggestion = namingRuleEngine.getFirstSuggestion(sourceTableName);
        String suggestedTableName = tableNamingSuggestion != null
                ? tableNamingSuggestion.getSuggestedName()
                : sourceTableName;

        List<ColumnDraft> columnDrafts = new ArrayList<>();
        if (tableInfo.getColumns() != null) {
            for (ColumnInfo columnInfo : tableInfo.getColumns()) {
                columnDrafts.add(generateColumnDraft(columnInfo));
            }
        }

        return TableDraft.builder()
                .sourceTableName(sourceTableName)
                .suggestedTableName(suggestedTableName)
                .remarks(tableInfo.getRemarks())
                .extractedAt(LocalDateTime.now())
                .columnDrafts(columnDrafts)
                .namingSuggestion(tableNamingSuggestion)
                .build();
    }

    /**
     * 为单个列生成草稿。
     *
     * @param columnInfo 列信息
     * @return 列草稿
     */
    public ColumnDraft generateColumnDraft(ColumnInfo columnInfo) {
        if (columnInfo == null) {
            throw new IllegalArgumentException("ColumnInfo cannot be null");
        }

        String sourceColumnName = columnInfo.getColumnName();
        NamingSuggestion columnNamingSuggestion = namingRuleEngine.getFirstSuggestion(sourceColumnName);
        String suggestedColumnName = columnNamingSuggestion != null
                ? columnNamingSuggestion.getSuggestedName()
                : sourceColumnName;

        var mappingResult = dataTypeMapper.map(
                columnInfo.getDataType(),
                columnInfo.getColumnSize(),
                columnInfo.getDecimalDigits()
        );

        return ColumnDraft.builder()
                .sourceColumnName(sourceColumnName)
                .suggestedColumnName(suggestedColumnName)
                .sourceDataType(columnInfo.getDataType())
                .mappedDataType(mappingResult.getMappedType())
                .mappingConfidence(mappingResult.getConfidence())
                .remarks(columnInfo.getRemarks())
                .columnSize(columnInfo.getColumnSize())
                .decimalDigits(columnInfo.getDecimalDigits())
                .nullable(columnInfo.getNullable())
                .defaultValue(columnInfo.getDefaultValue())
                .autoIncrement(columnInfo.getAutoIncrement())
                .namingSuggestion(columnNamingSuggestion)
                .build();
    }

    private int countLowConfidence(TableDraft tableDraft) {
        if (tableDraft.getColumnDrafts() == null) {
            return 0;
        }
        return (int) tableDraft.getColumnDrafts().stream()
                .filter(cd -> cd.getMappingConfidence() == MappingConfidence.LOW)
                .count();
    }

    /**
     * 清除草稿缓存。
     */
    public void clearCache() {
        draftCache.clear();
    }
}
