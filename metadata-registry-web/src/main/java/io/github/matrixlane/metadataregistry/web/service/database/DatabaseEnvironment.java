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

    /**
     * 扩展(EXT)表集:归 ext schema;与 orm/orm-team.xml 清单一致(OrmTeamXmlTest 守卫;含 V20260901015 补迁的 interop 两表)。
     *
     * <p><b>同名表警示</b>:V20260919001 语言名称扩展与 V20261008001 RA Primary 标志卫星
     * 均与标准表同名——团队库物理名均为 {@code language_identification} /
     * {@code registration_authority}(仅以 schema 区分),SQLite 下 EXT 表物理名为
     * {@code ext_language_identification} / {@code ext_registration_authority}。
     * 因此本清单中的裸键仅代表 EXT 表;标准表 {@code mdr.language_identification} /
     * {@code mdr.registration_authority} 必须走 {@link #qualify(String)} /
     * {@link #qualifyStandard(String)},禁止走 {@link #schemaOf(String)} /
     * {@link #qualifyAuto(String)}(后者会把同名标准表路由到 ext schema)。
     */
    private static final java.util.Set<String> EXT_TABLES = java.util.Set.of(
            "classification_scheme", "classification_node",
            "identifier_strategy_config", "identifier_sequence_registry", "identifier_version_history",
            "scoped_identifier_lifecycle", "namespace_identifier_settings",
            "contact_account_binding", "language_governance", "naming_convention_rules",
            // V20260919001 语言名称扩展:团队库与标准表同名(language_identification,schema 区分);
            // SQLite 无 schema,物理名加 ext_ 前缀(ext_language_identification)。
            "language_identification", "ext_language_identification",
            // V20261008001 RA Primary 标志卫星:团队库与标准表同名(registration_authority,schema 区分);
            // SQLite 物理名加 ext_ 前缀(ext_registration_authority)。标准表禁止走 qualifyAuto。
            "ext_registration_authority",
            "ai_generation_record",
            "account",
            "fhir_element_mapping", "interop_mapping",
            "interop_data_set", "interop_data_element",
            "item_link", "merge_request", "merge_request_item",
            "namespace_permission", "registry_node", "sync_watermark", "system_setting", "version_history",
            // V20261009001 注册状态流转审批工作流（非标准表）迁 ext：orm-team.xml 登记 schema="ext"
            // （仅实体类）；registration_transition_rule_approver 为 @ElementCollection 集合表、
            // 无法经 orm.xml 登记，由本清单兜底——RegistrationAuthorityDeletionService 经 qualifyAuto 访问。
            "registration_transition_rule", "registration_transition_approval",
            "registration_transition_rule_approver");

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

    /**
     * 表所属 schema(EXT 表返回 extSchema,其余返回 schema;JDBC 元数据查询用)。
     *
     * <p>仅用于"裸表名确定属于 EXT 表"的语境;同名标准表(如 language_identification)
     * 会被本方法路由到 ext schema,标准表语境请直接使用 {@link #schema()}。
     */
    public String schemaOf(String tableName) {
        return EXT_TABLES.contains(tableName.toLowerCase(java.util.Locale.ROOT)) ? extSchema : schema;
    }

    /**
     * 按表名自动选择 schema:EXT 表走 {@link #qualifyExt},其余走 {@link #qualify}。
     *
     * <p>仅用于"裸表名确定属于 EXT 表"的语境;同名标准表(如 language_identification)
     * 会被本方法限定到 ext schema,标准表语境请使用 {@link #qualify(String)} / {@link #qualifyStandard(String)}。
     */
    public String qualifyAuto(String tableName) {
        return EXT_TABLES.contains(tableName.toLowerCase(java.util.Locale.ROOT))
                ? qualifyExt(tableName)
                : qualify(tableName);
    }

    /**
     * 标准 schema 语境的表限定:永远限定到主 schema,不做 EXT 路由。
     *
     * <p>供调用方已知表属于标准 schema 时使用——典型场景是迁移导入:导出文件中的裸表名
     * 均来自主 schema(见 MigrationService 导出),其中 {@code language_identification}
     * 指标准表而非同名 EXT 表,若走 {@link #qualifyAuto(String)} 会被错误路由到 ext schema。
     */
    public String qualifyStandard(String tableName) {
        return qualify(schema, tableName);
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
