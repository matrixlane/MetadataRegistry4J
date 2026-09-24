package io.github.matrixlane.metadataregistry.web.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 迁移 V20260919002(sqlite 方言)端到端验证。
 *
 * <p>设计说明:沿用 {@link RegistrationStatusMigrationTest} 的迁移测试模式(仓库无
 * Testcontainers 依赖)。本测试用 sqlite-jdbc 在内存库中建出 V20260919001 的
 * ext_language_identification 与最小 language_identification 结构,预置 22 条
 * 语言代码,然后逐条实际执行 V20260919002 脚本的 INSERT...SELECT...NOT EXISTS,
 * 验证:22 条名称行按 language_identifier 正确关联、重复执行幂等、已有行不被覆盖。</p>
 */
class LanguageNameSeedMigrationTest {

    private static final String SCRIPT = "db/migration/sqlite/V20260919002__Seed_language_identification_names.sql";

    /** 与迁移脚本一致的 22 条预置(标识 → 语言名称/地区名称)。 */
    private static final Map<String, String[]> SEED = Map.ofEntries(
            Map.entry("zh-CN", new String[]{"中文", "中国"}),
            Map.entry("en-US", new String[]{"英语", "美国"}),
            Map.entry("ja-JP", new String[]{"日语", "日本"}),
            Map.entry("en-GB", new String[]{"英语", "英国"}),
            Map.entry("en-CA", new String[]{"英语", "加拿大"}),
            Map.entry("fr-CA", new String[]{"法语", "加拿大"}),
            Map.entry("fr-FR", new String[]{"法语", "法国"}),
            Map.entry("de-DE", new String[]{"德语", "德国"}),
            Map.entry("ru-RU", new String[]{"俄语", "俄罗斯"}),
            Map.entry("ko-KR", new String[]{"韩语", "韩国"}),
            Map.entry("it-IT", new String[]{"意大利语", "意大利"}),
            Map.entry("es-ES", new String[]{"西班牙语", "西班牙"}),
            Map.entry("es-AR", new String[]{"西班牙语", "阿根廷"}),
            Map.entry("es-MX", new String[]{"西班牙语", "墨西哥"}),
            Map.entry("pt-BR", new String[]{"葡萄牙语", "巴西"}),
            Map.entry("hi-IN", new String[]{"印地语", "印度"}),
            Map.entry("en-IN", new String[]{"英语", "印度"}),
            Map.entry("en-AU", new String[]{"英语", "澳大利亚"}),
            Map.entry("en-ZA", new String[]{"英语", "南非"}),
            Map.entry("ar-SA", new String[]{"阿拉伯语", "沙特阿拉伯"}),
            Map.entry("tr-TR", new String[]{"土耳其语", "土耳其"}),
            Map.entry("id-ID", new String[]{"印度尼西亚语", "印度尼西亚"}));

    @Test
    @DisplayName("V20260919002:22 条名称行按标识关联插入,数据与脚本一致")
    void seedsAll22NameRows() throws Exception {
        try (Connection conn = newDb()) {
            seedLanguages(conn);
            executeScript(conn);

            Map<String, String[]> actual = readExtensionNames(conn);
            assertThat(actual).containsExactlyInAnyOrderEntriesOf(SEED);
        }
    }

    @Test
    @DisplayName("V20260919002:重复执行幂等,不重复插入")
    void secondRunIsNoOp() throws Exception {
        try (Connection conn = newDb()) {
            seedLanguages(conn);
            executeScript(conn);
            executeScript(conn);

            assertThat(countExtensionRows(conn)).isEqualTo(22);
        }
    }

    @Test
    @DisplayName("V20260919002:既有扩展行(含用户编辑值)不被预置覆盖")
    void doesNotOverwriteExistingRows() throws Exception {
        try (Connection conn = newDb()) {
            seedLanguages(conn);
            // 模拟既有库:zh-CN 已有扩展行(初始化器种下的),用户已编辑为自定义值
            try (Statement st = conn.createStatement()) {
                st.execute("INSERT INTO ext_language_identification (id, language_id, language_name, territory_name) "
                        + "SELECT randomblob(16), id, '中文(用户编辑)', '中国(用户编辑)' "
                        + "FROM language_identification WHERE language_identifier = 'zh-CN'");
            }
            executeScript(conn);

            Map<String, String[]> actual = readExtensionNames(conn);
            assertThat(actual).hasSize(22);
            assertThat(actual.get("zh-CN")).containsExactly("中文(用户编辑)", "中国(用户编辑)");
            assertThat(actual.get("en-US")).containsExactly("英语", "美国");
        }
    }

