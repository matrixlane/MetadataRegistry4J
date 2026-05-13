package io.github.matrixlane.metadataregistry.schemaextraction.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ModelTest {

    @Test
    void shouldBuildColumnInfo() {
        ColumnInfo col = ColumnInfo.builder()
                .columnName("id")
                .ordinalPosition(1)
                .dataType("bigint")
                .columnSize(19)
                .decimalDigits(0)
                .nullable(false)
                .defaultValue(null)
                .remarks("Primary key")
                .autoIncrement(true)
                .generatedColumn(false)
                .build();

        assertThat(col.getColumnName()).isEqualTo("id");
        assertThat(col.getDataType()).isEqualTo("bigint");
        assertThat(col.getAutoIncrement()).isTrue();
    }

    @Test
    void shouldBuildPrimaryKeyInfo() {
        PrimaryKeyInfo pk = PrimaryKeyInfo.builder()
                .constraintName("PK_users")
                .columnNames(List.of("id"))
                .build();

        assertThat(pk.getConstraintName()).isEqualTo("PK_users");
        assertThat(pk.getColumnNames()).containsExactly("id");
    }

    @Test
    void shouldBuildIndexInfo() {
        IndexInfo idx = IndexInfo.builder()
                .indexName("IX_name")
                .unique(false)
                .type("OTHER")
                .columnNames(List.of("name"))
                .filterCondition(null)
                .build();

        assertThat(idx.getIndexName()).isEqualTo("IX_name");
        assertThat(idx.getUnique()).isFalse();
    }

    @Test
    void shouldBuildForeignKeyInfo() {
        ForeignKeyInfo fk = ForeignKeyInfo.builder()
                .constraintName("FK_orders_customer")
                .columnName("customer_id")
                .referencedTableName("customers")
                .referencedColumnName("id")
                .updateRule(1)
                .deleteRule(0)
                .build();

        assertThat(fk.getReferencedTableName()).isEqualTo("customers");
    }

    @Test
    void shouldBuildTableInfoWithDefaults() {
        TableInfo table = TableInfo.builder()
                .tableName("users")
                .tableType("TABLE")
                .build();

        assertThat(table.getTableName()).isEqualTo("users");
        assertThat(table.getColumns()).isEmpty();
        assertThat(table.getIndexes()).isEmpty();
        assertThat(table.getForeignKeys()).isEmpty();
    }

    @Test
    void shouldBuildDatabaseSchemaWithDefaults() {
        DatabaseSchema schema = DatabaseSchema.builder()
                .databaseProductName("SQL Server")
                .catalog("testdb")
                .build();

        assertThat(schema.getDatabaseProductName()).isEqualTo("SQL Server");
        assertThat(schema.getTables()).isEmpty();
    }

    @Test
    void shouldUseToBuilder() {
        ColumnInfo original = ColumnInfo.builder()
                .columnName("id")
                .dataType("int")
                .build();

        ColumnInfo modified = original.toBuilder()
                .remarks("Modified")
                .build();

        assertThat(modified.getColumnName()).isEqualTo("id");
        assertThat(modified.getRemarks()).isEqualTo("Modified");
    }
}
