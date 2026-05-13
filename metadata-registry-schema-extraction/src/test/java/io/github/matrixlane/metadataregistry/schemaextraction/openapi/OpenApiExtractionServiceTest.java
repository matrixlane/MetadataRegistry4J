package io.github.matrixlane.metadataregistry.schemaextraction.openapi;

import io.github.matrixlane.metadataregistry.schemaextraction.openapi.model.OpenApiExtractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiExtractionServiceTest {

    private OpenApiExtractionService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new OpenApiExtractionService();
    }

    @Test
    void shouldExtractFromFile() throws IOException {
        // Given
        Path file = tempDir.resolve("petstore.json");
        String content = loadResource("openapi/petstore-openapi3.json");
        Files.writeString(file, content, StandardCharsets.UTF_8);

        // When
        OpenApiExtractionResult result = service.extractFromFile(file);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDocumentTitle()).isEqualTo("Swagger Petstore");
        assertThat(result.getTotalSchemas()).isEqualTo(3);
        assertThat(result.getParseTimeMs()).isGreaterThanOrEqualTo(0);

        // Check Pet schema
        var petSchema = result.getSchemas().stream()
                .filter(s -> s.getName().equals("Pet"))
                .findFirst()
                .orElseThrow();
        assertThat(petSchema.getProperties()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(petSchema.getRequiredFields()).contains("name");

        // Check enum mapping
        var statusProp = petSchema.getProperties().stream()
                .filter(p -> p.getName().equals("status"))
                .findFirst()
                .orElseThrow();
        assertThat(statusProp.getEnumValues()).containsExactly("available", "pending", "sold");

        // Check reference mapping
        var categoryProp = petSchema.getProperties().stream()
                .filter(p -> p.getName().equals("category"))
                .findFirst()
                .orElseThrow();
        assertThat(categoryProp.getReference()).isEqualTo("#/components/schemas/Category");
        assertThat(categoryProp.isNestedObject()).isTrue();

        // Check array mapping
        var photoUrlsProp = petSchema.getProperties().stream()
                .filter(p -> p.getName().equals("photoUrls"))
                .findFirst()
                .orElseThrow();
        assertThat(photoUrlsProp.isArray()).isTrue();
    }

    @Test
    void shouldExtractFromSwagger2Yaml() throws IOException {
        // Given
        Path file = tempDir.resolve("swagger2.yaml");
        String content = loadResource("openapi/petstore-swagger2.yaml");
        Files.writeString(file, content, StandardCharsets.UTF_8);

        // When
        OpenApiExtractionResult result = service.extractFromFile(file);

        // Then
        assertThat(result.getDocumentTitle()).isEqualTo("Swagger Petstore Legacy");
        assertThat(result.getTotalSchemas()).isGreaterThanOrEqualTo(2);

        // Check Pet schema from Swagger 2.0
        var petSchema = result.getSchemas().stream()
                .filter(s -> s.getName().equals("Pet"))
                .findFirst()
                .orElseThrow();
        assertThat(petSchema.getProperties()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void shouldExtractFromString() {
        // Given
        String content = """
            {
              "openapi": "3.0.0",
              "info": { "title": "String Test", "version": "1.0.0" },
              "paths": {},
              "components": {
                "schemas": {
                  "User": {
                    "type": "object",
                    "properties": {
                      "id": { "type": "integer" },
                      "name": { "type": "string" }
                    }
                  }
                }
              }
            }
            """;

        // When
        OpenApiExtractionResult result = service.extractFromString(content);

        // Then
        assertThat(result.getDocumentTitle()).isEqualTo("String Test");
        assertThat(result.getTotalSchemas()).isEqualTo(1);
        assertThat(result.getTotalProperties()).isEqualTo(2);
    }

    @Test
    void shouldExtractFromUrlNotAvailable() {
        // Testing that the URL method exists and throws on bad URL
        assertThatThrownBy(() -> service.extractFromUrl("http://localhost:59999/nonexistent"))
                .isInstanceOf(OpenApiExtractionException.class);
    }

    @Test
    void shouldHandleComposedSchemas() throws IOException {
        // Given
        Path file = tempDir.resolve("composed.yaml");
        String content = loadResource("openapi/composed-schema.yaml");
        Files.writeString(file, content, StandardCharsets.UTF_8);

        // When
        OpenApiExtractionResult result = service.extractFromFile(file);

        // Then
        assertThat(result.getTotalSchemas()).isGreaterThanOrEqualTo(4);

        // allOf - Dog should inherit Animal properties
        var dogSchema = result.getSchemas().stream()
                .filter(s -> s.getName().equals("Dog"))
                .findFirst()
                .orElseThrow();
        assertThat(dogSchema.getProperties()).hasSizeGreaterThanOrEqualTo(2);

        // oneOf - Response should have composite property
        var responseSchema = result.getSchemas().stream()
                .filter(s -> s.getName().equals("Response"))
                .findFirst()
                .orElseThrow();
        assertThat(responseSchema.getProperties()).anyMatch(p -> "oneOf".equals(p.getCompositeType()));

        // anyOf - Mixed should have composite property
        var mixedSchema = result.getSchemas().stream()
                .filter(s -> s.getName().equals("Mixed"))
                .findFirst()
                .orElseThrow();
        assertThat(mixedSchema.getProperties()).anyMatch(p -> "anyOf".equals(p.getCompositeType()));
    }

    private String loadResource(String resourcePath) throws IOException {
        java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
}
