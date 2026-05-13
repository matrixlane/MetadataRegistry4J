package io.github.matrixlane.metadataregistry.schemaextraction.audit;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Audit event for schema extraction operations.
 */
@Data
@Builder
public class SchemaExtractionAuditEvent {

    private Instant timestamp;
    private String connectionName;
    private String jdbcUrl;
    private String databaseType;
    private String operation;
    private String status;
    private String details;
    private String username;
    private int tableCount;
    private long durationMs;
}
