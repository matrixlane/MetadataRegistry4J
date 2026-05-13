package io.github.matrixlane.metadataregistry.schemaextraction.connection;

import java.util.List;
import java.util.Optional;

/**
 * Store for persisting and retrieving database connection configurations
 * with encrypted sensitive fields.
 */
public interface DatabaseConnectionConfigStore {

    /**
     * Saves a connection configuration. Encrypts the password before storage.
     */
    void save(DatabaseConnectionConfig config);

    /**
     * Retrieves a connection configuration by name. Decrypts the password.
     */
    Optional<DatabaseConnectionConfig> findByName(String connectionName);

    /**
     * Lists all stored connection configurations.
     */
    List<DatabaseConnectionConfig> findAll();

    /**
     * Removes a connection configuration by name.
     */
    void deleteByName(String connectionName);

    /**
     * Returns true if a configuration with the given name exists.
     */
    boolean exists(String connectionName);
}
