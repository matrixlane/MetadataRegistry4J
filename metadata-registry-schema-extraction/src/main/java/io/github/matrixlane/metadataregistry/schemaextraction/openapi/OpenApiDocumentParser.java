package io.github.matrixlane.metadataregistry.schemaextraction.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Parses OpenAPI 3.0 and Swagger 2.0 documents from various sources.
 * Supports JSON and YAML formats, file uploads, URLs, and raw strings.
 * Enforces a maximum document size of 10MB.
 */
public class OpenApiDocumentParser {

    private static final Logger log = LoggerFactory.getLogger(OpenApiDocumentParser.class);
    private static final long MAX_DOCUMENT_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;
    private final HttpClient httpClient;

    public OpenApiDocumentParser() {
        this.jsonMapper = new ObjectMapper();
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Parses an OpenAPI document from a file path.
     *
     * @param filePath path to the document file
     * @return parsed OpenAPI model
     * @throws OpenApiExtractionException if parsing fails or document exceeds size limit
     */
    public OpenAPI parseFromFile(Path filePath) {
        try {
            long size = Files.size(filePath);
            if (size > MAX_DOCUMENT_SIZE_BYTES) {
                throw new OpenApiExtractionException(
                        "Document size " + size + " bytes exceeds maximum allowed " + MAX_DOCUMENT_SIZE_BYTES + " bytes");
            }
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            return parseFromString(content);
        } catch (IOException e) {
            throw new OpenApiExtractionException("Failed to read document from file: " + filePath, e);
        }
    }

    /**
     * Parses an OpenAPI document from a URL.
     *
     * @param url URL pointing to the document
     * @return parsed OpenAPI model
     * @throws OpenApiExtractionException if fetching or parsing fails
     */
    public OpenAPI parseFromUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json, application/x-yaml, text/yaml, */*")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new OpenApiExtractionException(
                        "Failed to fetch document from URL: HTTP " + response.statusCode());
            }

            byte[] body = response.body();
            if (body.length > MAX_DOCUMENT_SIZE_BYTES) {
                throw new OpenApiExtractionException(
                        "Document size " + body.length + " bytes exceeds maximum allowed " + MAX_DOCUMENT_SIZE_BYTES + " bytes");
            }

            String content = new String(body, StandardCharsets.UTF_8);
            return parseFromString(content);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenApiExtractionException("Request interrupted while fetching document from URL: " + url, e);
        } catch (IOException e) {
            throw new OpenApiExtractionException("Failed to fetch document from URL: " + url, e);
        }
    }

    /**
     * Parses an OpenAPI document from a raw string.
     * Automatically detects JSON vs YAML format.
     *
     * @param content the document content
     * @return parsed OpenAPI model
     * @throws OpenApiExtractionException if parsing fails
     */
    public OpenAPI parseFromString(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new OpenApiExtractionException("Document content is empty");
        }

        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_SIZE_BYTES) {
            throw new OpenApiExtractionException("Document content exceeds maximum size of 10MB");
        }

        ParseOptions parseOptions = new ParseOptions();
        parseOptions.setResolve(true);
        parseOptions.setResolveFully(false);

        // Try OpenAPI 3.0 parser first
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(content, null, parseOptions);

        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            for (String msg : result.getMessages()) {
                log.warn("OpenAPI parse message: {}", msg);
            }
        }

        // Fall back to SwaggerConverter for Swagger 2.0 documents
        if (result.getOpenAPI() == null) {
            log.info("OpenAPI 3.0 parser failed, trying Swagger 2.0 converter");
            io.swagger.v3.parser.converter.SwaggerConverter converter =
                    new io.swagger.v3.parser.converter.SwaggerConverter();
            result = converter.readContents(content, null, parseOptions);

            if (result.getMessages() != null && !result.getMessages().isEmpty()) {
                for (String msg : result.getMessages()) {
                    log.warn("SwaggerConverter parse message: {}", msg);
                }
            }
        }

        if (result.getOpenAPI() == null) {
            throw new OpenApiExtractionException(
                    "Failed to parse OpenAPI document. Errors: " + String.join(", ", result.getMessages()));
        }

        log.info("Successfully parsed OpenAPI document: version={}, title={}",
                result.getOpenAPI().getOpenapi(),
                result.getOpenAPI().getInfo() != null ? result.getOpenAPI().getInfo().getTitle() : "N/A");

        return result.getOpenAPI();
    }

    /**
     * Detects if the content is JSON format.
     */
    private boolean detectJson(String trimmed) {
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    /**
     * Validates that the content is valid JSON or YAML without fully parsing as OpenAPI.
     * Useful for quick validation before full parsing.
     */
    public boolean isValidFormat(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        String trimmed = content.trim();
        boolean isJson = detectJson(trimmed);
        ObjectMapper mapper = isJson ? jsonMapper : yamlMapper;
        try {
            JsonNode node = mapper.readTree(content);
            return node != null && !node.isMissingNode();
        } catch (IOException e) {
            return false;
        }
    }
}
