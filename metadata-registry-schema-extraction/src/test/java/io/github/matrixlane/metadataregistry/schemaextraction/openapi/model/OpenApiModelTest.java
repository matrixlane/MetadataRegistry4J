package io.github.matrixlane.metadataregistry.schemaextraction.openapi.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiModelTest {

    @Test
    void shouldBuildPropertyInfo() {
        OpenApiPropertyInfo prop = OpenApiPropertyInfo.builder()
                .name("testField")
                .description("A test field")
                .openApiType("string")
                .suggestedDataType("StringType")
                .required(true)
                .array(false)
                .enumValues(List.of("a", "b"))
                .minLength(1)
                .maxLength(50)
                .pattern("^[a-z]+$")
                .build();

        assertThat(prop.getName()).isEqualTo("testField");
        assertThat(prop.getDescription()).isEqualTo("A test field");
        assertThat(prop.getOpenApiType()).isEqualTo("string");
        assertThat(prop.getSuggestedDataType()).isEqualTo("StringType");
        assertThat(prop.isRequired()).isTrue();
        assertThat(prop.isArray()).isFalse();
        assertThat(prop.getEnumValues()).containsExactly("a", "b");
        assertThat(prop.getMinLength()).isEqualTo(1);
        assertThat(prop.getMaxLength()).isEqualTo(50);
        assertThat(prop.getPattern()).isEqualTo("^[a-z]+$");
        assertThat(prop.getWarnings()).isEmpty();
    }

    @Test
    void shouldBuildPropertyInfoWithWarnings() {
        OpenApiPropertyInfo prop = OpenApiPropertyInfo.builder()
                .name("warned")
                .warnings(List.of("Warning 1", "Warning 2"))
                .build();

        assertThat(prop.getWarnings()).containsExactly("Warning 1", "Warning 2");
    }

    @Test
    void shouldBuildSchemaInfo() {
        OpenApiPropertyInfo prop = OpenApiPropertyInfo.builder()
                .name("id")
                .openApiType("integer")
                .build();

        OpenApiSchemaInfo schema = OpenApiSchemaInfo.builder()
                .name("User")
                .description("User model")
                .type("object")
                .properties(List.of(prop))
                .requiredFields(List.of("id"))
                .suggestedObjectClassName("User")
                .build();

        assertThat(schema.getName()).isEqualTo("User");
        assertThat(schema.getDescription()).isEqualTo("User model");
        assertThat(schema.getType()).isEqualTo("object");
        assertThat(schema.getProperties()).hasSize(1);
        assertThat(schema.getRequiredFields()).containsExactly("id");
        assertThat(schema.getSuggestedObjectClassName()).isEqualTo("User");
        assertThat(schema.getPropertyCount()).isZero(); // builder default, not auto-calculated
    }

    @Test
    void shouldBuildExtractionResult() {
        OpenApiSchemaInfo schema = OpenApiSchemaInfo.builder()
                .name("Test")
                .build();

        OpenApiExtractionResult result = OpenApiExtractionResult.builder()
                .openApiVersion("3.0.0")
                .documentTitle("Test API")
                .documentVersion("1.0.0")
                .schemas(List.of(schema))
                .totalSchemas(1)
                .totalProperties(5)
                .totalEnumValues(3)
                .parseTimeMs(100)
                .extractionTimeMs(200)
                .build();

        assertThat(result.getOpenApiVersion()).isEqualTo("3.0.0");
        assertThat(result.getDocumentTitle()).isEqualTo("Test API");
        assertThat(result.getTotalSchemas()).isEqualTo(1);
        assertThat(result.getTotalProperties()).isEqualTo(5);
        assertThat(result.getTotalEnumValues()).isEqualTo(3);
        assertThat(result.getParseTimeMs()).isEqualTo(100);
        assertThat(result.getExtractionTimeMs()).isEqualTo(200);
    }

    @Test
    void shouldGenerateSummary() {
        OpenApiExtractionResult result = OpenApiExtractionResult.builder()
                .openApiVersion("3.0.0")
                .documentTitle("Summary API")
                .documentVersion("1.0.0")
                .totalSchemas(5)
                .totalProperties(20)
                .totalEnumValues(10)
                .warnings(List.of("Warning 1"))
                .unresolvedReferences(List.of("#/components/schemas/Missing"))
                .build();

        String summary = result.getSummary();

        assertThat(summary).contains("Summary API");
        assertThat(summary).contains("3.0.0");
        assertThat(summary).contains("Schemas extracted: 5");
        assertThat(summary).contains("Total properties: 20");
        assertThat(summary).contains("Total enum values: 10");
        assertThat(summary).contains("Warnings: 1");
        assertThat(summary).contains("Unresolved references: 1");
    }

    @Test
    void shouldGenerateSummaryWithoutTitle() {
        OpenApiExtractionResult result = OpenApiExtractionResult.builder()
                .openApiVersion("3.0.0")
                .totalSchemas(0)
                .totalProperties(0)
                .build();

        String summary = result.getSummary();

        assertThat(summary).contains("Untitled");
    }

    @Test
    void shouldSupportToBuilder() {
        OpenApiPropertyInfo original = OpenApiPropertyInfo.builder()
                .name("original")
                .openApiType("string")
                .build();

        OpenApiPropertyInfo modified = original.toBuilder()
                .name("modified")
                .required(true)
                .build();

        assertThat(modified.getName()).isEqualTo("modified");
        assertThat(modified.getOpenApiType()).isEqualTo("string");
        assertThat(modified.isRequired()).isTrue();
    }
}
