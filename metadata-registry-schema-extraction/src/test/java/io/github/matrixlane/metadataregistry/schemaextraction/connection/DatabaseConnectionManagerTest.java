package io.github.matrixlane.metadataregistry.schemaextraction.connection;

import io.github.matrixlane.metadataregistry.schemaextraction.exception.ConnectionException;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseConnectionManagerTest {

    private static final String MASTER_KEY = "test-master-key-12345678901234567890";

    @Test
    void shouldEncryptPasswordWhenCreatingDataSource() {
        ConnectionEncryption encryption = new ConnectionEncryption(MASTER_KEY);
        String encryptedPassword = encryption.encrypt("my-password");

        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .connectionName("test")
                .jdbcUrl("jdbc:h2:mem:testdb")
                .username("sa")
                .password(encryptedPassword)
                .driverClassName("org.h2.Driver")
                .maximumPoolSize(1)
                .minimumIdle(0)
                .build();

        DatabaseConnectionManager manager = new DatabaseConnectionManager(encryption);

        assertThatNoException().isThrownBy(() -> manager.registerConnection(config));
        assertThat(manager.hasConnection("test")).isTrue();

        manager.close();
    }

    @Test
    void shouldPreventDuplicateConnectionNames() {
        ConnectionEncryption encryption = mock(ConnectionEncryption.class);
        when(encryption.decrypt(any())).thenReturn("password");

        DatabaseConnectionManager manager = new DatabaseConnectionManager(encryption);

        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .connectionName("dup")
                .jdbcUrl("jdbc:h2:mem:testdb1")
                .username("sa")
                .password("p")
                .maximumPoolSize(1)
                .minimumIdle(0)
                .build();

        manager.registerConnection(config);

        assertThatThrownBy(() -> manager.registerConnection(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        manager.close();
    }

    @Test
    void shouldThrowWhenConnectionNotFound() {
        DatabaseConnectionManager manager = new DatabaseConnectionManager(MASTER_KEY);

        assertThatThrownBy(() -> manager.getConnection("nonexistent"))
                .isInstanceOf(ConnectionException.class)
                .hasMessageContaining("not found");

        assertThatThrownBy(() -> manager.getDataSource("nonexistent"))
                .isInstanceOf(ConnectionException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void shouldTestConnectionSuccessfully() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        when(mockConnection.isValid(3)).thenReturn(true);

        DataSource mockDataSource = mock(DataSource.class);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);

        // We can't easily mock the internal DataSource map, so test via real H2
        ConnectionEncryption encryption = new ConnectionEncryption(MASTER_KEY);
        DatabaseConnectionManager manager = new DatabaseConnectionManager(encryption);

        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .connectionName("h2-test")
                .jdbcUrl("jdbc:h2:mem:testdb2")
                .username("sa")
                .password(encryption.encrypt(""))
                .driverClassName("org.h2.Driver")
                .maximumPoolSize(1)
                .minimumIdle(0)
                .build();

        manager.registerConnection(config);

        assertThat(manager.testConnection("h2-test")).isTrue();

        manager.close();
    }

    @Test
    void shouldRemoveConnection() {
        ConnectionEncryption encryption = new ConnectionEncryption(MASTER_KEY);
        DatabaseConnectionManager manager = new DatabaseConnectionManager(encryption);

        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .connectionName("removable")
                .jdbcUrl("jdbc:h2:mem:testdb3")
                .username("sa")
                .password(encryption.encrypt(""))
                .driverClassName("org.h2.Driver")
                .maximumPoolSize(1)
                .minimumIdle(0)
                .build();

        manager.registerConnection(config);
        assertThat(manager.hasConnection("removable")).isTrue();

        manager.removeConnection("removable");
        assertThat(manager.hasConnection("removable")).isFalse();

        manager.close();
    }

    @Test
    void shouldCloseAllConnections() {
        ConnectionEncryption encryption = new ConnectionEncryption(MASTER_KEY);
        DatabaseConnectionManager manager = new DatabaseConnectionManager(encryption);

        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .connectionName("c1")
                .jdbcUrl("jdbc:h2:mem:testdb4")
                .username("sa")
                .password(encryption.encrypt(""))
                .driverClassName("org.h2.Driver")
                .maximumPoolSize(1)
                .minimumIdle(0)
                .build();

        manager.registerConnection(config);
        manager.close();

        assertThat(manager.hasConnection("c1")).isFalse();
    }
}
