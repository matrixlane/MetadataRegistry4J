package io.github.matrixlane.metadataregistry.schemaextraction.connection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.matrixlane.metadataregistry.schemaextraction.exception.ConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages isolated HikariCP connection pools for legacy database connections.
 */
public class DatabaseConnectionManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionManager.class);

    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final ConnectionEncryption encryption;

    public DatabaseConnectionManager(String masterKey) {
        this.encryption = new ConnectionEncryption(masterKey);
    }

    public DatabaseConnectionManager(ConnectionEncryption encryption) {
        this.encryption = encryption;
    }

    /**
     * Registers a new database connection configuration and creates a connection pool.
     */
    public void registerConnection(DatabaseConnectionConfig config) {
        config.validate();
        String name = config.getConnectionName();

        if (dataSources.containsKey(name)) {
            throw new IllegalArgumentException("Connection '" + name + "' already registered");
        }

        HikariDataSource dataSource = createDataSource(config);
        dataSources.put(name, dataSource);
        log.info("Registered connection pool '{}' with URL: {}", name, maskUrl(config.getJdbcUrl()));
    }

    /**
     * Tests connectivity for a registered connection.
     *
     * @return true if connection is valid
     */
    public boolean testConnection(String connectionName) {
        DataSource dataSource = getDataSource(connectionName);
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(3);
        } catch (SQLException e) {
            log.warn("Connection test failed for '{}': {}", connectionName, e.getMessage());
            return false;
        }
    }

    /**
     * Gets a connection from the named pool.
     */
    public Connection getConnection(String connectionName) {
        DataSource dataSource = getDataSource(connectionName);
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new ConnectionException("Failed to obtain connection from pool '" + connectionName + "'", e);
        }
    }

    /**
     * Gets the DataSource for a named connection.
     */
    public DataSource getDataSource(String connectionName) {
        HikariDataSource dataSource = dataSources.get(connectionName);
        if (dataSource == null) {
            throw new ConnectionException("Connection '" + connectionName + "' not found");
        }
        return dataSource;
    }

    /**
     * Removes a connection and closes its pool.
     */
    public void removeConnection(String connectionName) {
        HikariDataSource dataSource = dataSources.remove(connectionName);
        if (dataSource != null) {
            dataSource.close();
            log.info("Closed connection pool '{}'", connectionName);
        }
    }

    /**
     * Returns true if the named connection is registered.
     */
    public boolean hasConnection(String connectionName) {
        return dataSources.containsKey(connectionName);
    }

    @Override
    public void close() {
        dataSources.forEach((name, ds) -> {
            ds.close();
            log.info("Closed connection pool '{}'", name);
        });
        dataSources.clear();
    }

    private HikariDataSource createDataSource(DatabaseConnectionConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(encryption.decrypt(config.getPassword()));

        if (config.getDriverClassName() != null && !config.getDriverClassName().isBlank()) {
            hikariConfig.setDriverClassName(config.getDriverClassName());
        }

        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
        hikariConfig.setMinimumIdle(config.getMinimumIdle());
        hikariConfig.setIdleTimeout(config.getIdleTimeout());
        hikariConfig.setMaxLifetime(config.getMaxLifetime());
        hikariConfig.setPoolName("SchemaExtraction-" + config.getConnectionName());

        config.getAdditionalProperties().forEach((key, value) -> {
            hikariConfig.addDataSourceProperty(String.valueOf(key), value);
        });

        return new HikariDataSource(hikariConfig);
    }

    private String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        // Mask password if present in URL
        return url.replaceAll("(?i)(password=)[^;]*", "$1***");
    }
}
