package io.github.matrixlane.metadataregistry.schemaextraction;

import io.github.matrixlane.metadataregistry.schemaextraction.connection.ConnectionEncryption;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.DatabaseConnectionManager;
import io.github.matrixlane.metadataregistry.schemaextraction.strategy.SchemaExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SchemaExtractionFactoryTest {

    @Test
    void shouldCreateSchemaExtractor() {
        SchemaExtractor extractor = SchemaExtractionFactory.createSchemaExtractor();
        assertThat(extractor).isNotNull();
    }

    @Test
    void shouldCreateEncryption() {
        ConnectionEncryption encryption = SchemaExtractionFactory.createEncryption("test-key-12345678901234567890");
        assertThat(encryption).isNotNull();

        String ciphertext = encryption.encrypt("hello");
        assertThat(encryption.decrypt(ciphertext)).isEqualTo("hello");
    }

    @Test
    void shouldCreateConnectionManager() {
        DatabaseConnectionManager manager = SchemaExtractionFactory.createConnectionManager("test-key-12345678901234567890");
        assertThat(manager).isNotNull();
        manager.close();
    }
}
