package io.github.matrixlane.metadataregistry.draftgeneration.persistence;

import io.github.matrixlane.metadataregistry.draftgeneration.model.ColumnDraft;
import io.github.matrixlane.metadataregistry.draftgeneration.model.DraftGenerationResult;
import io.github.matrixlane.metadataregistry.draftgeneration.model.NamingSuggestion;
import io.github.matrixlane.metadataregistry.draftgeneration.model.TableDraft;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 草稿模型与持久化实体之间的转换器。
 */
public class MetadataDraftConverter {

    private MetadataDraftConverter() {
        // utility class
    }

    /**
     * 将 TableDraft 转换为 MetadataDraftEntity。
     */
    public static MetadataDraftEntity toEntity(TableDraft tableDraft,
                                                String databaseName,
                                                String schemaName) {
        if (tableDraft == null) {
            return null;
        }

        MetadataDraftEntity entity = new MetadataDraftEntity(
                databaseName != null ? databaseName : "",
                schemaName != null ? schemaName : "",
                tableDraft.getSourceTableName(),
                tableDraft.getSuggestedTableName()
        );
        entity.setTableRemarks(tableDraft.getRemarks());
        entity.setExtractedAt(tableDraft.getExtractedAt());

        if (tableDraft.getColumnDrafts() != null) {
            for (ColumnDraft columnDraft : tableDraft.getColumnDrafts()) {
                entity.addColumn(toEntity(columnDraft));
            }
        }

        return entity;
    }

    /**
     * 将 ColumnDraft 转换为 ColumnDraftEntity。
     */
    public static ColumnDraftEntity toEntity(ColumnDraft columnDraft) {
        if (columnDraft == null) {
            return null;
        }

        ColumnDraftEntity entity = new ColumnDraftEntity(
                columnDraft.getSourceColumnName(),
                columnDraft.getSuggestedColumnName(),
                columnDraft.getSourceDataType(),
                columnDraft.getMappedDataType(),
                columnDraft.getMappingConfidence()
        );
        entity.setMappingReason(
                columnDraft.getNamingSuggestion() != null
                        ? columnDraft.getNamingSuggestion().getRuleName()
                        : null
        );
        entity.setRemarks(columnDraft.getRemarks());
        entity.setColumnSize(columnDraft.getColumnSize());
        entity.setDecimalDigits(columnDraft.getDecimalDigits());
        entity.setNullable(columnDraft.getNullable());
        entity.setDefaultValue(columnDraft.getDefaultValue());
        entity.setAutoIncrement(columnDraft.getAutoIncrement());

        NamingSuggestion namingSuggestion = columnDraft.getNamingSuggestion();
        if (namingSuggestion != null) {
            entity.setNamingRuleApplied(namingSuggestion.getRuleName());
        }

        return entity;
    }

    /**
     * 将 DraftGenerationResult 转换为 MetadataDraftEntity 列表。
     */
    public static List<MetadataDraftEntity> toEntities(DraftGenerationResult result) {
        if (result == null || result.getTableDrafts() == null) {
            return List.of();
        }

        return result.getTableDrafts().stream()
                .map(td -> toEntity(td, result.getSourceDatabaseName(), result.getSourceSchemaName()))
                .collect(Collectors.toList());
    }
}
