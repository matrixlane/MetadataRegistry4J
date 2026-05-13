package io.github.matrixlane.metadataregistry.web.dto.schemaextraction;

import java.util.Properties;

/**
 * Response for a registered database connection (password masked).
 */
public record DatabaseConnectionResponse(
        String connectionName,
        String jdbcUrl,
        String username,
        String driverClassName,
        int connectionTimeout,
        int maximumPoolSize,
        int minimumIdle,
        long idleTimeout,
        long maxLifetime,
        Properties additionalProperties,
        boolean connected
) {
}
