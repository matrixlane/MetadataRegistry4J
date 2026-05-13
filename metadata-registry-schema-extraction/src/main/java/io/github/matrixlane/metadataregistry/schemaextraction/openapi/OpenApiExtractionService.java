package io.github.matrixlane.metadataregistry.schemaextraction.openapi;

import io.github.matrixlane.metadataregistry.schemaextraction.openapi.model.OpenApiExtractionResult;
import io.swagger.v3.oas.models.OpenAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Facade service that orchestrates OpenAPI document parsing and schema extraction.
 * Provides a single entry point for extracting metadata from OpenAPI documents.
 */
public class OpenApiExtractionService {

    private static final Logger log = LoggerFactory.getLogger(OpenApiExtractionService.class);

    private final OpenApiDocumentParser parser;
    private final OpenApiSchemaExtractor extractor;

    public OpenApiExtractionService() {
        this.parser = new OpenApiDocumentParser();
        this.extractor = new OpenApiSchemaExtractor();
    }

    public OpenApiExtractionService(OpenApiDocumentParser parser, OpenApiSchemaExtractor extractor) {
        this.parser = parser;
        this.extractor = extractor;
    }

    /**
     * Extracts metadata from an OpenAPI document file.
     *
     * @param filePath path to the OpenAPI document
     * @return extraction result
     */
    public OpenApiExtractionResult extractFromFile(Path filePath) {
        log.info("Extracting from file: {}", filePath);
        long start = System.currentTimeMillis();
        OpenAPI openAPI = parser.parseFromFile(filePath);
        OpenApiExtractionResult result = extractor.extract(openAPI);
        long total = System.currentTimeMillis() - start;
        log.info("Extraction completed in {}ms: {} schemas, {} properties", total,
                result.getTotalSchemas(), result.getTotalProperties());
        return result.toBuilder().parseTimeMs(total).build();
    }

    /**
     * Extracts metadata from an OpenAPI document URL.
     *
     * @param url URL of the OpenAPI document
     * @return extraction result
     */
    public OpenApiExtractionResult extractFromUrl(String url) {
        log.info("Extracting from URL: {}", url);
        long start = System.currentTimeMillis();
        OpenAPI openAPI = parser.parseFromUrl(url);
        OpenApiExtractionResult result = extractor.extract(openAPI);
        long total = System.currentTimeMillis() - start;
        log.info("Extraction completed in {}ms: {} schemas, {} properties", total,
                result.getTotalSchemas(), result.getTotalProperties());
        return result.toBuilder().parseTimeMs(total).sourceUrl(url).build();
    }

    /**
     * Extracts metadata from an OpenAPI document string.
     *
     * @param content the document content
     * @return extraction result
     */
    public OpenApiExtractionResult extractFromString(String content) {
        log.info("Extracting from string content ({} chars)", content.length());
        long start = System.currentTimeMillis();
        OpenAPI openAPI = parser.parseFromString(content);
        OpenApiExtractionResult result = extractor.extract(openAPI);
        long total = System.currentTimeMillis() - start;
        log.info("Extraction completed in {}ms: {} schemas, {} properties", total,
                result.getTotalSchemas(), result.getTotalProperties());
        return result.toBuilder().parseTimeMs(total).build();
    }
}
