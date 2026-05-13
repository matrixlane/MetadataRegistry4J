package io.github.matrixlane.metadataregistry.schemaextraction.mapping;

import io.github.matrixlane.metadataregistry.schemaextraction.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SchemaMappingServiceTest {

    private final SchemaMappingService service = new SchemaMappingService();

    @Test
    void shouldMapEmptySchema() {
        DatabaseSchema schema = DatabaseSchema.builder()
                .databaseProductName("H2")
                .catalog("testdb")
                .build();

        SchemaMappingResult result = service.mapSchema(schema);

        assertThat(result.getDataElementConcepts()).isEmpty();
        assertThat(result.getDataElements()).isEmpty();
        assertThat(result.getValueDomains()).isEmpty();
        assertThat(result.getRelationships()).isEmpty();
    }

    @Test
    void shouldMapTableToDataElementConcept() {
        DatabaseSchema schema = DatabaseSchema.builder()
                .databaseProductName("PostgreSQL")
                .catalog("testdb")
                .tables(List.of(TableInfo.builder()
                        .tableName("patients")
                        .tableType("TABLE")
                        .remarks("Patient demographic info")
                        .columns(List.of(
                                ColumnInfo.builder()
                                        .columnName("patient_id")
                                        .ordinalPosition(1)
                                        .dataType("varchar")
                                        .columnSize(50)
                                        .nullable(false)
                                        .build()
                        ))
                        .primaryKey(PrimaryKeyInfo.builder()
                                .constraintName("pk_patients")
                                .columnNames(List.of("patient_id"))
                                .build())
                        .build()))
                .build();

        SchemaMappingResult result = service.mapSchema(schema);

        assertThat(result.getDataElementConcepts()).hasSize(1);
        MappedDataElementConcept dec = result.getDataElementConcepts().get(0);
        assertThat(dec.getName()).isEqualTo("patients");
        assertThat(dec.getDescription()).isEqualTo("Patient demographic info");
        assertThat(dec.getTableName()).isEqualTo("patients");
        assertThat(dec.getObjectClassName()).isEqualTo("patients");
        assertThat(dec.getPropertyName()).isEqualTo("record");
    }

    @Test
    void shouldMapColumnToDataElement() {
        DatabaseSchema schema = DatabaseSchema.builder()
                .databaseProductName("MySQL")
                .catalog("testdb")
                .tables(List.of(TableInfo.builder()
                        .tableName("orders")
                        .tableType("TABLE")
                        .columns(List.of(
                                ColumnInfo.builder()
                                        .columnName("order_id")
                                        .ordinalPosition(1)
                                        .dataType("bigint")
                                        .columnSize(19)
                                        .nullable(false)
                                        .autoIncrement(true)
                                        .build(),
                                ColumnInfo.builder()
                                        .columnName("total")
                                        .ordinalPosition(2)
                                        .dataType("decimal")
                                        .columnSize(18)
                                        .decimalDigits(2)
                                        .nullable(true)
                                        .build()
                        ))
                        .primaryKey(PrimaryKeyInfo.builder()
                                .constraintName("pk_orders")
                                .columnNames(List.of("order_id"))
                                .build())
                        .build()))
                .build();

        SchemaMappingResult result = service.mapSchema(schema);

        assertThat(result.getDataElements()).hasSize(2);

        MappedDataElement pk = result.getDataElements().get(0);
        assertThat(pk.getName()).isEqualTo("order_id");
        assertThat(pk.getColumnName()).isEqualTo("order_id");
        assertThat(pk.isPrimaryKey()).isTrue();
        assertThat(pk.isAutoIncrement()).isTrue();
        assertThat(pk.isNullable()).isFalse();
        assertThat(pk.getDataElementConcept().getName()).isEqualTo("orders");

        MappedDataElement total = result.getDataElements().get(1);
        assertThat(total.getName()).isEqualTo("total");
        assertThat(total.isPrimaryKey()).isFalse();
        assertThat(total.isNullable()).isTrue();
    }

    @Test
    void shouldMapDataTypesToValueDomains() {
        DatabaseSchema schema = DatabaseSchema.builder()
                .databaseProductName("SQL Server")
                .catalog("testdb")
                .tables(List.of(TableInfo.builder()
                        .tableName("products")
                        .tableType("TABLE")
                        .columns(List.of(
                                ColumnInfo.builder()
                                        .columnName("name")
                                        .ordinalPosition(1)
                                        .dataType("nvarchar")
                                        .columnSize(255)
                                        .nullable(false)
                                        .build(),
                                ColumnInfo.builder()
                                        .columnName("price")
                                        .ordinalPosition(2)
                                        .dataType("decimal")
                                        .columnSize(18)
                                        .decimalDigits(4)
                                        .nullable(true)
                                        .build(),
                                ColumnInfo.builder()
                                        .columnName("qty")
                                        .ordinalPosition(3)
                                        .dataType("int")
                                        .columnSize(10)
                                        .nullable(true)
                                        .build()
                        ))
                        .build()))
                .build();

        SchemaMappingResult result = service.mapSchema(schema);

        // Should have 3 distinct value domains
        assertThat(result.getValueDomains()).hasSize(3);

        MappedValueDomain stringVd = result.getValueDomains().get(0);
        assertThat(stringVd.getDataTypeName()).isEqualTo("String");
        assertThat(stringVd.getMaximumCharacterQuantity()).isEqualTo(255);
        assertThat(stringVd.getName()).isEqualTo("String_255_VD");

        MappedValueDomain decimalVd = result.getValueDomains().get(1);
        assertThat(decimalVd.getDataTypeName()).isEqualTo("Decimal");
        assertThat(decimalVd.getMaximumCharacterQuantity()).isEqualTo(18);
        assertThat(decimalVd.getDecimalScale()).isEqualTo(4);
        assertThat(decimalVd.getName()).isEqualTo("Decimal_18_4_VD");

        MappedValueDomain intVd = result.getValueDomains().get(2);
        assertThat(intVd.getDataTypeName()).isEqualTo("Integer");
        assertThat(intVd.getName()).isEqualTo("Integer_10_VD");
    }

    @Test
    void shouldReuseIdenticalValueDomains() {
        DatabaseSchema schema = DatabaseSchema.builder()
                .databaseProductName("MySQL")
                .catalog("testdb")
                .tables(List.of(
                        TableInfo.builder()
                                .tableName("users")
                                .tableType("TABLE")
                                .columns(List.of(
                                        ColumnInfo.builder()
                                                .columnName("email")
                                                .ordinalPosition(1)
                                                .dataType("varchar")
                                                .columnSize(255)
                                                .nullable(false)
                                                .build()
                                ))
                                .build(),
                        TableInfo.builder()
                                .tableName("contacts")
                                .tableType("TABLE")
                                .columns(List.of(
                                        ColumnInfo.builder()
                                                .columnName("email")
                                                .ordinalPosition(1)
                                                .dataType("varchar")
                                                .columnSize(255)
                                                .nullable(false)
                                                .build()
                                ))
                                .build()
                ))
                .build();

        SchemaMappingResult result = service.mapSchema(schema);

        // Two tables, two columns, but same type/size so only 1 value domain
        assertThat(result.getValueDomains()).hasSize(1);
        assertThat(result.getDataElements()).hasSize(2);
    }

    @Test
    void shouldMapForeignKeysToRelationships() {
        DatabaseSchema schema = DatabaseSchema.builder()
                .databaseProductName("PostgreSQL")
                .catalog("testdb")
                .tables(List.of(TableInfo.builder()
                        .tableName("orders")
                        .tableType("TABLE")
                        .columns(List.of(
                                ColumnInfo.builder()
                                        .columnName("customer_id")
                                        .ordinalPosition(1)
                                        .dataType("varchar")
                                        .columnSize(50)
                                        .nullable(false)
                                        .build()
                        ))
                        .foreignKeys(List.of(
                                ForeignKeyInfo.builder()
                                        .constraintName("fk_orders_customer")
                                        .columnName("customer_id")
                                        .referencedTableName("customers")
                                        .referencedColumnName("id")
                                        .updateRule(1)
                                        .deleteRule(0)
                                        .build()
                        ))
                        .build()))
                .build();

        SchemaMappingResult result = service.mapSchema(schema);

        assertThat(result.getRelationships()).hasSize(1);
        MappedRelationship rel = result.getRelationships().get(0);
        assertThat(rel.getSourceTable()).isEqualTo("orders");
        assertThat(rel.getSourceColumn()).isEqualTo("customer_id");
        assertThat(rel.getTargetTable()).isEqualTo("customers");
        assertThat(rel.getTargetColumn()).isEqualTo("id");
        assertThat(rel.getConstraintName()).isEqualTo("fk_orders_customer");
    }

    @Test
    void shouldNormalizeVariousDataTypes() {
        DatabaseSchema schema = DatabaseSchema.builder()
                .databaseProductName("PostgreSQL")
                .catalog("testdb")
                .tables(List.of(TableInfo.builder()
                        .tableName("all_types")
                        .tableType("TABLE")
                        .columns(List.of(
                                ColumnInfo.builder().columnName("c1").dataType("character varying").build(),
                                ColumnInfo.builder().columnName("c2").dataType("int4").build(),
                                ColumnInfo.builder().columnName("c3").dataType("int8").build(),
                                ColumnInfo.builder().columnName("c4").dataType("float8").build(),
                                ColumnInfo.builder().columnName("c5").dataType("bool").build(),
                                ColumnInfo.builder().columnName("c6").dataType("timestamptz").build(),
                                ColumnInfo.builder().columnName("c7").dataType("jsonb").build(),
                                ColumnInfo.builder().columnName("c8").dataType("bytea").build(),
                                ColumnInfo.builder().columnName("c9").dataType("uuid").build(),
                                ColumnInfo.builder().columnName("c10").dataType("serial").build()
                        ))
                        .build()))
                .build();

        SchemaMappingResult result = service.mapSchema(schema);

        List<String> typeNames = result.getValueDomains().stream()
                .map(MappedValueDomain::getDataTypeName)
                .toList();

        assertThat(typeNames).containsExactlyInAnyOrder(
                "String", "Integer", "Long", "Double", "Boolean",
                "DateTime", "JSON", "Binary", "UUID"
        );
    }

    @Test
    void shouldUseDefaultDescriptionWhenRemarksAreNull() {
        DatabaseSchema schema = DatabaseSchema.builder()
                .databaseProductName("MySQL")
                .catalog("testdb")
                .tables(List.of(TableInfo.builder()
                        .tableName("no_comments")
                        .tableType("TABLE")
                        .columns(List.of(
                                ColumnInfo.builder()
                                        .columnName("id")
                                        .ordinalPosition(1)
                                        .dataType("int")
                                        .build()
                        ))
                        .build()))
                .build();

        SchemaMappingResult result = service.mapSchema(schema);

        MappedDataElementConcept dec = result.getDataElementConcepts().get(0);
        assertThat(dec.getDescription()).isEqualTo("Data element concept for table no_comments");

        MappedDataElement de = result.getDataElements().get(0);
        assertThat(de.getDescription()).isEqualTo("Data element for column no_comments.id");
    }
}
