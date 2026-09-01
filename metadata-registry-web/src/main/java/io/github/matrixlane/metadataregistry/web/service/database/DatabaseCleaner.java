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
        List<String> tables = discoverTables(conn, preserveTables);
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
     */
    public void disableForeignKeys(Connection conn, List<String> tables) throws SQLException {
        FkConstraintStrategy strategy = strategies.get(env.vendor());
        if (strategy != null) {
            strategy.beforeBulkDelete(conn, tables);
        }
    }

    /**
     * 批量写入后恢复外键约束。
     */
    public void enableForeignKeys(Connection conn, List<String> tables) throws SQLException {
        FkConstraintStrategy strategy = strategies.get(env.vendor());
        if (strategy != null) {
            strategy.afterBulkDelete(conn, tables);
        }
    }

    private List<String> discoverTables(Connection conn, Set<String> preserveTables) throws SQLException {
        Set<String> preserve = preserveTables.stream()
                .map(t -> t.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        DatabaseMetaData md = conn.getMetaData();
        List<String> tables = new ArrayList<>();
        discoverTablesInSchema(conn, md, env.schema(), preserve, tables);
        if (env.extSchema() != null && !env.extSchema().equalsIgnoreCase(env.schema() == null ? "" : env.schema())) {
            discoverTablesInSchema(conn, md, env.extSchema(), preserve, tables);
        }
        return tables;
    }

    private void discoverTablesInSchema(Connection conn, DatabaseMetaData md, String schema,
            Set<String> preserve, List<String> tables) throws SQLException {
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
                tables.add(name);
            }
        }
    }

    private void deleteWithRetry(Connection conn, List<String> tables) throws SQLException {
        List<String> remaining = new ArrayList<>(tables);
        for (int pass = 1; pass <= MAX_DELETE_PASSES && !remaining.isEmpty(); pass++) {
            List<String> failed = new ArrayList<>();
            for (String table : remaining) {
                try (Statement st = conn.createStatement()) {
                    int deleted = st.executeUpdate("DELETE FROM " + env.qualifyAuto(table));
                    log.debug("database-cleaner: deleted {} rows from [{}]", deleted, table);
                } catch (SQLException e) {
                    log.debug("database-cleaner: delete from [{}] failed on pass {}: {}", table, pass, e.getMessage());
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
            throw new SQLException("以下表在多轮删除后仍未清空: " + remaining);
        }
    }
}
