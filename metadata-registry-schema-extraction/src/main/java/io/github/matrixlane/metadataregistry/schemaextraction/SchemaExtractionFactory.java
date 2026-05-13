package io.github.matrixlane.metadataregistry.schemaextraction;

import io.github.matrixlane.metadataregistry.schemaextraction.connection.ConnectionEncryption;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.DatabaseConnectionManager;
import io.github.matrixlane.metadataregistry.schemaextraction.mysql.MySqlSchemaExtractor;
import io.github.matrixlane.metadataregistry.schemaextraction.oracle.OracleSchemaExtractor;
import io.github.matrixlane.metadataregistry.schemaextraction.postgresql.PostgreSqlSchemaExtractor;
import io.github.matrixlane.metadataregistry.schemaextraction.sqlserver.SqlServerSchemaExtractor;
import io.github.matrixlane.metadataregistry.schemaextraction.strategy.SchemaExtractor;

import java.util.List;

/**
 * Factory for creating pre-configured schema extraction components.
 */
public final class SchemaExtractionFactory {

    private SchemaExtractionFactory() {
        // Utility class
    }

    /**
     * Creates a SchemaExtractor with all supported strategies.
     */
    public static SchemaExtractor createSchemaExtractor() {
        return new SchemaExtractor(List.of(
                new SqlServerSchemaExtractor(),
                new MySqlSchemaExtractor(),
                new PostgreSqlSchemaExtractor(),
                new OracleSchemaExtractor()
        ));
    }

    /**
     * Creates a ConnectionEncryption with the given master key.
     */
    public static ConnectionEncryption createEncryption(String masterKey) {
        return new ConnectionEncryption(masterKey);
    }

    /**
     * Creates a DatabaseConnectionManager with the given master key.
     */
    public static DatabaseConnectionManager createConnectionManager(String masterKey) {
        return new DatabaseConnectionManager(masterKey);
    }
}
