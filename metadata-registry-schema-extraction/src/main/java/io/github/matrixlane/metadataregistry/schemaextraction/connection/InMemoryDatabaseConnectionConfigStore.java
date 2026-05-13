package io.github.matrixlane.metadataregistry.schemaextraction.connection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link DatabaseConnectionConfigStore}.
 * Suitable for development and single-node deployments.
 * Passwords are stored in encrypted form.
 */
public class InMemoryDatabaseConnectionConfigStore implements DatabaseConnectionConfigStore {

    private final Map<String, DatabaseConnectionConfig> configs = new ConcurrentHashMap<>();
    private final ConnectionEncryption encryption;

    public InMemoryDatabaseConnectionConfigStore(ConnectionEncryption encryption) {
        this.encryption = encryption;
    }

    @Override
    public void save(DatabaseConnectionConfig config) {
        DatabaseConnectionConfig encrypted = config.toBuilder()
                .password(encryption.encrypt(config.getPassword()))
                .build();
        configs.put(config.getConnectionName(), encrypted);
    }

    @Override
    public Optional<DatabaseConnectionConfig> findByName(String connectionName) {
        return Optional.ofNullable(configs.get(connectionName))
                .map(this::decryptConfig);
    }

    @Override
    public List<DatabaseConnectionConfig> findAll() {
        return configs.values().stream()
                .map(this::decryptConfig)
                .toList();
    }

    @Override
    public void deleteByName(String connectionName) {
        configs.remove(connectionName);
    }

    @Override
    public boolean exists(String connectionName) {
        return configs.containsKey(connectionName);
    }

    private DatabaseConnectionConfig decryptConfig(DatabaseConnectionConfig config) {
        return config.toBuilder()
                .password(encryption.decrypt(config.getPassword()))
                .build();
    }
}
