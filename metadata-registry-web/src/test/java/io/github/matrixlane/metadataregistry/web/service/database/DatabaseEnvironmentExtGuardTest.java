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
 * 导致原生 JDBC 语句(如恢复出厂设置的 DELETE FROM)按错误 schema 限定表名。</p>
 */
class DatabaseEnvironmentExtGuardTest {

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
            assertThat(env.schemaOf(table))
                    .as("表 [%s] 的 schemaOf 应路由到 ext schema", table)
                    .isEqualTo(env.extSchema());
            assertThat(env.qualifyAuto(table))
                    .as("表 [%s] 的 qualifyAuto 应限定到 ext schema", table)
                    .isEqualTo("\"ext\".\"" + table + "\"");
        }
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
