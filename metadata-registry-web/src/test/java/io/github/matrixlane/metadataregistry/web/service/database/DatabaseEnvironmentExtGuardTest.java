package io.github.matrixlane.metadataregistry.web.service.database;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXT 表清单漂移守卫:orm-team.xml 登记到 ext schema 的每个实体,
 * 其表名都必须被 {@link DatabaseEnvironment} 识别为 EXT 表(schemaOf/qualifyAuto 走 ext schema)。
 *
 * <p>防止新增 EXT 实体时只改 orm-team.xml 而遗漏 EXT_TABLES,
 * 导致原生 JDBC 语句(如恢复出厂设置的 DELETE FROM)按错误 schema 限定表名。
 *
 * <p>例外:与标准表同名的 EXT 卫星表(裸键须继续路由标准 schema——
 * {@code MetadataSearchService}/{@link RegistrationAuthorityDeletionService} 等以
 * qualifyAuto 引用标准表),其 EXT 侧一律经 {@code qualifyExt} 限定,
 * SQLite 物理名(ext_ 前缀)仍须在 EXT_TABLES 中(同 language_identification 口径)。
 */
class DatabaseEnvironmentExtGuardTest {

    /** 同名卫星表裸键保留给标准表的路由锁定(V20261008001 RegistrationAuthorityExtension)。 */
    private static final java.util.Set<String> SAME_NAME_SATELLITES = java.util.Set.of("registration_authority");

    @Test
    void everyOrmTeamTableIsRecognizedAsExt() throws Exception {
        List<String> tables = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream("/orm/orm-team.xml")) {
            assertThat(in).as("orm-team.xml 必须存在于类路径").isNotNull();
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            NodeList entities = doc.getDocumentElement().getElementsByTagName("entity");
            for (int i = 0; i < entities.getLength(); i++) {
                Element entity = (Element) entities.item(i);
                Element table = (Element) entity.getElementsByTagName("table").item(0);
                assertThat(table.getAttribute("schema")).isEqualTo("ext");
                tables.add(resolveTableName(Class.forName(entity.getAttribute("class"))));
            }
        }
        assertThat(tables).as("orm-team.xml 至少登记一个 EXT 实体").isNotEmpty();

        DatabaseEnvironment env = new DatabaseEnvironment("SQLSERVER", "", null, null);
        for (String table : tables) {
            if (SAME_NAME_SATELLITES.contains(table)) {
                // 同名卫星表:裸键保留给标准表(mdr 路由),EXT 侧经 qualifyExt 限定;
                // SQLite 物理名(ext_ 前缀)必须被 EXT_TABLES 识别
                assertThat(env.schemaOf(table))
                        .as("同名卫星表 [%s] 的裸键应保留给标准表", table)
                        .isEqualTo(env.schema());
                assertThat(env.qualifyExt(table))
                        .as("同名卫星表 [%s] 应可经 qualifyExt 限定到 ext schema", table)
                        .isEqualTo("\"ext\".\"" + table + "\"");
                String sqlitePhysical = "ext_" + table;
                assertThat(env.schemaOf(sqlitePhysical))
                        .as("同名卫星表 [%s] 的 SQLite 物理名应在 EXT_TABLES 中", sqlitePhysical)
                        .isEqualTo(env.extSchema());
                assertThat(env.qualifyAuto(sqlitePhysical))
                        .as("同名卫星表 [%s] 的 qualifyAuto 应限定到 ext schema", sqlitePhysical)
                        .isEqualTo("\"ext\".\"" + sqlitePhysical + "\"");
                continue;
            }
            assertThat(env.schemaOf(table))
                    .as("表 [%s] 的 schemaOf 应路由到 ext schema", table)
                    .isEqualTo(env.extSchema());
            assertThat(env.qualifyAuto(table))
                    .as("表 [%s] 的 qualifyAuto 应限定到 ext schema", table)
                    .isEqualTo("\"ext\".\"" + table + "\"");
        }
    }

    /**
     * 同名表路由锁定(回归 M2):语言扩展表与标准表同名(language_identification),
     * 裸键仅代表 EXT 表;标准 schema 语境必须走 qualifyStandard 而非 qualifyAuto/schemaOf。
     */
    @Test
    void sameNamedStandardTableMustUseQualifyStandard() {
        DatabaseEnvironment env = new DatabaseEnvironment("SQLSERVER", "", null, null);

        // EXT 路由:裸键 language_identification 指向 ext schema 的扩展表
        assertThat(env.schemaOf("language_identification")).isEqualTo(env.extSchema());
        assertThat(env.qualifyAuto("language_identification")).isEqualTo("\"ext\".\"language_identification\"");

        // 标准表语境:qualifyStandard 永远限定主 schema,不被 EXT 清单劫持
        assertThat(env.qualifyStandard("language_identification")).isEqualTo("\"mdr\".\"language_identification\"");
        assertThat(env.qualify("language_identification")).isEqualTo("\"mdr\".\"language_identification\"");
        // 其余表不受影响
        assertThat(env.qualifyStandard("data_element")).isEqualTo("\"mdr\".\"data_element\"");

        // SQLite:ext 表物理名为 ext_language_identification,qualifyStandard 仅引用裸表名
        DatabaseEnvironment sqlite = new DatabaseEnvironment("SQLITE", "jdbc:sqlite:metadata_registry.db", null, null);
        assertThat(sqlite.qualifyStandard("language_identification")).isEqualTo("\"language_identification\"");
        assertThat(sqlite.qualifyStandard("ext_language_identification")).isEqualTo("\"ext_language_identification\"");

        // V20261008001 RA Primary 卫星与标准表同名:裸键保留给标准表(与 language_identification
        // 相反——该裸键在标准表语境被 qualifyAuto 广泛使用,不得劫持),EXT 侧经 qualifyExt 限定
        assertThat(env.schemaOf("registration_authority")).isEqualTo(env.schema());
        assertThat(env.qualify("registration_authority")).isEqualTo("\"mdr\".\"registration_authority\"");
        assertThat(env.qualifyStandard("registration_authority")).isEqualTo("\"mdr\".\"registration_authority\"");
        assertThat(env.qualifyExt("registration_authority")).isEqualTo("\"ext\".\"registration_authority\"");
        assertThat(env.schemaOf("ext_registration_authority")).isEqualTo(env.extSchema());
        assertThat(sqlite.qualifyStandard("registration_authority")).isEqualTo("\"registration_authority\"");
        assertThat(sqlite.qualify("ext_registration_authority")).isEqualTo("\"ext_registration_authority\"");
    }

    private static String resolveTableName(Class<?> type) {
        jakarta.persistence.Table table = type.getAnnotation(jakarta.persistence.Table.class);
        if (table != null && !table.name().isBlank()) {
            return table.name();
        }
        // Spring 默认隐式命名:CamelCase → snake_case
        return type.getSimpleName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
