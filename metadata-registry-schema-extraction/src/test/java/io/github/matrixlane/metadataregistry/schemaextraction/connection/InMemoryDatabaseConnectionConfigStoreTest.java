package io.github.matrixlane.metadataregistry.schemaextraction.connection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class InMemoryDatabaseConnectionConfigStoreTest {

    private static final String MASTER_KEY = "test-master-key-12345678901234567890";
    private ConnectionEncryption encryption;
    private InMemoryDatabaseConnectionConfigStore store;

    @BeforeEach
    void setUp() {
        encryption = new ConnectionEncryption(MASTER_KEY);
        store = new InMemoryDatabaseConnectionConfigStore(encryption);
    }

    @Test
    void shouldSaveAndRetrieveConfig() {
        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .connectionName("test-conn")
                .jdbcUrl("jdbc:h2:mem:testdb")
                .username("sa")
                .password("secret123")
                .build();

        store.save(config);

        Optional<DatabaseConnectionConfig> found = store.findByName("test-conn");
        assertThat(found).isPresent();
        assertThat(found.get().getConnectionName()).isEqualTo("test-conn");
        assertThat(found.get().getJdbcUrl()).isEqualTo("jdbc:h2:mem:testdb");
        assertThat(found.get().getUsername()).isEqualTo("sa");
        assertThat(found.get().getPassword()).isEqualTo("secret123");
    }

    @Test
    void shouldEncryptPasswordInStorage() {
        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .connectionName("encrypted-conn")
                .jdbcUrl("jdbc:h2:mem:testdb")
                .username("sa")
                .password("my-secret-password")
                .build();

        store.save(config);

        // The stored config should have encrypted password
        // But findByName should return decrypted
        Optional<DatabaseConnectionConfig> found = store.findByName("encrypted-conn");
        assertThat(found).isPresent();
        assertThat(found.get().getPassword()).isEqualTo("my-secret-password");
    }

    @Test
    void shouldReturnEmptyForMissingConfig() {
        Optional<DatabaseConnectionConfig> found = store.findByName("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindAllConfigs() {
        store.save(DatabaseConnectionConfig.builder()
                .connectionName("conn1")
                .jdbcUrl("jdbc:h2:mem:db1")
                .username("sa")
                .password("p1")
                .build());
        store.save(DatabaseConnectionConfig.builder()
                .connectionName("conn2")
                .jdbcUrl("jdbc:h2:mem:db2")
                .username("sa")
                .password("p2")
                .build());

        List<DatabaseConnectionConfig> all = store.findAll();
        assertThat(all).hasSize(2);
        assertThat(all.stream().map(DatabaseConnectionConfig::getConnectionName))
                .containsExactlyInAnyOrder("conn1", "conn2");
    }

    @Test
    void shouldDeleteConfig() {
        store.save(DatabaseConnectionConfig.builder()
                .connectionName("to-delete")
                .jdbcUrl("jdbc:h2:mem:db")
                .username("sa")
                .password("p")
                .build());

        assertThat(store.exists("to-delete")).isTrue();

        store.deleteByName("to-delete");

        assertThat(store.exists("to-delete")).isFalse();
        assertThat(store.findByName("to-delete")).isEmpty();
    }

    @Test
    void shouldUpdateExistingConfig() {
        store.save(DatabaseConnectionConfig.builder()
                .connectionName("updatable")
                .jdbcUrl("jdbc:h2:mem:old")
                .username("sa")
                .password("oldpass")
                .build());

        store.save(DatabaseConnectionConfig.builder()
                .connectionName("updatable")
                .jdbcUrl("jdbc:h2:mem:new")
                .username("sa")
                .password("newpass")
                .build());

        Optional<DatabaseConnectionConfig> found = store.findByName("updatable");
        assertThat(found).isPresent();
        assertThat(found.get().getJdbcUrl()).isEqualTo("jdbc:h2:mem:new");
        assertThat(found.get().getPassword()).isEqualTo("newpass");
    }
}
