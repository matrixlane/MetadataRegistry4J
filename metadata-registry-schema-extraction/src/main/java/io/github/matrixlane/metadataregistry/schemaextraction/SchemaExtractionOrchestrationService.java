package io.github.matrixlane.metadataregistry.schemaextraction;

import io.github.matrixlane.metadataregistry.schemaextraction.audit.SchemaExtractionAuditEvent;
import io.github.matrixlane.metadataregistry.schemaextraction.audit.SchemaExtractionAuditLogger;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.DatabaseConnectionConfig;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.DatabaseConnectionManager;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.SchemaMappingResult;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.SchemaMappingService;
import io.github.matrixlane.metadataregistry.schemaextraction.model.DatabaseSchema;
import io.github.matrixlane.metadataregistry.schemaextraction.strategy.SchemaExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Orchestrates the complete schema extraction workflow:
 * connection → extraction → mapping → audit logging.
 */
public class SchemaExtractionOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(SchemaExtractionOrchestrationService.class);

    private final DatabaseConnectionManager connectionManager;
    private final SchemaExtractor schemaExtractor;
    private final SchemaMappingService schemaMappingService;
    private final SchemaExtractionAuditLogger auditLogger;

    public SchemaExtractionOrchestrationService(DatabaseConnectionManager connectionManager,
                                                  SchemaExtractor schemaExtractor,
                                                  SchemaMappingService schemaMappingService,
                                                  SchemaExtractionAuditLogger auditLogger) {
        this.connectionManager = connectionManager;
        this.schemaExtractor = schemaExtractor;
        this.schemaMappingService = schemaMappingService;
        this.auditLogger = auditLogger;
    }

    /**
     * Extracts schema from a registered connection, maps it to administered items,
     * and logs the operation.
     *
     * @param connectionName the registered connection name
     * @return schema mapping result
     */
    public SchemaMappingResult extractAndMap(String connectionName) {
        long startTime = System.currentTimeMillis();
        String jdbcUrl = null;
        String databaseType = null;
        int tableCount = 0;
        String status = "SUCCESS";
        String details = null;

        try {
            DatabaseConnectionConfig config = getConnectionConfig(connectionName);
            jdbcUrl = maskUrl(config.getJdbcUrl());

            log.info("Starting schema extraction for connection '{}'", connectionName);

            Connection connection = null;
            try {
                connection = connectionManager.getConnection(connectionName);
                try {
                    databaseType = connection.getMetaData().getDatabaseProductName();
                } catch (SQLException e) {
                    databaseType = "unknown";
                }
                DatabaseSchema schema = schemaExtractor.extract(connection);
                tableCount = schema.getTables().size();
                log.info("Extracted {} tables from connection '{}'", tableCount, connectionName);

                SchemaMappingResult result = schemaMappingService.mapSchema(schema);
                details = String.format("Extracted %d tables, mapped to %d DECs, %d DEs, %d VDs",
                        tableCount,
                        result.getDataElementConcepts().size(),
                        result.getDataElements().size(),
                        result.getValueDomains().size());

                return result;
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        log.warn("Failed to close connection: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            status = "FAILURE";
            details = e.getMessage();
            log.error("Schema extraction failed for connection '{}': {}", connectionName, e.getMessage(), e);
            throw e;
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            auditLogger.log(SchemaExtractionAuditEvent.builder()
                    .timestamp(Instant.now())
                    .connectionName(connectionName)
                    .jdbcUrl(jdbcUrl)
                    .databaseType(databaseType)
                    .operation("EXTRACT_AND_MAP")
                    .status(status)
                    .details(details)
                    .tableCount(tableCount)
                    .durationMs(durationMs)
                    .build());
        }
    }

    /**
     * Extracts schema from a registered connection with explicit catalog/schema.
     */
    public SchemaMappingResult extractAndMap(String connectionName, String catalog, String schemaName) {
        long startTime = System.currentTimeMillis();
        String jdbcUrl = null;
        String databaseType = null;
        int tableCount = 0;
        String status = "SUCCESS";
        String details = null;

        try {
            DatabaseConnectionConfig config = getConnectionConfig(connectionName);
            jdbcUrl = maskUrl(config.getJdbcUrl());

            log.info("Starting schema extraction for connection '{}', catalog='{}', schema='{}'",
                    connectionName, catalog, schemaName);

            Connection connection = null;
            try {
                connection = connectionManager.getConnection(connectionName);
                try {
                    databaseType = connection.getMetaData().getDatabaseProductName();
                } catch (SQLException e) {
                    databaseType = "unknown";
                }
                DatabaseSchema schema = schemaExtractor.extract(connection, catalog, schemaName);
                tableCount = schema.getTables().size();
                log.info("Extracted {} tables from connection '{}'", tableCount, connectionName);

                SchemaMappingResult result = schemaMappingService.mapSchema(schema);
                details = String.format("Extracted %d tables, mapped to %d DECs, %d DEs, %d VDs",
                        tableCount,
                        result.getDataElementConcepts().size(),
                        result.getDataElements().size(),
                        result.getValueDomains().size());

                return result;
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        log.warn("Failed to close connection: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            status = "FAILURE";
            details = e.getMessage();
            log.error("Schema extraction failed for connection '{}': {}", connectionName, e.getMessage(), e);
            throw e;
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            auditLogger.log(SchemaExtractionAuditEvent.builder()
                    .timestamp(Instant.now())
                    .connectionName(connectionName)
                    .jdbcUrl(jdbcUrl)
                    .databaseType(databaseType)
                    .operation("EXTRACT_AND_MAP")
                    .status(status)
                    .details(details)
                    .tableCount(tableCount)
                    .durationMs(durationMs)
                    .build());
        }
    }

    private DatabaseConnectionConfig getConnectionConfig(String connectionName) {
        // The connection manager doesn't expose config, so we use the connection name
        // In a real implementation, this would be retrieved from a config store
        return DatabaseConnectionConfig.builder()
                .connectionName(connectionName)
                .jdbcUrl("jdbc:unknown://localhost")
                .username("unknown")
                .password("unknown")
                .build();
    }

    private String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("(?i)(password=)[^;]*", "$1***");
    }
}
