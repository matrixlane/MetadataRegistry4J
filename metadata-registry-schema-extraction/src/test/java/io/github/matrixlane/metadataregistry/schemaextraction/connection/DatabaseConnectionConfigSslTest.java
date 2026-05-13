package io.github.matrixlane.metadataregistry.schemaextraction.connection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DatabaseConnectionConfigSslTest {

    @Test
    void shouldBuildConfigWithSslEnabled() {
        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .connectionName("ssl-conn")
                .jdbcUrl("jdbc:postgresql://localhost:5432/testdb")
                .username("user")
                .password("secret")
                .sslEnabled(true)
                .sslMode("verify-full")
                .trustStorePath("/path/to/truststore.jks")
                .trustStorePassword("trustpass")
                .keyStorePath("/path/to/keystore.jks")
                .keyStorePassword("keypass")
                .build();

        assertThat(config.isSslEnabled()).isTrue();
        assertThat(config.getSslMode()).isEqualTo("verify-full");
        assertThat(config.getTrustStorePath()).isEqualTo("/path/to/truststore.jks");
        assertThat(config.getTrustStorePassword()).isEqualTo("trustpass");
        assertThat(config.getKeyStorePath()).isEqualTo("/path/to/keystore.jks");
        assertThat(config.getKeyStorePassword()).isEqualTo("keypass");
    }

    @Test
    void shouldDefaultSslToDisabled() {
        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .connectionName("plain-conn")
                .jdbcUrl("jdbc:sqlserver://localhost:1433;databaseName=test")
                .username("sa")
                .password("secret")
                .build();

        assertThat(config.isSslEnabled()).isFalse();
        assertThat(config.getSslMode()).isNull();
    }

    @Test
    void shouldCopyConfigWithToBuilder() {
        DatabaseConnectionConfig original = DatabaseConnectionConfig.builder()
                .connectionName("original")
                .jdbcUrl("jdbc:mysql://localhost:3306/test")
                .username("root")
                .password("secret")
                .build();

        DatabaseConnectionConfig copy = original.toBuilder()
                .sslEnabled(true)
                .sslMode("required")
                .build();

        assertThat(copy.getConnectionName()).isEqualTo("original");
        assertThat(copy.getJdbcUrl()).isEqualTo("jdbc:mysql://localhost:3306/test");
        assertThat(copy.isSslEnabled()).isTrue();
        assertThat(copy.getSslMode()).isEqualTo("required");
    }
}
