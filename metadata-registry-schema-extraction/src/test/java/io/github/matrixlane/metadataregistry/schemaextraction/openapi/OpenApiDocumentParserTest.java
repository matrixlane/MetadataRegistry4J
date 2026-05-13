package io.github.matrixlane.metadataregistry.schemaextraction.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiDocumentParserTest {

    private OpenApiDocumentParser parser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new OpenApiDocumentParser();
    }

    @Test
    void shouldParseOpenApi3JsonFromFile() throws IOException {
        // Given
        Path file = tempDir.resolve("openapi.json");
        String content = """
            {
              "openapi": "3.0.0",
              "info": { "title": "Test API", "version": "1.0.0" },
              "paths": {},
              "components": {
                "schemas": {
                  "Pet": {
                    "type": "object",
                    "properties": {
                      "name": { "type": "string" }
                    }
                  }
                }
              }
            }
            """;
        Files.writeString(file, content, StandardCharsets.UTF_8);

        // When
        OpenAPI result = parser.parseFromFile(file);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOpenapi()).isEqualTo("3.0.0");
        assertThat(result.getInfo().getTitle()).isEqualTo("Test API");
        assertThat(result.getComponents().getSchemas()).containsKey("Pet");
    }

    @Test
    void shouldParseOpenApi3YamlFromFile() throws IOException {
        // Given
        Path file = tempDir.resolve("openapi.yaml");
        String content = """
            openapi: "3.0.0"
            info:
              title: "Test API YAML"
              version: "1.0.0"
            paths: {}
            components:
              schemas:
                User:
                  type: object
                  properties:
                    id:
                      type: integer
            """;
        Files.writeString(file, content, StandardCharsets.UTF_8);

        // When
        OpenAPI result = parser.parseFromFile(file);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getInfo().getTitle()).isEqualTo("Test API YAML");
        assertThat(result.getComponents().getSchemas()).containsKey("User");
    }

    @Test
    void shouldParseSwagger2JsonFromString() {
        // Given
        String content = """
            {
              "swagger": "2.0",
              "info": { "title": "Legacy API", "version": "1.0.0" },
              "paths": {},
              "definitions": {
                "OldModel": {
                  "type": "object",
                  "properties": {
                    "id": { "type": "integer" }
                  }
                }
              }
            }
            """;

        // When
        OpenAPI result = parser.parseFromString(content);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getInfo().getTitle()).isEqualTo("Legacy API");
    }

    @Test
    void shouldRejectEmptyContent() {
        assertThatThrownBy(() -> parser.parseFromString(""))
                .isInstanceOf(OpenApiExtractionException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void shouldRejectNullContent() {
        assertThatThrownBy(() -> parser.parseFromString(null))
                .isInstanceOf(OpenApiExtractionException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void shouldRejectInvalidJson() {
        assertThatThrownBy(() -> parser.parseFromString("{ invalid json"))
                .isInstanceOf(OpenApiExtractionException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void shouldDetectValidJsonFormat() {
        assertThat(parser.isValidFormat("{ \"openapi\": \"3.0.0\" }")).isTrue();
    }

    @Test
    void shouldDetectValidYamlFormat() {
        assertThat(parser.isValidFormat("openapi: \"3.0.0\"\ninfo:\n  title: Test")).isTrue();
    }

    @Test
    void shouldRejectInvalidFormat() {
        // A plain string is technically valid YAML scalar, so isValidFormat returns true.
        // Use an invalid structure instead.
        assertThat(parser.isValidFormat("{ broken json [")).isFalse();
    }

    @Test
    void shouldRejectEmptyFormat() {
        assertThat(parser.isValidFormat("")).isFalse();
    }

    @Test
    void shouldRejectNullFormat() {
        assertThat(parser.isValidFormat(null)).isFalse();
    }

    @Test
    void shouldRejectOversizedDocument() {
        // Create a string that is just over 10MB
        StringBuilder sb = new StringBuilder();
        sb.append("{ \"openapi\": \"3.0.0\", \"info\": { \"title\": \"X\", \"version\": \"1\" }, \"paths\": {}, \"components\": { \"schemas\": { \"Big\": { \"type\": \"object\", \"properties\": { \"data\": { \"type\": \"string\", \"description\": \"");
        // Add enough characters to exceed 10MB
        sb.append("x".repeat(11 * 1024 * 1024));
        sb.append("\" } } } } } }");

        assertThatThrownBy(() -> parser.parseFromString(sb.toString()))
                .isInstanceOf(OpenApiExtractionException.class)
                .hasMessageContaining("exceeds maximum");
    }
}
