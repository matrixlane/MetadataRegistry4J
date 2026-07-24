package io.github.matrixlane.metadataregistry.web.service.database;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Resolved once at startup: which database vendor we run on and which
 * schema/namespace the application tables live in. Centralizes the rules so
 * that no business code needs to know either.
 */
@Component
public class DatabaseEnvironment {

    private final DatabaseVendor vendor;
    private final String schema;

    public DatabaseEnvironment(
            @Value("${DB_VENDOR:}") String vendor,
            @Value("${spring.datasource.url:}") String jdbcUrl,
            @Value("${DB_DEFAULT_SCHEMA:}") String schema) {
        this.vendor = DatabaseVendor.resolve(vendor, jdbcUrl);
        String explicit = (schema == null || schema.isBlank()) ? null : schema.trim();
        this.schema = switch (this.vendor) {
            case SQLITE -> null;
            case DM -> explicit != null ? explicit : "MDR";
            case SQLSERVER -> explicit != null ? explicit : "mdr";
        };
    }

    public DatabaseVendor vendor() {
        return vendor;
    }

    /**
     * Schema/owner used for JDBC metadata queries and raw SQL qualification.
     * Null means "database default" (SQLite has no schema concept).
     */
    public String schema() {
        return schema;
    }

    /**
     * Fully qualified, double-quoted table reference for raw JDBC statements
     * (portable across SQL Server, SQLite and Dameng).
     */
    public String qualify(String tableName) {
        String quoted = "\"" + tableName + "\"";
        return schema == null ? quoted : "\"" + schema + "\"." + quoted;
    }

    /**
     * 供原生 JDBC 语句绑定 UUID 参数：SQLite（BLOB 存储）需要 byte[16]，
     * 达梦（RAW(16) 存储）同样需要 byte[16]；SQL Server 接受标准字符串形式。
     */
    public Object toJdbcUuid(UUID uuid) {
        return vendor == DatabaseVendor.SQLSERVER ? uuid.toString() : uuidToBytes(uuid);
    }

    /**
     * 从原生 JDBC 结果集中还原 UUID：兼容驱动返回 UUID / byte[16] / 字符串三种形态。
     */
    public static UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID u) {
            return u;
        }
        if (value instanceof byte[] bytes) {
            return uuidFromBytes(bytes);
        }
        return UUID.fromString(value.toString());
    }

    public static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    public static UUID uuidFromBytes(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }
}
