package io.github.matrixlane.metadataregistry.web.service.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FlywayHistoryRelocator} 单元测试:目标 schema 取自 Flyway 自身配置；
 * 搬迁 DDL 必须显式 autoCommit(连接池统一 auto-commit=false,否则被回滚);
 * 目标 schema 缺失必须 fail fast;非 SQL Server / Flyway 未启用完全不触碰 JDBC。
 */
@ExtendWith(MockitoExtension.class)
class FlywayHistoryRelocatorTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection conn;

    @Mock
    private DatabaseMetaData metaData;

    @Mock
    private Statement statement;

    @Mock
    private Flyway flyway;

    @Mock
    private ClassicConfiguration configuration;

    private DatabaseEnvironment env;

    @BeforeEach
    void setUp() throws Exception {
        env = new DatabaseEnvironment("SQLSERVER", "", null, null);
        lenient().when(dataSource.getConnection()).thenReturn(conn);
        lenient().when(conn.getMetaData()).thenReturn(metaData);
        lenient().when(conn.getCatalog()).thenReturn("metadata_registry");
        lenient().when(conn.createStatement()).thenReturn(statement);
        lenient().when(flyway.getConfiguration()).thenReturn(configuration);
        lenient().when(configuration.getTable()).thenReturn("flyway_schema_history");
        lenient().when(configuration.getDefaultSchema()).thenReturn("ext");
        lenient().when(configuration.getSchemas()).thenReturn(new String[]{"mdr", "ext"});
    }

    /** 按 schema 名返回表探测结果(getTables 的 schemaPattern 参数为第二个). */
    private void tableLookup(Map<String, Boolean> existsBySchema) throws Exception {
        when(metaData.getTables(any(), any(), eq("flyway_schema_history"),
                eq(new String[]{"TABLE"})))
                .thenAnswer(invocation -> {
                    String schema = invocation.getArgument(1);
                    return singleRow(existsBySchema.getOrDefault(schema, false));
                });
    }

    private void schemaLookup(boolean targetExists) throws Exception {
        lenient().when(metaData.getSchemas(any(), eq("ext")))
                .thenAnswer(invocation -> singleRow(targetExists));
    }

    private ResultSet singleRow(boolean exists) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(exists);
        return rs;
    }

    private static ObjectProvider<Flyway> providerOf(Flyway flyway) {
        return new ObjectProvider<>() {
            @Override
            public Flyway getObject() {
                if (flyway == null) {
                    throw new NoSuchBeanDefinitionException(Flyway.class);
                }
                return flyway;
            }
        };
    }

    private static FlywayHistoryRelocator relocator(DataSource ds, DatabaseEnvironment env, Flyway flyway) {
        return new FlywayHistoryRelocator(ds, env, providerOf(flyway));
    }

    @Test
    @DisplayName("目标处无 + 主 schema 有 → 执行带引号的 ALTER SCHEMA TRANSFER,且显式提交并恢复原 autoCommit")
    void relocatesWithExplicitCommit() throws Exception {
        tableLookup(Map.of("ext", false, "mdr", true));
        schemaLookup(true);
        when(conn.getAutoCommit()).thenReturn(false);

        relocator(dataSource, env, flyway).relocate();

        verify(statement).execute("ALTER SCHEMA \"ext\" TRANSFER \"mdr\".\"flyway_schema_history\"");
        verify(conn).setAutoCommit(true);
        verify(conn).setAutoCommit(false);
    }

    @Test
    @DisplayName("连接本为 autoCommit 时不做 setAutoCommit 调整")
    void keepsAutoCommitWhenAlreadyTrue() throws Exception {
        tableLookup(Map.of("ext", false, "mdr", true));
        schemaLookup(true);
        when(conn.getAutoCommit()).thenReturn(true);

        relocator(dataSource, env, flyway).relocate();

        verify(statement).execute("ALTER SCHEMA \"ext\" TRANSFER \"mdr\".\"flyway_schema_history\"");
        verify(conn, never()).setAutoCommit(anyBoolean());
    }

    @Test
    @DisplayName("两处各有一张(异常态) → 跳过搬迁,不执行 ALTER")
    void skipsWhenBothSchemasHaveHistory() throws Exception {
        tableLookup(Map.of("ext", true, "mdr", true));

        relocator(dataSource, env, flyway).relocate();

        verify(statement, never()).execute(any());
    }

    @Test
    @DisplayName("目标处已有历史表 → 无需搬迁")
    void skipsWhenAlreadyInTarget() throws Exception {
        tableLookup(Map.of("ext", true, "mdr", false));

        relocator(dataSource, env, flyway).relocate();

        verify(statement, never()).execute(any());
    }

    @Test
    @DisplayName("两处都无(全新库) → 无需搬迁")
    void skipsWhenAbsentEverywhere() throws Exception {
        tableLookup(Map.of("ext", false, "mdr", false));

        relocator(dataSource, env, flyway).relocate();

        verify(statement, never()).execute(any());
    }

    @Test
    @DisplayName("目标 schema 不存在 → fail fast(防 Flyway re-baseline)")
    void failsFastWhenTargetSchemaMissing() throws Exception {
        tableLookup(Map.of("ext", false, "mdr", true));
        schemaLookup(false);

        assertThatThrownBy(() -> relocator(dataSource, env, flyway).relocate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("re-baseline");
        verify(statement, never()).execute(any());
    }

    @Test
    @DisplayName("defaultSchema 未设时回退到 schemas[0] 作为目标")
    void fallsBackToFirstSchemasEntry() throws Exception {
        when(configuration.getDefaultSchema()).thenReturn(null);
        when(configuration.getSchemas()).thenReturn(new String[]{"custom_target"});
        tableLookup(Map.of("custom_target", false, "mdr", true));
        lenient().when(metaData.getSchemas(any(), eq("custom_target")))
                .thenAnswer(invocation -> singleRow(true));
        when(conn.getAutoCommit()).thenReturn(true);

        relocator(dataSource, env, flyway).relocate();

        verify(statement).execute("ALTER SCHEMA \"custom_target\" TRANSFER \"mdr\".\"flyway_schema_history\"");
    }

    @Test
    @DisplayName("目标 schema 以 Flyway 配置为准(与 DB_EXT_SCHEMA 分叉时跟随 Flyway)")
    void targetSchemaFollowsFlywayConfiguration() throws Exception {
        when(configuration.getDefaultSchema()).thenReturn("flyway_target");
        tableLookup(Map.of("flyway_target", false, "MDR2", true));
        lenient().when(metaData.getSchemas(any(), eq("flyway_target")))
                .thenAnswer(invocation -> singleRow(true));
        DatabaseEnvironment customEnv = new DatabaseEnvironment("SQLSERVER", "", "MDR2", "EXT2");
        when(conn.getAutoCommit()).thenReturn(true);

        relocator(dataSource, customEnv, flyway).relocate();

        verify(statement).execute("ALTER SCHEMA \"flyway_target\" TRANSFER \"MDR2\".\"flyway_schema_history\"");
    }

    @Test
    @DisplayName("探测失败 → fail fast 阻断启动")
    void failsFastOnSqlError() throws Exception {
        when(metaData.getTables(any(), any(), eq("flyway_schema_history"),
                eq(new String[]{"TABLE"})))
                .thenThrow(new SQLException("boom"));

        assertThatThrownBy(() -> relocator(dataSource, env, flyway).relocate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("flyway_schema_history");
    }

    @Test
    @DisplayName("连接获取失败 → 包装为 IllegalStateException 阻断启动")
    void wrapsGetConnectionFailure() throws Exception {
        when(dataSource.getConnection())
                .thenThrow(new CannotGetJdbcConnectionException("no db"));

        assertThatThrownBy(() -> relocator(dataSource, env, flyway).relocate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("re-baseline");
    }

    @Test
    @DisplayName("SQLite 方言 → 完全不触碰 JDBC")
    void noOpForSqlite() throws Exception {
        DatabaseEnvironment sqlite = new DatabaseEnvironment("SQLITE", "jdbc:sqlite::memory:", null, null);

        relocator(dataSource, sqlite, flyway).relocate();

        verify(dataSource, never()).getConnection();
    }

    @Test
    @DisplayName("达梦方言(用户即模式) → 完全不触碰 JDBC")
    void noOpForDm() throws Exception {
        DatabaseEnvironment dm = new DatabaseEnvironment("DM", "", null, null);

        relocator(dataSource, dm, flyway).relocate();

        verify(dataSource, never()).getConnection();
    }

    @Test
    @DisplayName("Flyway 未启用 → 完全不触碰 JDBC")
    void noOpWhenFlywayAbsent() throws Exception {
        FlywayHistoryRelocator relocator = new FlywayHistoryRelocator(dataSource, env, providerOf(null));

        relocator.relocate();

        verify(dataSource, never()).getConnection();
    }
}
