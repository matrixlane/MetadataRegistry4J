package io.github.matrixlane.metadataregistry.web.dto.schemaextraction;

import jakarta.validation.constraints.NotBlank;

import java.util.Properties;

/**
 * Request to register a new database connection for schema extraction.
 */
public record DatabaseConnectionRequest(
        @NotBlank(message = "connectionName 不能为空")
        String connectionName,
        @NotBlank(message = "jdbcUrl 不能为空")
        String jdbcUrl,
        @NotBlank(message = "username 不能为空")
        String username,
        @NotBlank(message = "password 不能为空")
        String password,
        String driverClassName,
        Integer connectionTimeout,
        Integer maximumPoolSize,
        Integer minimumIdle,
        Long idleTimeout,
        Long maxLifetime,
        Properties additionalProperties
) {
}
