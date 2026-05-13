package io.github.matrixlane.metadataregistry.schemaextraction.connection;

import lombok.Builder;
import lombok.Data;

import java.util.Properties;

/**
 * Configuration for a database connection.
 */
@Data
@Builder(toBuilder = true)
public class DatabaseConnectionConfig {

    private String connectionName;
    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
    @Builder.Default
    private int connectionTimeout = 3000;
    @Builder.Default
    private int maximumPoolSize = 5;
    @Builder.Default
    private int minimumIdle = 1;
    @Builder.Default
    private long idleTimeout = 600000;
    @Builder.Default
    private long maxLifetime = 1800000;
    @Builder.Default
    private Properties additionalProperties = new Properties();

    // SSL/TLS configuration
    @Builder.Default
    private boolean sslEnabled = false;
    private String sslMode;
    private String trustStorePath;
    private String trustStorePassword;
    private String keyStorePath;
    private String keyStorePassword;

    /**
     * Validates that required fields are present.
     */
    public void validate() {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("JDBC URL is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (password == null) {
            throw new IllegalArgumentException("Password is required (may be empty)");
        }
    }
}
