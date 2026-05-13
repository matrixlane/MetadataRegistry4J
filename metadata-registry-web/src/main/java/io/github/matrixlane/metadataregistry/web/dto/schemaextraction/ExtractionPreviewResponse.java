package io.github.matrixlane.metadataregistry.web.dto.schemaextraction;

import java.util.List;
import java.util.UUID;

/**
 * Preview of extracted schema mapped to administered item candidates.
 */
public record ExtractionPreviewResponse(
        UUID sessionId,
        String databaseProductName,
        String catalog,
        String schema,
        int tableCount,
        int columnCount,
        List<MappedDataElementConceptPreview> dataElementConcepts,
        List<MappedDataElementPreview> dataElements,
        List<MappedValueDomainPreview> valueDomains,
        List<MappedRelationshipPreview> relationships
) {

    public record MappedDataElementConceptPreview(
            String name,
            String description,
            String tableName,
            String objectClassName,
            String propertyName
    ) {
    }

    public record MappedDataElementPreview(
            String name,
            String description,
            String tableName,
            String columnName,
            boolean nullable,
            boolean primaryKey,
            boolean autoIncrement,
            String dataType,
            Integer columnSize,
            Integer decimalDigits,
            String valueDomainName,
            String dataElementConceptName
    ) {
    }

    public record MappedValueDomainPreview(
            String name,
            String description,
            String dataTypeName,
            Integer maximumCharacterQuantity,
            Integer decimalScale
    ) {
    }

    public record MappedRelationshipPreview(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            String constraintName
    ) {
    }
}
