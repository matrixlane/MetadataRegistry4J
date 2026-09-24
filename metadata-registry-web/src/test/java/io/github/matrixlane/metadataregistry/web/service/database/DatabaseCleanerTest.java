package io.github.matrixlane.metadataregistry.web.service.database;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DatabaseCleaner} 单元测试:验证 DELETE / ALTER TABLE 按发现时的 schema 限定,
 * 而不是按硬编码表名清单猜测(回归:ext 孤儿表被限定到 mdr 导致恢复出厂设置 500)。
 */
@ExtendWith(MockitoExtension.class)
class DatabaseCleanerTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection conn;

    @Mock
    private DatabaseMetaData metaData;

    @Mock
    private ResultSet mdrTables;

    @Mock
    private ResultSet extTables;

    @Mock
    private Statement statement;

    private DatabaseEnvironment env;

    @BeforeEach
    void setUp() throws Exception {
        env = new DatabaseEnvironment("SQLSERVER", "", null, null);
        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(metaData);
        lenient().when(conn.getCatalog()).thenReturn("metadata_registry");
        lenient().when(conn.createStatement()).thenReturn(statement);
    }

    private void discoverMdr(String... names) throws Exception {
        when(metaData.getTables(eq("metadata_registry"), eq("mdr"), isNull(), eq(new String[]{"TABLE"})))
                .thenReturn(mdrTables);
        boolean[] more = new boolean[names.length + 1];
        for (int i = 0; i < names.length; i++) {
            more[i] = true;
        }
        org.mockito.stubbing.OngoingStubbing<Boolean> stubbing = when(mdrTables.next());
        for (boolean b : more) {
            stubbing = stubbing.thenReturn(b);
        }
        if (names.length > 0) {
            org.mockito.stubbing.OngoingStubbing<String> namesStubbing = when(mdrTables.getString("TABLE_NAME"));
            for (String name : names) {
                namesStubbing = namesStubbing.thenReturn(name);
            }
        }
    }

    private void discoverExt(String... names) throws Exception {
        when(metaData.getTables(eq("metadata_registry"), eq("ext"), isNull(), eq(new String[]{"TABLE"})))
                .thenReturn(extTables);
        boolean[] more = new boolean[names.length + 1];
        for (int i = 0; i < names.length; i++) {
            more[i] = true;
        }
        org.mockito.stubbing.OngoingStubbing<Boolean> stubbing = when(extTables.next());
        for (boolean b : more) {
            stubbing = stubbing.thenReturn(b);
        }
        if (names.length > 0) {
            org.mockito.stubbing.OngoingStubbing<String> namesStubbing = when(extTables.getString("TABLE_NAME"));
            for (String name : names) {
                namesStubbing = namesStubbing.thenReturn(name);
            }
        }
    }

    @Test
    @DisplayName("DELETE 按发现时的 schema 限定(mdr 表走 mdr,ext 表走 ext)")
    void deleteQualifiesWithDiscoveredSchema() throws Exception {
        discoverMdr("data_element", "flyway_schema_history");
        discoverExt("account", "item_classification");

        DatabaseCleaner cleaner = new DatabaseCleaner(dataSource, env, List.of());
        cleaner.clearTables(conn, Set.of("flyway_schema_history"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).executeUpdate(sql.capture());
        assertThat(sql.getAllValues()).containsExactlyInAnyOrder(
                "DELETE FROM \"mdr\".\"data_element\"",
                "DELETE FROM \"ext\".\"account\"",
                "DELETE FROM \"ext\".\"item_classification\"");
    }

    @Test
    @DisplayName("同名表在两个 schema 同时存在时各自删除")
    void deletesSameNamedTableFromBothSchemas() throws Exception {
        discoverMdr("sync_watermark");
        discoverExt("sync_watermark");

        DatabaseCleaner cleaner = new DatabaseCleaner(dataSource, env, List.of());
        cleaner.clearTables(conn, Set.of());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).executeUpdate(sql.capture());
        assertThat(sql.getAllValues()).containsExactlyInAnyOrder(
                "DELETE FROM \"mdr\".\"sync_watermark\"",
                "DELETE FROM \"ext\".\"sync_watermark\"");
    }

    @Test
    @DisplayName("SQL Server 外键策略按发现时的 schema 限定 ALTER TABLE")
    void sqlServerStrategyQualifiesWithDiscoveredSchema() throws Exception {
        discoverMdr("data_element");
        discoverExt("item_classification");

        DatabaseCleaner cleaner = new DatabaseCleaner(
                dataSource, env, List.of(new SqlServerFkConstraintStrategy(env)));
        cleaner.clearTables(conn, Set.of());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).execute(sql.capture());
        assertThat(sql.getAllValues()).contains(
                "ALTER TABLE \"mdr\".\"data_element\" NOCHECK CONSTRAINT ALL",
                "ALTER TABLE \"ext\".\"item_classification\" NOCHECK CONSTRAINT ALL",
                "ALTER TABLE \"mdr\".\"data_element\" WITH CHECK CHECK CONSTRAINT ALL",
                "ALTER TABLE \"ext\".\"item_classification\" WITH CHECK CHECK CONSTRAINT ALL");
    }

    @Test
    @DisplayName("preserve 清单按表名跳过两个 schema")
    void preserveSkipsTableInEitherSchema() throws Exception {
        discoverMdr("system_setting");
        discoverExt("system_setting", "account");

        DatabaseCleaner cleaner = new DatabaseCleaner(dataSource, env, List.of());
        cleaner.clearTables(conn, Set.of("system_setting"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).executeUpdate(sql.capture());
        assertThat(sql.getAllValues()).containsExactly("DELETE FROM \"ext\".\"account\"");
    }

    @Test
    @DisplayName("达梦策略用发现时的 schema 查外键元数据")
    void dmStrategyUsesDiscoveredSchemaForMetadata() throws Exception {
        env = new DatabaseEnvironment("DM", "", null, null);
        DatabaseCleaner cleaner = new DatabaseCleaner(
                dataSource, env, List.of(new DmFkConstraintStrategy(env)));

        when(metaData.getTables(eq("metadata_registry"), eq("MDR"), isNull(), eq(new String[]{"TABLE"})))
                .thenReturn(mdrTables);
        when(mdrTables.next()).thenReturn(true, false);
        when(mdrTables.getString("TABLE_NAME")).thenReturn("data_element");
        when(metaData.getTables(eq("metadata_registry"), eq("EXT"), isNull(), eq(new String[]{"TABLE"})))
                .thenReturn(extTables);
        when(extTables.next()).thenReturn(true, false);
        when(extTables.getString("TABLE_NAME")).thenReturn("item_classification");

        ResultSet mdrFkRows = org.mockito.Mockito.mock(ResultSet.class);
        when(mdrFkRows.next()).thenReturn(false);
        when(metaData.getImportedKeys(eq("metadata_registry"), eq("MDR"), eq("data_element")))
                .thenReturn(mdrFkRows);
        ResultSet fkRows = org.mockito.Mockito.mock(ResultSet.class);
        when(metaData.getImportedKeys(eq("metadata_registry"), eq("EXT"), eq("item_classification")))
                .thenReturn(fkRows);
        when(fkRows.next()).thenReturn(true, false);
        when(fkRows.getString("FK_NAME")).thenReturn("FK_ITEM_CLASS");

        cleaner.clearTables(conn, Set.of());

        verify(metaData, org.mockito.Mockito.times(2))
                .getImportedKeys("metadata_registry", "EXT", "item_classification");
        verify(statement).execute("ALTER TABLE \"EXT\".\"item_classification\" DISABLE CONSTRAINT \"FK_ITEM_CLASS\"");
    }

    @Test
    @DisplayName("显式 schema 停/启 FK:同名标准表 language_identification 限定到 mdr 而非 ext(导入路径)")
    void explicitSchemaFkTargetsStandardTableForSameNamedImport() throws Exception {
        DatabaseCleaner cleaner = new DatabaseCleaner(
                dataSource, env, List.of(new SqlServerFkConstraintStrategy(env)));

        // 模拟迁移导入:导出文件中的裸表名均来自主 schema(含与 EXT 表同名的 language_identification)
        cleaner.disableForeignKeys(conn, env.schema(), List.of("data_element", "language_identification"));
        cleaner.enableForeignKeys(conn, env.schema(), List.of("data_element", "language_identification"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).execute(sql.capture());
        assertThat(sql.getAllValues()).contains(
                "ALTER TABLE \"mdr\".\"language_identification\" NOCHECK CONSTRAINT ALL",
                "ALTER TABLE \"mdr\".\"language_identification\" WITH CHECK CHECK CONSTRAINT ALL");
        assertThat(sql.getAllValues())
                .noneMatch(s -> s.contains("\"ext\".\"language_identification\""));
    }

    @Test
    @DisplayName("多轮删除仍失败的表按 schema.表名 报错")
    void reportsUnqualifiedFailureWithSchema() throws Exception {
        discoverMdr("broken_table");
        discoverExt();
        java.sql.SQLException boom = new java.sql.SQLException("denied");
        when(statement.executeUpdate(anyString())).thenThrow(boom);

        DatabaseCleaner cleaner = new DatabaseCleaner(dataSource, env, List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> cleaner.clearTables(conn, Set.of()))
                .isInstanceOf(java.sql.SQLException.class)
                .hasMessageContaining("mdr.broken_table");
        verify(statement, never()).execute(anyString());
    }
}
