package io.github.matrixlane.metadataregistry.schemaextraction.mapping;

import io.github.matrixlane.metadataregistry.dataspecification.*;
import io.github.matrixlane.metadataregistry.schemaextraction.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AdministeredItemMappingServiceTest {

    private final AdministeredItemMappingService service = new AdministeredItemMappingService();

    @Test
    void shouldMapEmptySchema() {
        SchemaMappingResult mappingResult = SchemaMappingResult.builder()
                .databaseProductName("H2")
                .catalog("testdb")
                .build();

        AdministeredItemMappingResult result = service.mapToEntities(mappingResult);

        assertThat(result.getObjectClasses()).isEmpty();
        assertThat(result.getDataElementConcepts()).isEmpty();
        assertThat(result.getDataTypes()).isEmpty();
        assertThat(result.getValueDomains()).isEmpty();
        assertThat(result.getDataElements()).isEmpty();
    }

    @Test
    void shouldMapTableToObjectClassAndDec() {
        SchemaMappingResult mappingResult = SchemaMappingResult.builder()
                .databaseProductName("PostgreSQL")
                .catalog("testdb")
                .dataElementConcepts(List.of(
                        MappedDataElementConcept.builder()
                                .name("patients")
                                .description("Patient demographic info")
                                .tableName("patients")
                                .objectClassName("patients")
                                .propertyName("record")
                                .build()
                ))
                .dataElements(List.of(
                        MappedDataElement.builder()
                                .name("patient_id")
                                .description("Unique patient identifier")
                                .tableName("patients")
                                .columnName("patient_id")
                                .nullable(false)
                                .primaryKey(true)
                                .dataElementConcept(MappedDataElementConcept.builder()
                                        .name("patients")
                                        .tableName("patients")
                                        .build())
                                .valueDomain(MappedValueDomain.builder()
                                        .name("String_50_VD")
                                        .dataTypeName("String")
                                        .maximumCharacterQuantity(50)
                                        .build())
                                .build()
                ))
                .valueDomains(List.of(
                        MappedValueDomain.builder()
                                .name("String_50_VD")
                                .dataTypeName("String")
                                .maximumCharacterQuantity(50)
                                .build()
                ))
                .build();

        AdministeredItemMappingResult result = service.mapToEntities(mappingResult);

        assertThat(result.getObjectClasses()).hasSize(1);
        ObjectClass oc = result.getObjectClasses().get(0);
        assertThat(oc.getDesignations()).hasSize(1);
        assertThat(oc.getDesignations().get(0).toString()).isEqualTo("patients");

        assertThat(result.getProperties()).hasSize(1);
        Property property = result.getProperties().get(0);
        assertThat(property.getDesignations().get(0).toString()).isEqualTo("record");

        assertThat(result.getDataElementConcepts()).hasSize(1);
        DataElementConcept dec = result.getDataElementConcepts().get(0);
        assertThat(dec.getObjectClass()).isEqualTo(oc);
        assertThat(dec.getProperty()).isEqualTo(property);
        assertThat(dec.getDesignations().get(0).toString()).isEqualTo("patients");

        assertThat(result.getDataTypes()).hasSize(1);
        DataType dt = result.getDataTypes().get(0);
        assertThat(dt.getDesignations().get(0).toString()).isEqualTo("String");

        assertThat(result.getValueDomains()).hasSize(1);
        DescribedValueDomain vd = result.getValueDomains().get(0);
        assertThat(vd.getDataType()).isEqualTo(dt);
        assertThat(vd.getMaximumCharacterQuantity()).isEqualTo(50);

        assertThat(result.getDataElements()).hasSize(1);
        DataElement de = result.getDataElements().get(0);
        assertThat(de.getDataElementConcept()).isEqualTo(dec);
        assertThat(de.getValueDomain()).isEqualTo(vd);
        assertThat(de.getDesignations().get(0).toString()).isEqualTo("patient_id");
    }

    @Test
    void shouldReuseDataTypeForSameTypeName() {
        SchemaMappingResult mappingResult = SchemaMappingResult.builder()
                .databaseProductName("MySQL")
                .catalog("testdb")
                .dataElementConcepts(List.of(
                        MappedDataElementConcept.builder()
                                .name("users")
                                .tableName("users")
                                .objectClassName("users")
                                .propertyName("record")
                                .build()
                ))
                .dataElements(List.of(
                        MappedDataElement.builder()
                                .name("email")
                                .tableName("users")
                                .columnName("email")
                                .nullable(false)
                                .valueDomain(MappedValueDomain.builder()
                                        .name("String_255_VD")
                                        .dataTypeName("String")
                                        .maximumCharacterQuantity(255)
                                        .build())
                                .build(),
                        MappedDataElement.builder()
                                .name("name")
                                .tableName("users")
                                .columnName("name")
                                .nullable(true)
                                .valueDomain(MappedValueDomain.builder()
                                        .name("String_255_VD")
                                        .dataTypeName("String")
                                        .maximumCharacterQuantity(255)
                                        .build())
                                .build()
                ))
                .valueDomains(List.of(
                        MappedValueDomain.builder()
                                .name("String_255_VD")
                                .dataTypeName("String")
                                .maximumCharacterQuantity(255)
                                .build()
                ))
                .build();

        AdministeredItemMappingResult result = service.mapToEntities(mappingResult);

        // Two columns but same data type, so only 1 DataType entity
        assertThat(result.getDataTypes()).hasSize(1);
        // But 2 separate ValueDomain entities (since mapping service may not deduplicate at entity level)
        assertThat(result.getValueDomains()).hasSize(2);
        assertThat(result.getDataElements()).hasSize(2);
    }

    @Test
    void shouldMapMultipleTables() {
        SchemaMappingResult mappingResult = SchemaMappingResult.builder()
                .databaseProductName("SQL Server")
                .catalog("testdb")
                .dataElementConcepts(List.of(
                        MappedDataElementConcept.builder()
                                .name("patients")
                                .tableName("patients")
                                .objectClassName("patients")
                                .propertyName("record")
                                .build(),
                        MappedDataElementConcept.builder()
                                .name("orders")
                                .tableName("orders")
                                .objectClassName("orders")
                                .propertyName("record")
                                .build()
                ))
                .dataElements(List.of(
                        MappedDataElement.builder()
                                .name("patient_id")
                                .tableName("patients")
                                .columnName("patient_id")
                                .valueDomain(MappedValueDomain.builder()
                                        .name("Integer_VD")
                                        .dataTypeName("Integer")
                                        .build())
                                .build(),
                        MappedDataElement.builder()
                                .name("order_id")
                                .tableName("orders")
                                .columnName("order_id")
                                .valueDomain(MappedValueDomain.builder()
                                        .name("Integer_VD")
                                        .dataTypeName("Integer")
                                        .build())
                                .build()
                ))
                .valueDomains(List.of(
                        MappedValueDomain.builder()
                                .name("Integer_VD")
                                .dataTypeName("Integer")
                                .build()
                ))
                .build();

        AdministeredItemMappingResult result = service.mapToEntities(mappingResult);

        assertThat(result.getObjectClasses()).hasSize(2);
        assertThat(result.getProperties()).hasSize(2);
        assertThat(result.getDataElementConcepts()).hasSize(2);
        assertThat(result.getDataTypes()).hasSize(1);
        assertThat(result.getValueDomains()).hasSize(2);
        assertThat(result.getDataElements()).hasSize(2);
    }
}
