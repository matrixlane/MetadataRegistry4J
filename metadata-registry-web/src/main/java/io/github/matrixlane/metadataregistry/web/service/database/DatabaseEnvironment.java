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

    /** 扩展(EXT)表集:归 ext schema;与 orm/orm-team.xml 清单一致(OrmTeamXmlTest 守卫;含 V20260901015 补迁的 interop 两表)。 */
    private static final java.util.Set<String> EXT_TABLES = java.util.Set.of(
            "classification_scheme", "classification_node",
            "identifier_strategy_config", "identifier_sequence_registry", "identifier_version_history",
            "scoped_identifier_lifecycle", "namespace_identifier_settings",
            "contact_account_binding", "language_governance", "naming_convention_rules",
            "ai_generation_record",
            "account",
            "mapping_scheme", "mapping_subscription", "fhir_element_mapping", "interop_mapping",
            "interop_data_set", "interop_data_element",
            "item_link", "merge_request", "merge_request_item",
            "namespace_permission", "registry_node", "sync_watermark", "system_setting", "version_history");

    private final DatabaseVendor vendor;
    private final String schema;
    private final String extSchema;

    public DatabaseEnvironment(
            @Value("${DB_VENDOR:}") String vendor,
            @Value("${spring.datasource.url:}") String jdbcUrl,
            @Value("${DB_DEFAULT_SCHEMA:}") String schema,
            @Value("${DB_EXT_SCHEMA:}") String extSchema) {
        this.vendor = DatabaseVendor.resolve(vendor, jdbcUrl);
        String explicit = (schema == null || schema.isBlank()) ? null : schema.trim();
        this.schema = switch (this.vendor) {
            case SQLITE -> null;
            case DM -> explicit != null ? explicit : "MDR";
            case SQLSERVER -> explicit != null ? explicit : "mdr";
        };
        String extExplicit = (extSchema == null || extSchema.isBlank()) ? null : extSchema.trim();
        this.extSchema = switch (this.vendor) {
            case SQLITE -> null;
            case DM -> extExplicit != null ? extExplicit : "EXT";
            case SQLSERVER -> extExplicit != null ? extExplicit : "ext";
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
        return qualify(schema, tableName);
    }

    /** 按显式 schema 限定表引用;schema 为 null(SQLite)时仅引用表名。 */
    public String qualify(String schemaName, String tableName) {
        String quoted = "\"" + tableName + "\"";
        return schemaName == null ? quoted : "\"" + schemaName + "\"." + quoted;
    }

    /** 扩展 schema(SQLite 为 null)。 */
    public String extSchema() {
        return extSchema;
    }

    /** EXT 表的限定引用。 */
    public String qualifyExt(String tableName) {
        return qualify(extSchema, tableName);
    }

    /** 表所属 schema(EXT 表返回 extSchema,其余返回 schema;JDBC 元数据查询用)。 */
    public String schemaOf(String tableName) {
        return EXT_TABLES.contains(tableName.toLowerCase(java.util.Locale.ROOT)) ? extSchema : schema;
    }

    /** 按表名自动选择 schema:EXT 表走 {@link #qualifyExt},其余走 {@link #qualify}。 */
    public String qualifyAuto(String tableName) {
        return EXT_TABLES.contains(tableName.toLowerCase(java.util.Locale.ROOT))
                ? qualifyExt(tableName)
                : qualify(tableName);
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
