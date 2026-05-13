package io.github.matrixlane.metadataregistry.schemaextraction;

import io.github.matrixlane.metadataregistry.schemaextraction.audit.SchemaExtractionAuditEvent;
import io.github.matrixlane.metadataregistry.schemaextraction.audit.SchemaExtractionAuditLogger;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.ConnectionEncryption;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.DatabaseConnectionConfig;
import io.github.matrixlane.metadataregistry.schemaextraction.connection.DatabaseConnectionManager;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.SchemaMappingResult;
import io.github.matrixlane.metadataregistry.schemaextraction.mapping.SchemaMappingService;
import io.github.matrixlane.metadataregistry.schemaextraction.model.DatabaseSchema;
import io.github.matrixlane.metadataregistry.schemaextraction.strategy.SchemaExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SchemaExtractionOrchestrationServiceTest {

    private static final String MASTER_KEY = "test-master-key-12345678901234567890";

    private DatabaseConnectionManager connectionManager;
    private SchemaExtractor schemaExtractor;
    private SchemaMappingService schemaMappingService;
    private SchemaExtractionAuditLogger auditLogger;
    private SchemaExtractionOrchestrationService orchestrationService;
    private List<SchemaExtractionAuditEvent> capturedEvents;

    @BeforeEach
    void setUp() {
        ConnectionEncryption encryption = new ConnectionEncryption(MASTER_KEY);
        connectionManager = new DatabaseConnectionManager(encryption);
        schemaExtractor = mock(SchemaExtractor.class);
        schemaMappingService = new SchemaMappingService();
        auditLogger = new SchemaExtractionAuditLogger();
        capturedEvents = new ArrayList<>();
        auditLogger.addHandler(capturedEvents::add);

        orchestrationService = new SchemaExtractionOrchestrationService(
                connectionManager, schemaExtractor, schemaMappingService, auditLogger);

        // Register a test connection
        DatabaseConnectionConfig config = DatabaseConnectionConfig.builder()
                .connectionName("test-conn")
                .jdbcUrl("jdbc:h2:mem:orchestrationdb")
                .username("sa")
                .password(encryption.encrypt(""))
                .driverClassName("org.h2.Driver")
                .maximumPoolSize(1)
                .minimumIdle(0)
                .build();
        connectionManager.registerConnection(config);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        connectionManager.close();
    }

    @Test
    void shouldExtractAndMapWithAuditEvent() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet tablesRs = createResultSet(Collections.emptyList());

        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");

        // We need to use the real connection manager but mock the extractor
        // Actually, the orchestration service gets connection from the manager
        // Let's mock the connection manager instead for this test
        DatabaseConnectionManager mockManager = mock(DatabaseConnectionManager.class);
        when(mockManager.getConnection("test-conn")).thenReturn(connection);

        SchemaExtractor realExtractor = SchemaExtractionFactory.createSchemaExtractor();

        SchemaExtractionOrchestrationService service = new SchemaExtractionOrchestrationService(
                mockManager, realExtractor, schemaMappingService, auditLogger);

        DatabaseSchema schema = DatabaseSchema.builder()
                .databaseProductName("H2")
                .catalog("test")
                .build();

        when(schemaExtractor.extract(connection)).thenReturn(schema);

        // Re-mock with mocked extractor
        service = new SchemaExtractionOrchestrationService(
                mockManager, schemaExtractor, schemaMappingService, auditLogger);
        when(schemaExtractor.extract(connection)).thenReturn(schema);

        SchemaMappingResult result = service.extractAndMap("test-conn");

        assertThat(result).isNotNull();
        assertThat(capturedEvents).hasSize(1);
        SchemaExtractionAuditEvent event = capturedEvents.get(0);
        assertThat(event.getConnectionName()).isEqualTo("test-conn");
        assertThat(event.getOperation()).isEqualTo("EXTRACT_AND_MAP");
        assertThat(event.getStatus()).isEqualTo("SUCCESS");
        assertThat(event.getDatabaseType()).isEqualTo("H2");
        assertThat(event.getDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldLogFailureAuditEventOnException() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("H2");

        DatabaseConnectionManager mockManager = mock(DatabaseConnectionManager.class);
        when(mockManager.getConnection("bad-conn")).thenReturn(connection);

        SchemaExtractor mockExtractor = mock(SchemaExtractor.class);
        when(mockExtractor.extract(connection)).thenThrow(new RuntimeException("Extraction failed"));

        SchemaExtractionOrchestrationService service = new SchemaExtractionOrchestrationService(
                mockManager, mockExtractor, schemaMappingService, auditLogger);

        assertThatThrownBy(() -> service.extractAndMap("bad-conn"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Extraction failed");

        assertThat(capturedEvents).hasSize(1);
        SchemaExtractionAuditEvent event = capturedEvents.get(0);
        assertThat(event.getConnectionName()).isEqualTo("bad-conn");
        assertThat(event.getStatus()).isEqualTo("FAILURE");
        assertThat(event.getDetails()).contains("Extraction failed");
    }

    @Test
    void shouldExtractWithCatalogAndSchema() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        DatabaseConnectionManager mockManager = mock(DatabaseConnectionManager.class);
        when(mockManager.getConnection("pg-conn")).thenReturn(connection);

        SchemaExtractor mockExtractor = mock(SchemaExtractor.class);
        DatabaseSchema schema = DatabaseSchema.builder()
                .databaseProductName("PostgreSQL")
                .catalog("mydb")
                .schema("public")
                .build();
        when(mockExtractor.extract(connection, "mydb", "public")).thenReturn(schema);

        SchemaExtractionOrchestrationService service = new SchemaExtractionOrchestrationService(
                mockManager, mockExtractor, schemaMappingService, auditLogger);

        SchemaMappingResult result = service.extractAndMap("pg-conn", "mydb", "public");

        assertThat(result).isNotNull();
        verify(mockExtractor).extract(connection, "mydb", "public");

        assertThat(capturedEvents).hasSize(1);
        assertThat(capturedEvents.get(0).getStatus()).isEqualTo("SUCCESS");
    }

    @SuppressWarnings("unchecked")
    private ResultSet createResultSet(List<Map<String, Object>> rows) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        if (rows.isEmpty()) {
            when(rs.next()).thenReturn(false);
            return rs;
        }
        final int[] currentIndex = {-1};
        when(rs.next()).thenAnswer((InvocationOnMock invocation) -> {
            currentIndex[0]++;
            return currentIndex[0] < rows.size();
        });
        when(rs.getString(anyString())).thenAnswer((InvocationOnMock invocation) -> {
            String column = invocation.getArgument(0);
            Map<String, Object> row = rows.get(currentIndex[0]);
            Object value = row.get(column);
            return value != null ? value.toString() : null;
        });
        return rs;
    }
}
