package io.github.matrixlane.metadataregistry.web.service.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dialect-portable bulk cleaner for application tables.
 *
 * <p>Replaces the legacy SQL Server-only implementation that queried
 * INFORMATION_SCHEMA and issued NOCHECK/DBCC statements. Tables are
 * discovered through standard JDBC metadata ({@link DatabaseMetaData}),
 * foreign-key handling is delegated to a {@link FkConstraintStrategy}
 * selected by the current {@link DatabaseVendor}, and a retry loop provides
 * a dialect-independent safety net for unexpected dependency cycles.
 *
 * <p>Used by the init wizard (abort/setup cleanup, preserving account and
 * system_setting) and by factory reset (wiping everything except the Flyway
 * history table).
 */
@Slf4j
@Component
public class DatabaseCleaner {

    private static final int MAX_DELETE_PASSES = 3;

    private final DataSource dataSource;
    private final DatabaseEnvironment env;
    private final Map<DatabaseVendor, FkConstraintStrategy> strategies;

    public DatabaseCleaner(DataSource dataSource,
                           DatabaseEnvironment env,
                           List<FkConstraintStrategy> strategies) {
        this.dataSource = dataSource;
        this.env = env;
        this.strategies = strategies.stream()
                .collect(Collectors.toMap(FkConstraintStrategy::vendor, Function.identity()));
    }

    /**
     * Deletes all rows from every application table except the preserved ones.
     * Must be called inside a transaction (participates in the caller's).
     *
     * @param preserveTables table names to skip, case-insensitive
     */
    public void clearTables(Set<String> preserveTables) {
        Connection conn = DataSourceUtils.getConnection(dataSource);
        try {
            clearTables(conn, preserveTables);
        } catch (SQLException e) {
            throw new IllegalStateException("清空业务表失败: " + e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    /**
     * 在调用方提供的连接上执行清空（用于需要手工控制事务边界的场景，
     * 例如迁移导入的清空+写入+提交序列）。
     */
    public void clearTables(Connection conn, Set<String> preserveTables) throws SQLException {
        List<TableRef> tables = discoverTables(conn, preserveTables);
        log.info("database-cleaner: clearing {} tables on {} (preserving {})",
                tables.size(), env.vendor(), preserveTables);
        FkConstraintStrategy strategy = strategies.get(env.vendor());
        if (strategy != null) {
            strategy.beforeBulkDelete(conn, tables);
        }
        deleteWithRetry(conn, tables);
        if (strategy != null) {
            strategy.afterBulkDelete(conn, tables);
        }
    }

    /**
     * 批量写入前停用外键约束（供迁移导入等场景复用）。
     * 与 {@link #clearTables} 使用同一方言策略。
     *
     * <p>裸表名按 EXT 清单自动路由 schema;存在与 EXT 表同名的标准表时
     * (如 language_identification)会被路由到 ext schema,标准表清单请改用
     * {@link #disableForeignKeys(Connection, String, List)} 显式指定 schema。
     */
    public void disableForeignKeys(Connection conn, List<String> tables) throws SQLException {
        disableForeignKeys(conn, null, tables);
    }

    /**
     * 批量写入前停用外键约束,调用方显式指定表所在 schema(schema 为 null 时按
     * EXT 清单自动路由,见 {@link #disableForeignKeys(Connection, List)})。
     */
    public void disableForeignKeys(Connection conn, String schema, List<String> tables) throws SQLException {
        FkConstraintStrategy strategy = strategies.get(env.vendor());
        if (strategy != null) {
            strategy.beforeBulkDelete(conn, toTableRefs(schema, tables));
        }
    }

    /**
     * 批量写入后恢复外键约束。同名表路由注意事项同
     * {@link #disableForeignKeys(Connection, List)}。
     */
    public void enableForeignKeys(Connection conn, List<String> tables) throws SQLException {
        enableForeignKeys(conn, null, tables);
    }

    /**
     * 批量写入后恢复外键约束,调用方显式指定表所在 schema。
     */
    public void enableForeignKeys(Connection conn, String schema, List<String> tables) throws SQLException {
        FkConstraintStrategy strategy = strategies.get(env.vendor());
        if (strategy != null) {
            strategy.afterBulkDelete(conn, toTableRefs(schema, tables));
        }
    }

    /**
     * 表名转 {@link TableRef}:schema 为 null 时按 EXT 清单路由
     * (迁移导入等场景,表清单来自导出文件);显式传入 schema 时一律采用
     * (导出文件的裸表名均来自主 schema,同名 EXT 表不得劫持路由)。
     */
    private List<TableRef> toTableRefs(String schema, List<String> tables) {
        return tables.stream()
                .map(name -> new TableRef(schema != null ? schema : env.schemaOf(name), name))
                .collect(Collectors.toList());
    }

    private List<TableRef> discoverTables(Connection conn, Set<String> preserveTables) throws SQLException {
        Set<String> preserve = preserveTables.stream()
                .map(t -> t.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        DatabaseMetaData md = conn.getMetaData();
        List<TableRef> tables = new ArrayList<>();
        discoverTablesInSchema(conn, md, env.schema(), preserve, tables);
        if (env.extSchema() != null && !env.extSchema().equalsIgnoreCase(env.schema() == null ? "" : env.schema())) {
            discoverTablesInSchema(conn, md, env.extSchema(), preserve, tables);
        }
        return tables;
    }

    private void discoverTablesInSchema(Connection conn, DatabaseMetaData md, String schema,
            Set<String> preserve, List<TableRef> tables) throws SQLException {
        try (ResultSet rs = md.getTables(conn.getCatalog(), schema, null, new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name == null) {
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                // SQLite internal tables (sqlite_sequence etc.)
                if (lower.startsWith("sqlite_")) {
                    continue;
                }
                // 达梦内部约束辅助表（SREF_CON_TAB<digits>_*），不可直接删除
                if (lower.startsWith("sref_")) {
                    continue;
                }
                if (preserve.contains(lower)) {
                    continue;
                }
                tables.add(new TableRef(schema, name));
            }
        }
    }

    private void deleteWithRetry(Connection conn, List<TableRef> tables) throws SQLException {
        List<TableRef> remaining = new ArrayList<>(tables);
        for (int pass = 1; pass <= MAX_DELETE_PASSES && !remaining.isEmpty(); pass++) {
            List<TableRef> failed = new ArrayList<>();
            for (TableRef table : remaining) {
                try (Statement st = conn.createStatement()) {
                    int deleted = st.executeUpdate("DELETE FROM " + env.qualify(table.schema(), table.name()));
                    log.debug("database-cleaner: deleted {} rows from [{}]", deleted, table.name());
                } catch (SQLException e) {
                    log.debug("database-cleaner: delete from [{}] failed on pass {}: {}", table.name(), pass, e.getMessage());
                    failed.add(table);
                }
            }
            if (failed.size() == remaining.size()) {
                // No progress in a full pass — retrying will not help.
                break;
            }
            remaining = failed;
        }
        if (!remaining.isEmpty()) {
            throw new SQLException("以下表在多轮删除后仍未清空: "
                    + remaining.stream().map(t -> t.schema() + "." + t.name()).collect(Collectors.joining(", ")));
        }
    }
}
