package io.github.matrixlane.metadataregistry.schemaextraction.openapi.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of extracting schemas from an OpenAPI document.
 * Provides a preview of extracted data models and their suggested mappings
 * to Administered Items.
 */
@Data
@Builder(toBuilder = true)
public class OpenApiExtractionResult {

    private String openApiVersion;
    private String documentTitle;
    private String documentVersion;
    private String documentDescription;
    private String sourceUrl;
    private boolean swagger2Converted;

    @Builder.Default
    private List<OpenApiSchemaInfo> schemas = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    private List<String> unresolvedReferences = new ArrayList<>();

    private int totalSchemas;
    private int totalProperties;
    private int totalEnumValues;
    private long parseTimeMs;
    private long extractionTimeMs;

    /**
     * Returns a summary of the extraction for preview purposes.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("OpenAPI Document: ").append(documentTitle != null ? documentTitle : "Untitled").append("\n");
        sb.append("Version: ").append(openApiVersion).append("\n");
        if (swagger2Converted) {
            sb.append("Note: Swagger 2.0 document was converted to OpenAPI 3.0\n");
        }
        sb.append("Schemas extracted: ").append(totalSchemas).append("\n");
        sb.append("Total properties: ").append(totalProperties).append("\n");
        sb.append("Total enum values: ").append(totalEnumValues).append("\n");
        if (!warnings.isEmpty()) {
            sb.append("Warnings: ").append(warnings.size()).append("\n");
        }
        if (!unresolvedReferences.isEmpty()) {
            sb.append("Unresolved references: ").append(unresolvedReferences.size()).append("\n");
        }
        return sb.toString();
    }
}
