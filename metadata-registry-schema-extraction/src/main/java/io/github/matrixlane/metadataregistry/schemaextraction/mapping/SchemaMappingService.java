package io.github.matrixlane.metadataregistry.schemaextraction.mapping;

import io.github.matrixlane.metadataregistry.schemaextraction.model.*;

import java.util.*;

/**
 * Maps extracted database schema to ISO/IEC 11179 administered item candidates.
 *
 * <p>Mapping rules:
 * <ul>
 *   <li>Table → DataElementConcept (ObjectClass = table name, Property = "record")</li>
 *   <li>Column → DataElement (references table's DEC and a ValueDomain)</li>
 *   <li>Column data type → ValueDomain</li>
 *   <li>Foreign key → Relationship</li>
 * </ul>
 */
public class SchemaMappingService {

    /**
     * Maps a database schema to administered item candidates.
     */
    public SchemaMappingResult mapSchema(DatabaseSchema schema) {
        List<MappedDataElementConcept> decs = new ArrayList<>();
        List<MappedDataElement> des = new ArrayList<>();
        List<MappedRelationship> rels = new ArrayList<>();
        Map<String, MappedValueDomain> valueDomainCache = new LinkedHashMap<>();

        for (TableInfo table : schema.getTables()) {
            // 1. Map table to DataElementConcept
            MappedDataElementConcept dec = mapTableToDec(table);
            decs.add(dec);

            Set<String> pkColumns = table.getPrimaryKey() != null
                    ? new HashSet<>(table.getPrimaryKey().getColumnNames())
                    : Collections.emptySet();

            // 2. Map columns to DataElements and ValueDomains
            for (ColumnInfo column : table.getColumns()) {
                MappedValueDomain vd = mapColumnToValueDomain(column);
                String vdKey = buildValueDomainKey(vd);
                MappedValueDomain cachedVd = valueDomainCache.computeIfAbsent(vdKey, k -> vd);

                MappedDataElement de = mapColumnToDataElement(table, column, dec, cachedVd, pkColumns.contains(column.getColumnName()));
                des.add(de);
            }

            // 3. Map foreign keys to relationships
            for (ForeignKeyInfo fk : table.getForeignKeys()) {
                rels.add(MappedRelationship.builder()
                        .sourceTable(table.getTableName())
                        .sourceColumn(fk.getColumnName())
                        .targetTable(fk.getReferencedTableName())
                        .targetColumn(fk.getReferencedColumnName())
                        .constraintName(fk.getConstraintName())
                        .build());
            }
        }

        return SchemaMappingResult.builder()
                .databaseProductName(schema.getDatabaseProductName())
                .catalog(schema.getCatalog())
                .schema(schema.getSchema())
                .dataElementConcepts(decs)
                .dataElements(des)
                .valueDomains(new ArrayList<>(valueDomainCache.values()))
                .relationships(rels)
                .build();
    }

    private MappedDataElementConcept mapTableToDec(TableInfo table) {
        String tableName = table.getTableName();
        String description = table.getRemarks() != null ? table.getRemarks()
                : "Data element concept for table " + tableName;

        return MappedDataElementConcept.builder()
                .name(tableName)
                .description(description)
                .tableName(tableName)
                .objectClassName(tableName)
                .propertyName("record")
                .build();
    }

    private MappedDataElement mapColumnToDataElement(TableInfo table, ColumnInfo column,
                                                      MappedDataElementConcept dec,
                                                      MappedValueDomain vd, boolean isPk) {
        String columnName = column.getColumnName();
        String description = column.getRemarks() != null ? column.getRemarks()
                : "Data element for column " + table.getTableName() + "." + columnName;

        return MappedDataElement.builder()
                .name(columnName)
                .description(description)
                .tableName(table.getTableName())
                .columnName(columnName)
                .nullable(Boolean.TRUE.equals(column.getNullable()))
                .primaryKey(isPk)
                .autoIncrement(Boolean.TRUE.equals(column.getAutoIncrement()))
                .dataElementConcept(dec)
                .valueDomain(vd)
                .build();
    }

    private MappedValueDomain mapColumnToValueDomain(ColumnInfo column) {
        String dataType = normalizeDataType(column.getDataType());
        String name = buildValueDomainName(dataType, column.getColumnSize(), column.getDecimalDigits());
        String description = "Value domain for " + dataType
                + (column.getColumnSize() != null ? "(" + column.getColumnSize() + ")" : "");

        MappedValueDomain.MappedValueDomainBuilder builder = MappedValueDomain.builder()
                .name(name)
                .description(description)
                .dataTypeName(dataType);

        if (column.getColumnSize() != null) {
            builder.maximumCharacterQuantity(column.getColumnSize());
        }
        if (column.getDecimalDigits() != null) {
            builder.decimalScale(column.getDecimalDigits());
        }

        return builder.build();
    }

    private String normalizeDataType(String rawType) {
        if (rawType == null) {
            return "UNKNOWN";
        }
        String lower = rawType.toLowerCase();
        return switch (lower) {
            case "varchar", "nvarchar", "nvarchar2", "character varying" -> "String";
            case "char", "nchar", "character" -> "String";
            case "text", "clob", "nclob", "longtext", "mediumtext", "tinytext" -> "Text";
            case "int", "integer", "int4", "serial" -> "Integer";
            case "bigint", "int8", "bigserial" -> "Long";
            case "smallint", "int2", "tinyint" -> "Short";
            case "decimal", "numeric", "number" -> "Decimal";
            case "float", "real", "float4" -> "Float";
            case "double", "double precision", "float8" -> "Double";
            case "bit", "boolean", "bool" -> "Boolean";
            case "date" -> "Date";
            case "datetime", "datetime2", "smalldatetime", "timestamp", "timestamptz" -> "DateTime";
            case "time", "timetz" -> "Time";
            case "binary", "varbinary", "blob", "bytea", "image" -> "Binary";
            case "uniqueidentifier", "uuid" -> "UUID";
            case "money", "smallmoney" -> "Decimal";
            case "xml" -> "XML";
            case "json", "jsonb" -> "JSON";
            default -> rawType;
        };
    }

    private String buildValueDomainName(String dataType, Integer size, Integer scale) {
        StringBuilder sb = new StringBuilder(dataType);
        if (size != null) {
            sb.append("_").append(size);
            if (scale != null) {
                sb.append("_").append(scale);
            }
        }
        sb.append("_VD");
        return sb.toString();
    }

    private String buildValueDomainKey(MappedValueDomain vd) {
        return vd.getDataTypeName() + "|" + vd.getMaximumCharacterQuantity() + "|" + vd.getDecimalScale();
    }
}
