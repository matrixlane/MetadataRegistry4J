package io.github.matrixlane.metadataregistry.schemaextraction.openapi;

import io.github.matrixlane.metadataregistry.schemaextraction.openapi.model.OpenApiExtractionResult;
import io.github.matrixlane.metadataregistry.schemaextraction.openapi.model.OpenApiPropertyInfo;
import io.github.matrixlane.metadataregistry.schemaextraction.openapi.model.OpenApiSchemaInfo;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiSchemaExtractorTest {

    private OpenApiSchemaExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new OpenApiSchemaExtractor();
    }

    @Test
    void shouldExtractBasicSchemas() {
        // Given
        OpenAPI openAPI = new OpenAPI()
                .openapi("3.0.0")
                .info(new Info().title("Test API").version("1.0.0"));

        Schema petSchema = new ObjectSchema()
                .description("A pet")
                .addProperty("name", new StringSchema().description("Pet name"))
                .addProperty("age", new IntegerSchema().format("int32"));
        petSchema.setRequired(List.of("name"));

        openAPI.setComponents(new Components().addSchemas("Pet", petSchema));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);

        // Then
        assertThat(result.getTotalSchemas()).isEqualTo(1);
        assertThat(result.getDocumentTitle()).isEqualTo("Test API");

        OpenApiSchemaInfo petInfo = result.getSchemas().get(0);
        assertThat(petInfo.getName()).isEqualTo("Pet");
        assertThat(petInfo.getDescription()).isEqualTo("A pet");
        assertThat(petInfo.getProperties()).hasSize(2);
        assertThat(petInfo.getSuggestedObjectClassName()).isEqualTo("Pet");
    }

    @Test
    void shouldMapPropertyTypesCorrectly() {
        // Given
        OpenAPI openAPI = new OpenAPI().openapi("3.0.0");

        Schema schema = new ObjectSchema()
                .addProperty("stringField", new StringSchema())
                .addProperty("intField", new IntegerSchema().format("int32"))
                .addProperty("longField", new IntegerSchema().format("int64"))
                .addProperty("floatField", new NumberSchema().format("float"))
                .addProperty("doubleField", new NumberSchema().format("double"))
                .addProperty("boolField", new BooleanSchema())
                .addProperty("dateField", new StringSchema().format("date"))
                .addProperty("dateTimeField", new StringSchema().format("date-time"))
                .addProperty("emailField", new StringSchema().format("email"));

        openAPI.setComponents(new Components().addSchemas("TypeTest", schema));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);

        // Then
        OpenApiSchemaInfo schemaInfo = result.getSchemas().get(0);
        Map<String, String> typeMap = schemaInfo.getProperties().stream()
                .collect(java.util.stream.Collectors.toMap(OpenApiPropertyInfo::getName, OpenApiPropertyInfo::getSuggestedDataType));

        assertThat(typeMap.get("stringField")).isEqualTo("StringType");
        assertThat(typeMap.get("intField")).isEqualTo("IntegerType");
        assertThat(typeMap.get("longField")).isEqualTo("IntegerType");
        assertThat(typeMap.get("floatField")).isEqualTo("IntegerType");
        assertThat(typeMap.get("doubleField")).isEqualTo("IntegerType");
        assertThat(typeMap.get("boolField")).isEqualTo("BooleanType");
        assertThat(typeMap.get("dateField")).isEqualTo("Date");
        assertThat(typeMap.get("dateTimeField")).isEqualTo("Datetime");
        assertThat(typeMap.get("emailField")).isEqualTo("EmailAddress");
    }

    @Test
    void shouldExtractEnumValues() {
        // Given
        OpenAPI openAPI = new OpenAPI().openapi("3.0.0");
        Schema schema = new ObjectSchema()
                .addProperty("status", new StringSchema()
                        .description("Status")
                        ._enum(List.of("active", "inactive", "pending")));

        openAPI.setComponents(new Components().addSchemas("StatusModel", schema));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);

        // Then
        OpenApiPropertyInfo statusProp = result.getSchemas().get(0).getProperties().get(0);
        assertThat(statusProp.getName()).isEqualTo("status");
        assertThat(statusProp.getEnumValues()).containsExactly("active", "inactive", "pending");
        assertThat(statusProp.getMappedPermissibleValues()).containsExactly("active", "inactive", "pending");
    }

    @Test
    void shouldHandleRequiredFields() {
        // Given
        OpenAPI openAPI = new OpenAPI().openapi("3.0.0");
        Schema schema = new ObjectSchema()
                .addProperty("id", new IntegerSchema())
                .addProperty("optionalField", new StringSchema());
        schema.setRequired(List.of("id"));

        openAPI.setComponents(new Components().addSchemas("RequiredTest", schema));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);

        // Then
        List<OpenApiPropertyInfo> props = result.getSchemas().get(0).getProperties();
        OpenApiPropertyInfo idProp = props.stream().filter(p -> p.getName().equals("id")).findFirst().orElseThrow();
        OpenApiPropertyInfo optProp = props.stream().filter(p -> p.getName().equals("optionalField")).findFirst().orElseThrow();

        assertThat(idProp.isRequired()).isTrue();
        assertThat(optProp.isRequired()).isFalse();
    }

    @Test
    void shouldHandleArrayProperties() {
        // Given
        OpenAPI openAPI = new OpenAPI().openapi("3.0.0");
        Schema schema = new ObjectSchema()
                .addProperty("tags", new ArraySchema().items(new StringSchema()))
                .addProperty("items", new ArraySchema().items(new Schema<>().$ref("#/components/schemas/Item")));

        openAPI.setComponents(new Components().addSchemas("ArrayTest", schema));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);

        // Then
        List<OpenApiPropertyInfo> props = result.getSchemas().get(0).getProperties();
        OpenApiPropertyInfo tagsProp = props.stream().filter(p -> p.getName().equals("tags")).findFirst().orElseThrow();
        OpenApiPropertyInfo itemsProp = props.stream().filter(p -> p.getName().equals("items")).findFirst().orElseThrow();

        assertThat(tagsProp.isArray()).isTrue();
        assertThat(tagsProp.getItemType()).isEqualTo("string");
        assertThat(tagsProp.getSuggestedDataType()).isEqualTo("StringType");

        assertThat(itemsProp.isArray()).isTrue();
        assertThat(itemsProp.getItemType()).isEqualTo("#/components/schemas/Item");
        assertThat(itemsProp.getSuggestedDataType()).isEqualTo("Array");
        assertThat(itemsProp.isNestedObject()).isTrue();
    }

    @Test
    void shouldHandleReferenceProperties() {
        // Given
        OpenAPI openAPI = new OpenAPI().openapi("3.0.0");
        Schema schema = new ObjectSchema()
                .addProperty("category", new Schema<>().$ref("#/components/schemas/Category"));

        openAPI.setComponents(new Components()
                .addSchemas("RefTest", schema)
                .addSchemas("Category", new ObjectSchema().addProperty("name", new StringSchema())));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);

        // Then
        OpenApiPropertyInfo catProp = result.getSchemas().get(0).getProperties().get(0);
        assertThat(catProp.getReference()).isEqualTo("#/components/schemas/Category");
        assertThat(catProp.isNestedObject()).isTrue();
        assertThat(catProp.getSuggestedDataType()).isEqualTo("Object");
    }

    @Test
    void shouldHandleAllOfComposedSchema() {
        // Given
        OpenAPI openAPI = new OpenAPI().openapi("3.0.0");

        Schema animal = new ObjectSchema()
                .addProperty("species", new StringSchema())
                .addProperty("legs", new IntegerSchema());

        ComposedSchema dog = new ComposedSchema();
        dog.addAllOfItem(new Schema<>().$ref("#/components/schemas/Animal"));
        dog.addAllOfItem(new ObjectSchema()
                .addProperty("breed", new StringSchema())
                .addProperty("friendly", new BooleanSchema()));

        openAPI.setComponents(new Components()
                .addSchemas("Animal", animal)
                .addSchemas("Dog", dog));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);

        // Then
        OpenApiSchemaInfo dogInfo = result.getSchemas().stream()
                .filter(s -> s.getName().equals("Dog"))
                .findFirst()
                .orElseThrow();

        // Should have properties from both Animal and the inline schema
        assertThat(dogInfo.getProperties()).hasSizeGreaterThanOrEqualTo(2);
        List<String> propNames = dogInfo.getProperties().stream()
                .map(OpenApiPropertyInfo::getName)
                .toList();
        assertThat(propNames).contains("breed", "friendly");
    }

    @Test
    void shouldHandleOneOfComposedSchema() {
        // Given
        OpenAPI openAPI = new OpenAPI().openapi("3.0.0");

        ComposedSchema response = new ComposedSchema();
        response.addOneOfItem(new Schema<>().$ref("#/components/schemas/Success"));
        response.addOneOfItem(new Schema<>().$ref("#/components/schemas/Error"));

        openAPI.setComponents(new Components()
                .addSchemas("Response", response)
                .addSchemas("Success", new ObjectSchema().addProperty("data", new StringSchema()))
                .addSchemas("Error", new ObjectSchema().addProperty("message", new StringSchema())));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);

        // Then
        OpenApiSchemaInfo responseInfo = result.getSchemas().stream()
                .filter(s -> s.getName().equals("Response"))
                .findFirst()
                .orElseThrow();

        OpenApiPropertyInfo oneOfProp = responseInfo.getProperties().stream()
                .filter(p -> p.getCompositeType() != null && p.getCompositeType().equals("oneOf"))
                .findFirst()
                .orElseThrow();

        assertThat(oneOfProp.getCompositeReferences()).contains("#/components/schemas/Success", "#/components/schemas/Error");
        assertThat(oneOfProp.getWarnings()).anyMatch(w -> w.contains("manual review"));
    }

    @Test
    void shouldExtractConstraints() {
        // Given
        OpenAPI openAPI = new OpenAPI().openapi("3.0.0");
        Schema schema = new ObjectSchema()
                .addProperty("code", new StringSchema()
                        .minLength(3)
                        .maxLength(10)
                        .pattern("^[A-Z]+$"))
                .addProperty("score", new NumberSchema().minimum(java.math.BigDecimal.valueOf(0)).maximum(java.math.BigDecimal.valueOf(100)));

        openAPI.setComponents(new Components().addSchemas("ConstraintTest", schema));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);

        // Then
        OpenApiPropertyInfo codeProp = result.getSchemas().get(0).getProperties().stream()
                .filter(p -> p.getName().equals("code"))
                .findFirst()
                .orElseThrow();

        assertThat(codeProp.getMinLength()).isEqualTo(3);
        assertThat(codeProp.getMaxLength()).isEqualTo(10);
        assertThat(codeProp.getPattern()).isEqualTo("^[A-Z]+$");

        OpenApiPropertyInfo scoreProp = result.getSchemas().get(0).getProperties().stream()
                .filter(p -> p.getName().equals("score"))
                .findFirst()
                .orElseThrow();

        assertThat(scoreProp.getMinimum()).isEqualTo(java.math.BigDecimal.valueOf(0));
        assertThat(scoreProp.getMaximum()).isEqualTo(java.math.BigDecimal.valueOf(100));
    }

    @Test
    void shouldHandleNestedInlineObject() {
        // Given
        OpenAPI openAPI = new OpenAPI().openapi("3.0.0");
        Schema schema = new ObjectSchema()
                .addProperty("address", new ObjectSchema()
                        .description("Inline address")
                        .addProperty("street", new StringSchema())
                        .addProperty("city", new StringSchema()));

        openAPI.setComponents(new Components().addSchemas("NestedTest", schema));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);

        // Then
        OpenApiPropertyInfo addressProp = result.getSchemas().get(0).getProperties().get(0);
        assertThat(addressProp.getName()).isEqualTo("address");
        assertThat(addressProp.isNestedObject()).isTrue();
        assertThat(addressProp.getWarnings()).anyMatch(w -> w.contains("Inline nested object"));
    }

    @Test
    void shouldGenerateSummary() {
        // Given
        OpenAPI openAPI = new OpenAPI().openapi("3.0.0")
                .info(new Info().title("Summary Test").version("1.0.0"));
        openAPI.setComponents(new Components().addSchemas("A", new ObjectSchema().addProperty("x", new StringSchema())));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);
        String summary = result.getSummary();

        // Then
        assertThat(summary).contains("Summary Test");
        assertThat(summary).contains("3.0.0");
        assertThat(summary).contains("Schemas extracted: 1");
        assertThat(summary).contains("Total properties: 1");
    }

    @Test
    void shouldWarnWhenNoSchemas() {
        // Given
        OpenAPI openAPI = new OpenAPI().openapi("3.0.0").info(new Info().title("Empty").version("1"));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);

        // Then
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("No schemas found"));
        assertThat(result.getTotalSchemas()).isZero();
    }

    @Test
    void shouldCountTotalEnumValues() {
        // Given
        OpenAPI openAPI = new OpenAPI().openapi("3.0.0");
        Schema schema = new ObjectSchema()
                .addProperty("status", new StringSchema()._enum(List.of("a", "b", "c")))
                .addProperty("priority", new StringSchema()._enum(List.of("low", "high")));

        openAPI.setComponents(new Components().addSchemas("EnumTest", schema));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);

        // Then
        assertThat(result.getTotalEnumValues()).isEqualTo(5);
    }

    @Test
    void shouldHandleNullPropertySchema() {
        // Given
        OpenAPI openAPI = new OpenAPI().openapi("3.0.0");
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("broken", null);

        openAPI.setComponents(new Components().addSchemas("NullTest", schema));

        // When
        OpenApiExtractionResult result = extractor.extract(openAPI);

        // Then
        OpenApiPropertyInfo brokenProp = result.getSchemas().get(0).getProperties().get(0);
        assertThat(brokenProp.getName()).isEqualTo("broken");
        assertThat(brokenProp.getSuggestedDataType()).isEqualTo("StringType");
        assertThat(brokenProp.getWarnings()).anyMatch(w -> w.contains("null"));
    }
}
