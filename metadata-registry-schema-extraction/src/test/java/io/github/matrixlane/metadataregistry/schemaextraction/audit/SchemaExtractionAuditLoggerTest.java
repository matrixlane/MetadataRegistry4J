package io.github.matrixlane.metadataregistry.schemaextraction.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SchemaExtractionAuditLoggerTest {

    @Test
    void shouldNotifyAllHandlers() {
        SchemaExtractionAuditLogger logger = new SchemaExtractionAuditLogger();
        List<SchemaExtractionAuditEvent> captured = new ArrayList<>();

        logger.addHandler(captured::add);
        logger.addHandler(captured::add);

        SchemaExtractionAuditEvent event = SchemaExtractionAuditEvent.builder()
                .timestamp(Instant.now())
                .connectionName("test-conn")
                .operation("EXTRACT")
                .status("SUCCESS")
                .build();

        logger.log(event);

        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).getConnectionName()).isEqualTo("test-conn");
        assertThat(captured.get(1).getConnectionName()).isEqualTo("test-conn");
    }

    @Test
    void shouldWorkWithNoHandlers() {
        SchemaExtractionAuditLogger logger = new SchemaExtractionAuditLogger();

        SchemaExtractionAuditEvent event = SchemaExtractionAuditEvent.builder()
                .timestamp(Instant.now())
                .operation("EXTRACT")
                .status("SUCCESS")
                .build();

        // Should not throw
        assertThatNoException().isThrownBy(() -> logger.log(event));
    }
}
