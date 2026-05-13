package io.github.matrixlane.metadataregistry.draftgeneration.generator;

import io.github.matrixlane.metadataregistry.draftgeneration.model.*;
import io.github.matrixlane.metadataregistry.schemaextraction.model.ColumnInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.model.DatabaseSchema;
import io.github.matrixlane.metadataregistry.schemaextraction.model.TableInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetadataDraftGeneratorTest {

    private MetadataDraftGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MetadataDraftGenerator();
    }

    @Test
    void generate_shouldReturnEmptyResultForNullSchema() {
        DraftGenerationResult result = generator.generate(null);

        assertThat(result.getTotalTables()).isZero();
        assertThat(result.getTotalColumns()).isZero();
        assertThat(result.getLowConfidenceCount()).isZero();
    }

    @Test
    void generate_shouldReturnEmptyResultForEmptySchema() {
        DatabaseSchema schema = DatabaseSchema.builder()
                .databaseProductName("Microsoft SQL Server")
                .schema("dbo")
                .build();

        DraftGenerationResult result = generator.generate(schema);

        assertThat(result.getTotalTables()).isZero();
        assertThat(result.getTotalColumns()).isZero();
    }

    @Test
    void generate_shouldGenerateDraftsForAllTables() {
        ColumnInfo col1 = ColumnInfo.builder()
                .columnName("user_name")
                .dataType("VARCHAR")
                .columnSize(100)
                .build();
        ColumnInfo col2 = ColumnInfo.builder()
                .columnName("created_at")
                .dataType("DATETIME")
                .build();

        TableInfo table = TableInfo.builder()
                .tableName("tbl_user")
                .columns(List.of(col1, col2))
                .build();

        DatabaseSchema schema = DatabaseSchema.builder()
                .databaseProductName("Microsoft SQL Server")
                .schema("dbo")
                .tables(List.of(table))
                .build();

        DraftGenerationResult result = generator.generate(schema);

        assertThat(result.getTotalTables()).isEqualTo(1);
        assertThat(result.getTotalColumns()).isEqualTo(2);
        assertThat(result.getSourceDatabaseName()).isEqualTo("Microsoft SQL Server");
        assertThat(result.getSourceSchemaName()).isEqualTo("dbo");
        assertThat(result.getGenerationTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void generate_shouldCountLowConfidenceMappings() {
        ColumnInfo col = ColumnInfo.builder()
                .columnName("custom_field")
                .dataType("UNKNOWN_TYPE")
                .build();

        TableInfo table = TableInfo.builder()
                .tableName("test_table")
                .columns(List.of(col))
                .build();

        DatabaseSchema schema = DatabaseSchema.builder()
                .tables(List.of(table))
                .build();

        DraftGenerationResult result = generator.generate(schema);

        assertThat(result.getLowConfidenceCount()).isEqualTo(1);
    }

    @Test
    void generateTableDraft_shouldApplyNamingRules() {
        TableInfo table = TableInfo.builder()
                .tableName("tbl_order_item")
                .columns(List.of())
                .build();

        TableDraft draft = generator.generateTableDraft(table);

        assertThat(draft.getSourceTableName()).isEqualTo("tbl_order_item");
        assertThat(draft.getSuggestedTableName()).isEqualTo("order_item");
        assertThat(draft.getExtractedAt()).isNotNull();
    }

    @Test
    void generateTableDraft_shouldThrowForNullTable() {
        assertThatThrownBy(() -> generator.generateTableDraft(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TableInfo cannot be null");
    }

    @Test
    void generateColumnDraft_shouldMapDataType() {
        ColumnInfo column = ColumnInfo.builder()
                .columnName("user_name")
                .dataType("NVARCHAR")
                .columnSize(255)
                .nullable(true)
                .defaultValue("N/A")
                .build();

        ColumnDraft draft = generator.generateColumnDraft(column);

        assertThat(draft.getSourceColumnName()).isEqualTo("user_name");
        assertThat(draft.getSuggestedColumnName()).isEqualTo("userName");
        assertThat(draft.getSourceDataType()).isEqualTo("NVARCHAR");
        assertThat(draft.getMappedDataType()).isEqualTo("String");
        assertThat(draft.getMappingConfidence()).isEqualTo(MappingConfidence.HIGH);
        assertThat(draft.getColumnSize()).isEqualTo(255);
        assertThat(draft.getNullable()).isTrue();
        assertThat(draft.getDefaultValue()).isEqualTo("N/A");
        assertThat(draft.getNamingSuggestion()).isNotNull();
    }

    @Test
    void generateColumnDraft_shouldHandleDecimalWithMediumConfidence() {
        ColumnInfo column = ColumnInfo.builder()
                .columnName("price")
                .dataType("DECIMAL")
                .columnSize(18)
                .decimalDigits(2)
                .build();

        ColumnDraft draft = generator.generateColumnDraft(column);

        assertThat(draft.getMappedDataType()).isEqualTo("Decimal");
        assertThat(draft.getMappingConfidence()).isEqualTo(MappingConfidence.MEDIUM);
    }

    @Test
    void generateColumnDraft_shouldHandleUnknownTypeWithLowConfidence() {
        ColumnInfo column = ColumnInfo.builder()
                .columnName("custom_data")
                .dataType("CUSTOM_TYPE")
                .build();

        ColumnDraft draft = generator.generateColumnDraft(column);

        assertThat(draft.getMappedDataType()).isEqualTo("Unknown");
        assertThat(draft.getMappingConfidence()).isEqualTo(MappingConfidence.LOW);
    }

    @Test
    void generateColumnDraft_shouldThrowForNullColumn() {
        assertThatThrownBy(() -> generator.generateColumnDraft(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ColumnInfo cannot be null");
    }

    @Test
    void generate_shouldHandleMultipleTables() {
        TableInfo table1 = TableInfo.builder()
                .tableName("tbl_user")
                .columns(List.of(
                        ColumnInfo.builder().columnName("id").dataType("INT").build()
                ))
                .build();
        TableInfo table2 = TableInfo.builder()
                .tableName("tbl_order")
                .columns(List.of(
                        ColumnInfo.builder().columnName("total_amount").dataType("DECIMAL").build()
                ))
                .build();

        DatabaseSchema schema = DatabaseSchema.builder()
                .tables(List.of(table1, table2))
                .build();

        DraftGenerationResult result = generator.generate(schema);

        assertThat(result.getTotalTables()).isEqualTo(2);
        assertThat(result.getTotalColumns()).isEqualTo(2);
        assertThat(result.getTableDrafts()).hasSize(2);
    }

    @Test
    void clearCache_shouldNotThrow() {
        generator.clearCache();
    }

    @Test
    void generate_shouldMeetPerformanceRequirement() {
        // Generate 1000 tables with 5 columns each
        List<TableInfo> tables = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            List<ColumnInfo> columns = new java.util.ArrayList<>();
            for (int j = 0; j < 5; j++) {
                columns.add(ColumnInfo.builder()
                        .columnName("col_" + j)
                        .dataType("VARCHAR")
                        .build());
            }
            tables.add(TableInfo.builder()
                    .tableName("tbl_table_" + i)
                    .columns(columns)
                    .build());
        }

        DatabaseSchema schema = DatabaseSchema.builder()
                .tables(tables)
                .build();

        long start = System.currentTimeMillis();
        DraftGenerationResult result = generator.generate(schema);
        long duration = System.currentTimeMillis() - start;

        assertThat(result.getTotalTables()).isEqualTo(1000);
        assertThat(result.getTotalColumns()).isEqualTo(5000);
        // Requirement: 1000 tables in <= 5 seconds
        assertThat(duration).isLessThanOrEqualTo(5000);
    }
}
