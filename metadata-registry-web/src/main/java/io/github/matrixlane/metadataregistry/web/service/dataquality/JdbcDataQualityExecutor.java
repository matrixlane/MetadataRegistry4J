package io.github.matrixlane.metadataregistry.web.service.dataquality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 JDBC 的数据质量评估执行器.
 *
 * <p>连接到遗留数据库并执行统计查询，将结果提取为内存中的列数据。
 * 支持 SQL Server（当前 MVP 的主要目标数据库）。
 *
 * <p>后续可通过扩展支持其他数据库方言。
 */
public class JdbcDataQualityExecutor {

    private static final Logger log = LoggerFactory.getLogger(JdbcDataQualityExecutor.class);

    /** 数据库类型枚举. */
    public enum DatabaseType {
        SQL_SERVER,
        UNKNOWN
    }

    private final DatabaseType databaseType;
    private final JdbcTemplate jdbcTemplate;

    public JdbcDataQualityExecutor(String url, String username, String password, DatabaseType databaseType) {
        this.databaseType = databaseType;
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(60); // 60 seconds timeout
    }

    public JdbcDataQualityExecutor(DataSource dataSource, DatabaseType databaseType) {
        this.databaseType = databaseType;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.jdbcTemplate.setQueryTimeout(60);
    }

    /**
     * 获取表的估计行数.
     */
    public long getEstimatedRowCount(String schema, String tableName) {
        String sql = switch (databaseType) {
            case SQL_SERVER ->
                    "SELECT SUM(p.rows) FROM sys.tables t " +
                    "INNER JOIN sys.partitions p ON t.object_id = p.object_id " +
                    "INNER JOIN sys.schemas s ON t.schema_id = s.schema_id " +
                    "WHERE s.name = ? AND t.name = ? AND p.index_id IN (0, 1)";
            default -> "SELECT COUNT(*) FROM " + quoteIdentifier(schema) + "." + quoteIdentifier(tableName);
        };

        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class, schema, tableName);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("Failed to get estimated row count for {}.{}, falling back to COUNT(*)",
                    schema, tableName, e);
            return getExactRowCount(schema, tableName);
        }
    }

    /**
     * 获取表的精确行数（使用 COUNT(*)）.
     */
    public long getExactRowCount(String schema, String tableName) {
        String sql = "SELECT COUNT(*) FROM " + quoteIdentifier(schema) + "." + quoteIdentifier(tableName);
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("Failed to get exact row count for {}.{}", schema, tableName, e);
            return 0L;
        }
    }

    /**
     * 提取列数据到内存.
     *
     * @param schema       schema 名
     * @param tableName    表名
     * @param columns      要提取的列（null 或空表示所有列）
     * @param useSampling  是否使用采样
     * @param sampleSize   采样行数
     * @return 列名 -> 列值列表
     */
    public Map<String, List<Object>> fetchColumnData(String schema, String tableName,
                                                      List<String> columns,
                                                      boolean useSampling, int sampleSize) {
        String qualifiedName = quoteIdentifier(schema) + "." + quoteIdentifier(tableName);
        String selectClause = buildSelectClause(columns, qualifiedName);
        String sql = buildFetchSql(selectClause, qualifiedName, useSampling, sampleSize);

        log.debug("Executing fetch SQL: {}", sql);

        Map<String, List<Object>> columnData = new HashMap<>();

        jdbcTemplate.query(sql, rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            // Initialize lists
            for (int i = 1; i <= columnCount; i++) {
                String colName = meta.getColumnLabel(i);
                columnData.putIfAbsent(colName, new ArrayList<>());
            }

            // Collect rows
            rs.beforeFirst();
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    String colName = meta.getColumnLabel(i);
                    Object value = rs.getObject(i);
                    columnData.get(colName).add(value);
                }
            }
        });

        return columnData;
    }

    /**
     * 检查表是否为空.
     */
    public boolean isTableEmpty(String schema, String tableName) {
        return getExactRowCount(schema, tableName) == 0;
    }

    // ─────────────────────────────────────────────────────────────────
    // SQL Building
    // ─────────────────────────────────────────────────────────────────

    private String buildSelectClause(List<String> columns, String qualifiedName) {
        if (columns == null || columns.isEmpty()) {
            return "*";
        }
        return String.join(", ", columns.stream()
                .map(this::quoteIdentifier)
                .toList());
    }

    private String buildFetchSql(String selectClause, String qualifiedName,
                                  boolean useSampling, int sampleSize) {
        if (!useSampling) {
            return "SELECT " + selectClause + " FROM " + qualifiedName;
        }

        // Sampling strategy depends on database type
        return switch (databaseType) {
            case SQL_SERVER -> buildSqlServerSamplingSql(selectClause, qualifiedName, sampleSize);
            default -> "SELECT TOP " + sampleSize + " " + selectClause + " FROM " + qualifiedName;
        };
    }

    private String buildSqlServerSamplingSql(String selectClause, String qualifiedName, int sampleSize) {
        // Use TABLESAMPLE for larger tables, TOP with NEWID() for moderate ones
        // Note: TABLESAMPLE has page-level bias - documented in sampling strategy
        if (sampleSize > 500_000) {
            return "SELECT " + selectClause + " FROM " + qualifiedName
                    + " TABLESAMPLE (" + sampleSize + " ROWS)";
        }
        // NEWID() random sampling for smaller sample sizes
        return "SELECT TOP " + sampleSize + " " + selectClause
                + " FROM " + qualifiedName
                + " ORDER BY NEWID()";
    }

    private String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) return "";
        // SQL Server uses square brackets; override for other DBs as needed
        return "[" + identifier.replace("]", "]]") + "]";
    }

    /**
     * 测试连接是否可用.
     */
    public boolean testConnection() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.warn("Database connection test failed", e);
            return false;
        }
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }
}