    @Test
    @DisplayName("V20260919002:标准表缺少某标识时,该条预置不插入")
    void skipsMissingLanguageIdentifier() throws Exception {
        try (Connection conn = newDb()) {
            seedLanguages(conn);
            // 模拟语言代码未播种的全新库(语言 UUID 运行时生成,迁移先于播种执行)
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM language_identification WHERE language_identifier = 'zh-CN'");
            }
            executeScript(conn);

            Map<String, String[]> actual = readExtensionNames(conn);
            assertThat(actual).hasSize(21);
            assertThat(actual).doesNotContainKey("zh-CN");
        }
    }

    @Test
    @DisplayName("V20260919001(sqlite):表已存在时(ddl-auto 先建)可安全重入")
    void v20260919001IsIdempotentAfterDdlAuto() throws Exception {
        String ddl = """
                CREATE TABLE IF NOT EXISTS ext_language_identification (
                    id blob not null,
                    language_id blob not null,
                    language_name varchar(255),
                    territory_name varchar(255),
                    primary key (id)
                );
                CREATE UNIQUE INDEX IF NOT EXISTS uk_liext_language ON ext_language_identification (language_id);
                """;
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement st = conn.createStatement()) {
            // 第一次:空库执行 Flyway 迁移脚本
            for (String statement : ddl.split(";")) {
                if (!statement.isBlank()) {
                    st.execute(statement);
                }
            }
            // 第二次:模拟 Hibernate ddl-auto 已建表后 Flyway 重入(IF NOT EXISTS 守卫)
            for (String statement : ddl.split(";")) {
                if (!statement.isBlank()) {
                    st.execute(statement);
                }
            }
            ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE name = 'ext_language_identification'");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    // =========================================================================
    // V20260919003(sqlite):一体化预置(固定 UUID 语言代码 + 名称),恢复出厂重放同一脚本
    // =========================================================================

    private static final String SCRIPT_003 = "db/migration/sqlite/V20260919003__Seed_builtin_languages.sql";

    private void executeScript(Connection conn) throws IOException, SQLException {
        executeScript(conn, SCRIPT, 22);
    }

    private void executeScript003(Connection conn) throws IOException, SQLException {
        executeScript(conn, SCRIPT_003, 44);
    }

    /** 与 {@link #executeScript(Connection)} 相同,但可指定脚本路径并断言语句数。 */
    private void executeScript(Connection conn, String scriptPath, int expectedStatements) throws IOException, SQLException {
        String content;
        try (var in = getClass().getClassLoader().getResourceAsStream(scriptPath)) {
            assertThat(in).as("迁移脚本应存在于 classpath: %s", scriptPath).isNotNull();
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                statements.add(current.toString());
                current.setLength(0);
            }
        }
        assertThat(statements).as("%s 语句数", scriptPath).hasSize(expectedStatements);
        try (Statement st = conn.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
        }
    }

    @Test
    @DisplayName("V20260919003:空库(先跑 002 无语言行)执行后 22 条代码(固定 UUID)+22 条名称")
    void v20260919003SeedsCodesAndNamesOnFreshDb() throws Exception {
        try (Connection conn = newDb()) {
            executeScript(conn); // V20260919002:无语言行,0 插入
            executeScript003(conn);

            assertThat(countRows(conn, "language_identification")).isEqualTo(22);
            assertThat(countRows(conn, "ext_language_identification")).isEqualTo(22);
            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery("SELECT lower(hex(id)) FROM language_identification"
                            + " WHERE language_identifier = 'zh-CN'")) {
                rs.next();
                assertThat(rs.getString(1)).isEqualTo("228df1d4d1fb4ec3a4ccdd58acd5f531");
            }
            Map<String, String[]> actual = readExtensionNames(conn);
            assertThat(actual).containsExactlyInAnyOrderEntriesOf(SEED);
        }
    }

    @Test
    @DisplayName("V20260919003:既有库(22 语言已存在)全部幂等跳过")
    void v20260919003SkipsExistingCodesAndNames() throws Exception {
        try (Connection conn = newDb()) {
            seedLanguages(conn);
            executeScript(conn); // 既有库名称补种:22 行
            executeScript003(conn); // 一体化预置:全部防重跳过

            assertThat(countRows(conn, "language_identification")).isEqualTo(22);
            assertThat(countRows(conn, "ext_language_identification")).isEqualTo(22);
            // 名称仍来自 002 的补种值(未被 003 覆盖)
            Map<String, String[]> actual = readExtensionNames(conn);
            assertThat(actual).containsExactlyInAnyOrderEntriesOf(SEED);
        }
    }

    @Test
    @DisplayName("V20260919003:重复执行幂等")
    void v20260919003SecondRunIsNoOp() throws Exception {
        try (Connection conn = newDb()) {
            executeScript003(conn);
            executeScript003(conn);

            assertThat(countRows(conn, "language_identification")).isEqualTo(22);
            assertThat(countRows(conn, "ext_language_identification")).isEqualTo(22);
        }
    }

    private int countRows(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Connection newDb() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            // 最小 language_identification 结构(迁移仅需 id + 标识组成列)
            st.execute("CREATE TABLE language_identification ("
                    + "id blob not null, language_identifier varchar(255),"
                    + " script_identifier varchar(255), geopolitical_territory_identifier varchar(255),"
                    + " primary key (id))");
            // 与 V20260919001 sqlite 脚本一致的扩展表结构
            st.execute("CREATE TABLE ext_language_identification ("
                    + "id blob not null, language_id blob not null,"
                    + " language_name varchar(255), territory_name varchar(255), primary key (id))");
            st.execute("CREATE UNIQUE INDEX uk_liext_language ON ext_language_identification (language_id)");
        }
        return conn;
    }

    private void seedLanguages(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO language_identification (id, language_identifier) VALUES (randomblob(16), ?)")) {
            for (String tag : SEED.keySet()) {
                ps.setString(1, tag);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private Map<String, String[]> readExtensionNames(Connection conn) throws SQLException {
        Map<String, String[]> result = new HashMap<>();
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT l.language_identifier, e.language_name, e.territory_name"
                                + " FROM ext_language_identification e"
                                + " JOIN language_identification l ON l.id = e.language_id")) {
            while (rs.next()) {
                result.put(rs.getString(1),
                        new String[]{rs.getString(2), rs.getString(3)});
            }
        }
        return result;
    }

    private int countExtensionRows(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM ext_language_identification")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
