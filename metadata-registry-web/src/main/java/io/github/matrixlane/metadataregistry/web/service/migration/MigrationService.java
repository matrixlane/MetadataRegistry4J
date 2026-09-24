package io.github.matrixlane.metadataregistry.web.service.migration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseCleaner;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 跨数据库数据迁移（见 openspec: database-migration）。
 *
 * <p>导出：将全部表（含 account / system_setting，排除 flyway_schema_history）
 * 按当前方言限定名读取并写入 {@code ./data/migration-export.json}（v1 单文件格式）。
 *
 * <p>导入：目标库行数检查（非空需 force）→ 清空 → 方言 FK 停用 → 逐表批量写入
 * → 逐表行数校验，全部在单事务内完成，失败整体回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {

    private static final String EXPORT_FILE_NAME = "migration-export.json";
    private static final int BATCH_SIZE = 200;

    private final DataSource dataSource;
    private final DatabaseEnvironment env;
    private final DatabaseCleaner databaseCleaner;
    private final MaintenanceModeService maintenanceMode;
    private final ObjectMapper objectMapper;

    // ── 导出 ────────────────────────────────────────────────────

    public Map<String, Object> exportData() {
        maintenanceMode.enable();
        try {
            Path out = exportFile();
            Files.createDirectories(out.getParent());
            Connection conn = DataSourceUtils.getConnection(dataSource);
            List<String> tables;
            try {
                tables = discoverTables(conn);
            } finally {
                DataSourceUtils.releaseConnection(conn, dataSource);
            }
            long totalRows = 0;
            JsonFactory factory = new JsonFactory();
            conn = DataSourceUtils.getConnection(dataSource);
            try (Writer writer = Files.newBufferedWriter(out);
                 JsonGenerator g = factory.createGenerator(writer)) {
                g.writeStartObject();
                g.writeStringField("format", "mdr-migration-v1");
                g.writeStringField("exportedAt", Instant.now().toString());
                g.writeStringField("sourceVendor", env.vendor().name());
                g.writeArrayFieldStart("tables");
                for (String table : tables) {
                    totalRows += exportTable(conn, table, g);
                }
                g.writeEndArray();
                g.writeEndObject();
            } finally {
                DataSourceUtils.releaseConnection(conn, dataSource);
            }
            log.warn("迁移导出完成：{} 张表，{} 行 → {}", tables.size(), totalRows, out.toAbsolutePath());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("file", out.toAbsolutePath().toString());
            result.put("tableCount", tables.size());
            result.put("totalRows", totalRows);
            return result;
        } catch (Exception e) {
            maintenanceMode.disable();
            throw new BusinessException("迁移导出失败: " + e.getMessage());
        }
    }

    private long exportTable(Connection conn, String table, JsonGenerator g) throws SQLException, IOException {
        long count = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + env.qualify(table))) {
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            g.writeStartObject();
            g.writeStringField("name", table);
            g.writeArrayFieldStart("columns");
            for (int i = 1; i <= cols; i++) {
                g.writeString(md.getColumnName(i));
            }
            g.writeEndArray();
            g.writeArrayFieldStart("rows");
            while (rs.next()) {
                g.writeStartArray();
                for (int i = 1; i <= cols; i++) {
                    writeValue(g, rs.getObject(i), md.getColumnType(i));
                }
                g.writeEndArray();
                count++;
            }
            g.writeEndArray();
            g.writeNumberField("rowCount", count);
            g.writeEndObject();
        }
        return count;
    }

    private void writeValue(JsonGenerator g, Object value, int jdbcType) throws IOException {
        if (value == null) {
            g.writeNull();
            return;
        }
        if (isTemporal(jdbcType)) {
            // 时间列统一序列化为 {"$ts": epochMillis}，兼容 SQLite 将时间存为
            // INTEGER/文本而目标是 datetime2/timestamp 的场景
            Long millis = toEpochMillis(value);
            if (millis != null) {
                g.writeStartObject();
                g.writeNumberField("$ts", millis);
                g.writeEndObject();
                return;
            }
        }
        if (value instanceof byte[] bytes) {
            g.writeStartObject();
            g.writeStringField("$blob", Base64.getEncoder().encodeToString(bytes));
            g.writeEndObject();
        } else if (value instanceof Timestamp ts) {
            g.writeStartObject();
            g.writeNumberField("$ts", ts.getTime());
            g.writeEndObject();
        } else if (value instanceof java.sql.Date d) {
            g.writeStartObject();
            g.writeNumberField("$ts", d.getTime());
            g.writeEndObject();
        } else {
            g.writeObject(value);
        }
    }

    private boolean isTemporal(int jdbcType) {
        return jdbcType == java.sql.Types.TIMESTAMP
                || jdbcType == java.sql.Types.TIMESTAMP_WITH_TIMEZONE
                || jdbcType == java.sql.Types.DATE
                || jdbcType == java.sql.Types.TIME
                || jdbcType == java.sql.Types.TIME_WITH_TIMEZONE;
    }

    private Long toEpochMillis(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof Timestamp ts) {
            return ts.getTime();
        }
        if (value instanceof java.util.Date d) {
            return d.getTime();
        }
        if (value instanceof String s) {
            try {
                String normalized = s.trim().replace(' ', 'T');
                return java.time.LocalDateTime.parse(
                                normalized.length() > 23 ? normalized.substring(0, 23) : normalized)
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    // ── 导入 ────────────────────────────────────────────────────

    public Map<String, Object> importData(boolean force) {
        Path file = exportFile();
        if (!Files.exists(file)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "迁移文件不存在: " + file.toAbsolutePath());
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(file.toFile());
        } catch (IOException e) {
            throw new BusinessException("迁移文件解析失败: " + e.getMessage());
        }
        if (!"mdr-migration-v1".equals(root.path("format").asText())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "不支持的迁移文件格式");
        }

        Connection conn = DataSourceUtils.getConnection(dataSource);
        boolean previousAutoCommit;
        try {
            previousAutoCommit = conn.getAutoCommit();
        } catch (SQLException e) {
            throw new BusinessException("迁移导入失败: " + e.getMessage());
        }
        try {
            // 手工事务边界：清空 + 写入 + 校验在同一连接上原子提交，
            // 避免池化连接未提交导致的清理不可见问题。
            conn.setAutoCommit(false);

            List<String> targetTables = discoverTables(conn);
            long existing = countRows(conn, targetTables);
            if (existing > 0 && !force) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "目标库非空（现有 " + existing + " 行）。为防止数据合并事故已拒绝导入；确认覆盖请使用 force=true");
            }

            // 清空（含种子与系统表；保留 flyway_schema_history）
            databaseCleaner.clearTables(conn, Set.of("flyway_schema_history"));

            JsonNode tables = root.path("tables");
            Map<String, Long> mismatches = new LinkedHashMap<>();
            long totalRows = 0;
            List<String> tableNames = new ArrayList<>();
            for (JsonNode tableNode : tables) {
                tableNames.add(tableNode.path("name").asText());
            }
            // 导出文件只含主 schema 表的裸名(导出走 env.schema() 发现),
            // 显式指定主 schema 限定,避免同名 EXT 表(language_identification)
            // 按 EXT 清单劫持路由(见 DatabaseEnvironment.EXT_TABLES 同名表警示)。
            databaseCleaner.disableForeignKeys(conn, env.schema(), tableNames);
            for (JsonNode tableNode : tables) {
                totalRows += importTable(conn, tableNode);
            }
            databaseCleaner.enableForeignKeys(conn, env.schema(), tableNames);

            // 逐表行数校验
            for (JsonNode tableNode : tables) {
                String name = tableNode.path("name").asText();
                long expected = tableNode.path("rowCount").asLong();
                long actual = countRows(conn, List.of(name));
                if (expected != actual) {
                    mismatches.put(name, actual);
                }
            }
            if (!mismatches.isEmpty()) {
                throw new BusinessException("迁移校验失败，行数不一致的表: " + mismatches);
            }

            conn.commit();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tableCount", tables.size());
            result.put("totalRows", totalRows);
            result.put("verified", true);
            return result;
        } catch (Exception e) {
            try {
                conn.rollback();
            } catch (SQLException rollbackError) {
                log.warn("迁移导入回滚失败: {}", rollbackError.getMessage());
            }
            if (e instanceof BusinessException be) {
                throw be;
            }
            throw new BusinessException("迁移导入失败: " + e.getMessage());
        } finally {
            try {
                conn.setAutoCommit(previousAutoCommit);
            } catch (SQLException e) {
                log.debug("恢复 autoCommit 失败: {}", e.getMessage());
            }
            DataSourceUtils.releaseConnection(conn, dataSource);
        }
    }

    private int importTable(Connection conn, JsonNode tableNode) throws SQLException, IOException {
        String name = tableNode.path("name").asText();
        JsonNode columns = tableNode.path("columns");
        JsonNode rows = tableNode.path("rows");
        if (rows.isEmpty()) {
            return 0;
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(env.qualify(name)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(',');
            sql.append('"').append(columns.get(i).asText()).append('"');
        }
        sql.append(") VALUES (").append("?,".repeat(columns.size()));
        sql.setLength(sql.length() - 1);
        sql.append(')');

        Set<String> uuidColumns = uuidLikeColumns(conn, name);
        int count = 0;
        boolean identityInsert = enableIdentityInsertIfNeeded(conn, name);
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            try {
                for (JsonNode row : rows) {
                    for (int i = 0; i < columns.size(); i++) {
                        bindValue(ps, i + 1, row.get(i), uuidColumns.contains(
                                columns.get(i).asText().toLowerCase(Locale.ROOT)));
                    }
                    ps.executeUpdate();
                    count++;
                }
                log.debug("migration-import: inserted {} rows into [{}]", count, name);
            } catch (SQLException e) {
                throw new SQLException("迁移导入写入表 [" + name + "] 失败: " + e.getMessage(), e);
            }
        } finally {
            if (identityInsert) {
                disableIdentityInsert(conn, name);
            }
        }
        return count;
    }

    /**
     * SQL Server 的 IDENTITY 列需要显式开启 IDENTITY_INSERT 才能写入显式值。
     *
     * <p>注意：必须通过 JDBC 元数据（IS_AUTOINCREMENT）确认表确有 IDENTITY 列
     * 后再开启——盲目对无 IDENTITY 列的表执行 SET IDENTITY_INSERT 会报错，
     * 而 mssql-jdbc 默认 SET XACT_ABORT ON，该错误会静默回滚整个事务
     * （包括此前的清空操作），导致后续写入出现诡异的唯一键冲突。
     */
    private boolean enableIdentityInsertIfNeeded(Connection conn, String table) {
        if (env.vendor() != io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor.SQLSERVER) {
            return false;
        }
        try {
            DatabaseMetaData md = conn.getMetaData();
            boolean hasIdentity = false;
            try (ResultSet rs = md.getColumns(conn.getCatalog(), env.schema(), table, null)) {
                while (rs.next()) {
                    if ("YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"))) {
                        hasIdentity = true;
                        break;
                    }
                }
            }
            if (!hasIdentity) {
                return false;
            }
            try (Statement st = conn.createStatement()) {
                st.execute("SET IDENTITY_INSERT " + env.qualify(table) + " ON");
                return true;
            }
        } catch (SQLException e) {
            log.warn("检测/开启 IDENTITY_INSERT 失败（表 {}）: {}", table, e.getMessage());
            return false;
        }
    }

    private void disableIdentityInsert(Connection conn, String table) {
        try (Statement st = conn.createStatement()) {
            st.execute("SET IDENTITY_INSERT " + env.qualify(table) + " OFF");
        } catch (SQLException e) {
            log.debug("IDENTITY_INSERT OFF for {} failed: {}", table, e.getMessage());
        }
    }

    private void bindValue(PreparedStatement ps, int index, JsonNode node, boolean uuidColumn) throws SQLException {
        if (node == null || node.isNull()) {
            ps.setObject(index, null);
            return;
        }
        if (node.isObject()) {
            if (node.has("$blob")) {
                byte[] bytes = Base64.getDecoder().decode(node.get("$blob").asText());
                if (uuidColumn && bytes.length == 16 && env.vendor()
                        != io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor.SQLITE) {
                    // 目标是按 GUID 字符串存储的方言（SQL Server UNIQUEIDENTIFIER 等）
                    ps.setObject(index, DatabaseEnvironment.uuidFromBytes(bytes).toString());
                } else {
                    ps.setObject(index, bytes);
                }
                return;
            }
            if (node.has("$ts")) {
                ps.setTimestamp(index, new Timestamp(node.get("$ts").asLong()));
                return;
            }
        }
        switch (node.getNodeType()) {
            case STRING -> ps.setString(index, node.asText());
            case NUMBER -> ps.setObject(index, node.numberValue());
            case BOOLEAN -> ps.setBoolean(index, node.asBoolean());
            default -> ps.setObject(index, node.asText());
        }
    }

    /**
     * 目标表中"疑似 UUID"的列（16 字节 binary 或 uniqueidentifier 类型名）。
     */
    private Set<String> uuidLikeColumns(Connection conn, String table) throws SQLException {
        Set<String> result = new java.util.HashSet<>();
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(conn.getCatalog(), env.schema(), table, null)) {
            while (rs.next()) {
                String typeName = rs.getString("TYPE_NAME");
                int size = rs.getInt("COLUMN_SIZE");
                String col = rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                if (typeName == null) {
                    continue;
                }
                String t = typeName.toLowerCase(Locale.ROOT);
                if (t.contains("uniqueidentifier") || (t.contains("blob") && size == 16)) {
                    result.add(col);
                }
            }
        }
        return result;
    }

    // ── 状态 / 中止 ─────────────────────────────────────────────

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maintenanceMode", maintenanceMode.isActive());
        result.put("exportFileExists", Files.exists(exportFile()));
        result.put("exportFile", exportFile().toAbsolutePath().toString());
        result.put("vendor", env.vendor().name());
        return result;
    }

    public void abort() {
        maintenanceMode.disable();
        log.warn("迁移已中止，只读维护状态解除");
    }

    // ── 工具 ────────────────────────────────────────────────────

    private Path exportFile() {
        return Paths.get("data", EXPORT_FILE_NAME);
    }

    private List<String> discoverTables(Connection conn) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = md.getTables(conn.getCatalog(), env.schema(), null, new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name == null) {
                    continue;
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.startsWith("sqlite_") || lower.equals("flyway_schema_history")) {
                    continue;
                }
                // 达梦内部约束辅助表不可直接读写
                if (lower.startsWith("sref_")) {
                    continue;
                }
                tables.add(name);
            }
        }
        return tables;
    }

    private long countRows(Connection conn, List<String> tables) throws SQLException {
        long total = 0;
        for (String table : tables) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + env.qualify(table))) {
                if (rs.next()) {
                    total += rs.getLong(1);
                }
            }
        }
        return total;
    }
}
