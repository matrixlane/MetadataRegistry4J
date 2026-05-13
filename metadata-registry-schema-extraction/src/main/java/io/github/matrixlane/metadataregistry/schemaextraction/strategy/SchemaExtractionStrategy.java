package io.github.matrixlane.metadataregistry.schemaextraction.strategy;

import io.github.matrixlane.metadataregistry.schemaextraction.model.DatabaseSchema;

import java.sql.Connection;

/**
 * Strategy interface for database schema extraction.
 */
public interface SchemaExtractionStrategy {

    /**
     * Returns true if this strategy supports the given database type.
     */
    boolean supports(DatabaseType databaseType);

    /**
     * Extracts the complete schema from the given connection.
     *
     * @param connection active JDBC connection
     * @param catalog    catalog name (may be null)
     * @param schema     schema name (may be null)
     * @return complete database schema
     */
    DatabaseSchema extractSchema(Connection connection, String catalog, String schema);

    /**
     * Extracts the complete schema using the default catalog/schema.
     */
    default DatabaseSchema extractSchema(Connection connection) {
        return extractSchema(connection, null, null);
    }
}
