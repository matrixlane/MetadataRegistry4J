package io.github.matrixlane.metadataregistry.schemaextraction.connection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DatabaseConnectionConfigTest {

    @Test
    void shouldBuildValidConfig() {
        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .connectionName("test-conn")
                .jdbcUrl("jdbc:sqlserver://localhost:1433;databaseName=test")
                .username("sa")
                .password("secret")
                .driverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver")
                .build();

        assertThat(config.getConnectionName()).isEqualTo("test-conn");
        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:sqlserver://localhost:1433;databaseName=test");
        assertThat(config.getUsername()).isEqualTo("sa");
        assertThat(config.getPassword()).isEqualTo("secret");
        assertThat(config.getDriverClassName()).isEqualTo("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        assertThat(config.getConnectionTimeout()).isEqualTo(3000);
        assertThat(config.getMaximumPoolSize()).isEqualTo(5);
    }

    @Test
    void shouldValidateMissingUrl() {
        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .username("sa")
                .password("secret")
                .build();

        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JDBC URL is required");
    }

    @Test
    void shouldValidateMissingUsername() {
        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .jdbcUrl("jdbc:sqlserver://localhost")
                .password("secret")
                .build();

        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is required");
    }

    @Test
    void shouldValidateMissingPassword() {
        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .jdbcUrl("jdbc:sqlserver://localhost")
                .username("sa")
                .build();

        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password is required");
    }

    @Test
    void shouldAcceptEmptyPassword() {
        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .jdbcUrl("jdbc:sqlserver://localhost")
                .username("sa")
                .password("")
                .build();

        assertThatNoException().isThrownBy(config::validate);
    }

    @Test
    void shouldAllowCustomPoolSettings() {
        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .jdbcUrl("jdbc:sqlserver://localhost")
                .username("sa")
                .password("secret")
                .maximumPoolSize(20)
                .minimumIdle(5)
                .connectionTimeout(5000)
                .build();

        assertThat(config.getMaximumPoolSize()).isEqualTo(20);
        assertThat(config.getMinimumIdle()).isEqualTo(5);
        assertThat(config.getConnectionTimeout()).isEqualTo(5000);
    }
}
