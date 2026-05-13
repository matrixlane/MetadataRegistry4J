package io.github.matrixlane.metadataregistry.schemaextraction.strategy;

import io.github.matrixlane.metadataregistry.schemaextraction.model.DatabaseSchema;
import io.github.matrixlane.metadataregistry.schemaextraction.exception.SchemaExtractionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * Main entry point for schema extraction. Delegates to appropriate strategy.
 */
public class SchemaExtractor {

    private static final Logger log = LoggerFactory.getLogger(SchemaExtractor.class);

    private final List<SchemaExtractionStrategy> strategies;

    public SchemaExtractor(List<SchemaExtractionStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    /**
     * Extracts schema from the given connection, auto-detecting database type.
     */
    public DatabaseSchema extract(Connection connection) {
        return extract(connection, null, null);
    }

    /**
     * Extracts schema from the given connection with explicit catalog/schema.
     */
    public DatabaseSchema extract(Connection connection, String catalog, String schema) {
        DatabaseType dbType = detectDatabaseType(connection);
        log.info("Detected database type: {}", dbType);

        SchemaExtractionStrategy strategy = strategies.stream()
                .filter(s -> s.supports(dbType))
                .findFirst()
                .orElseThrow(() -> new SchemaExtractionException(
                        "No extraction strategy found for database type: " + dbType));

        return strategy.extractSchema(connection, catalog, schema);
    }

    private DatabaseType detectDatabaseType(Connection connection) {
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData.getDatabaseProductName();
            if (productName == null) {
                return DatabaseType.UNKNOWN;
            }
            String lower = productName.toLowerCase();
            if (lower.contains("microsoft") || lower.contains("sql server")) {
                return DatabaseType.SQL_SERVER;
            }
            if (lower.contains("mysql")) {
                return DatabaseType.MYSQL;
            }
            if (lower.contains("oracle")) {
                return DatabaseType.ORACLE;
            }
            if (lower.contains("postgresql") || lower.contains("postgres")) {
                return DatabaseType.POSTGRESQL;
            }
            return DatabaseType.UNKNOWN;
        } catch (SQLException e) {
            throw new SchemaExtractionException("Failed to detect database type", e);
        }
    }
}
