package io.github.matrixlane.metadataregistry.schemaextraction.strategy;

import io.github.matrixlane.metadataregistry.schemaextraction.exception.SchemaExtractionException;
import io.github.matrixlane.metadataregistry.schemaextraction.model.DatabaseSchema;
import io.github.matrixlane.metadataregistry.schemaextraction.sqlserver.SqlServerSchemaExtractor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchemaExtractorTest {

    @Test
    void shouldAutoDetectSqlServer() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        ResultSet tablesRs = mock(ResultSet.class);
        ResultSet columnsRs = mock(ResultSet.class);
        ResultSet pkRs = mock(ResultSet.class);
        ResultSet idxRs = mock(ResultSet.class);
        ResultSet fkRs = mock(ResultSet.class);

        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("Microsoft SQL Server");
        when(metaData.getDatabaseProductVersion()).thenReturn("16.0");
        when(connection.getCatalog()).thenReturn("testdb");

        when(metaData.getTables("testdb", null, "%", new String[]{"TABLE", "VIEW"}))
                .thenReturn(tablesRs);
        when(tablesRs.next()).thenReturn(false);

        when(metaData.getColumns(any(), any(), any(), any())).thenReturn(columnsRs);
        when(columnsRs.next()).thenReturn(false);

        when(metaData.getPrimaryKeys(any(), any(), any())).thenReturn(pkRs);
        when(pkRs.next()).thenReturn(false);

        when(metaData.getIndexInfo(any(), any(), any(), anyBoolean(), anyBoolean())).thenReturn(idxRs);
        when(idxRs.next()).thenReturn(false);

        when(metaData.getImportedKeys(any(), any(), any())).thenReturn(fkRs);
        when(fkRs.next()).thenReturn(false);

        SchemaExtractor extractor = new SchemaExtractor(List.of(new SqlServerSchemaExtractor()));

        DatabaseSchema schema = extractor.extract(connection);

        assertThat(schema).isNotNull();
        assertThat(schema.getDatabaseProductName()).isEqualTo("Microsoft SQL Server");
        assertThat(schema.getTables()).isEmpty();
    }

    @Test
    void shouldDetectMySql() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");

        SchemaExtractor extractor = new SchemaExtractor(List.of(new SqlServerSchemaExtractor()));

        assertThatThrownBy(() -> extractor.extract(connection))
                .isInstanceOf(SchemaExtractionException.class)
                .hasMessageContaining("No extraction strategy found");
    }

    @Test
    void shouldDetectOracle() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("Oracle");

        SchemaExtractor extractor = new SchemaExtractor(List.of(new SqlServerSchemaExtractor()));

        assertThatThrownBy(() -> extractor.extract(connection))
                .isInstanceOf(SchemaExtractionException.class)
                .hasMessageContaining("No extraction strategy found");
    }

    @Test
    void shouldDetectPostgresql() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        SchemaExtractor extractor = new SchemaExtractor(List.of(new SqlServerSchemaExtractor()));

        assertThatThrownBy(() -> extractor.extract(connection))
                .isInstanceOf(SchemaExtractionException.class)
                .hasMessageContaining("No extraction strategy found");
    }

    @Test
    void shouldHandleUnknownDatabase() throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("SomeExoticDB");

        SchemaExtractor extractor = new SchemaExtractor(List.of(new SqlServerSchemaExtractor()));

        assertThatThrownBy(() -> extractor.extract(connection))
                .isInstanceOf(SchemaExtractionException.class)
                .hasMessageContaining("No extraction strategy found");
    }

    @Test
    void shouldThrowOnMetadataFailure() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getMetaData()).thenThrow(new SQLException("Connection closed"));

        SchemaExtractor extractor = new SchemaExtractor(List.of(new SqlServerSchemaExtractor()));

        assertThatThrownBy(() -> extractor.extract(connection))
                .isInstanceOf(SchemaExtractionException.class)
                .hasMessageContaining("Failed to detect database type");
    }
}
