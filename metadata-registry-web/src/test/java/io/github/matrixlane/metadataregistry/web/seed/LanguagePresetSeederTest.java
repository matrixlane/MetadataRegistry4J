package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LanguagePresetSeeder} 集成测试：以真实 SQLite 内存库执行 classpath 上的
 * V20260919003 预置脚本（与 Flyway 迁移共用同一份文件，验证"单一数据源"与幂等性）。
 */
class LanguagePresetSeederTest {

    private LanguagePresetSeeder newSeeder(javax.sql.DataSource dataSource) {
        DatabaseEnvironment env = new DatabaseEnvironment("", "jdbc:sqlite:memdb", "", "");
        return new LanguagePresetSeeder(new JdbcTemplate(dataSource), env, new DefaultResourceLoader());
    }

    private javax.sql.DataSource newDb() {
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setJdbcUrl("jdbc:sqlite::memory:");
        // 单连接常驻:SQLite 内存库随最后一个连接关闭而消失
        ds.setMaximumPoolSize(1);
        ds.setMinimumIdle(1);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE language_identification ("
                + "id blob not null, language_identifier varchar(255),"
                + " script_identifier varchar(255), geopolitical_territory_identifier varchar(255),"
                + " private_use_qualifier varchar(255), primary key (id))");
        jdbc.execute("CREATE TABLE ext_language_identification ("
                + "id blob not null, language_id blob not null,"
                + " language_name varchar(255), territory_name varchar(255), primary key (id))");
        jdbc.execute("CREATE UNIQUE INDEX uk_liext_language ON ext_language_identification (language_id)");
        return ds;
    }

    @Test
    @DisplayName("reapplyPreset:标准表带漂移的 NOT NULL language_id 列时跳过 INSERT 不抛异常,UPDATE 仍生效")
    void toleratesDriftedLegacySchema() throws Exception {
        // 远古 ddl-auto 建库的漂移形态：标准表残留 NOT NULL 且无默认值的 language_id 列，
        // 预置 INSERT 的列清单不含该列，重放即 SQLITE_CONSTRAINT_NOTNULL（2026-10-19/20
        // 生产库实测）。期望：INSERT 被跳过、启动不被阻断，UPDATE 语句照常生效。
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setJdbcUrl("jdbc:sqlite::memory:");
        ds.setMaximumPoolSize(1);
        ds.setMinimumIdle(1);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE language_identification ("
                + "id blob not null, language_id blob not null,"
                + " language_identifier varchar(255), script_identifier varchar(255),"
                + " geopolitical_territory_identifier varchar(255), primary key (id))");
        jdbc.execute("CREATE TABLE ext_language_identification ("
                + "id blob not null, language_id blob not null,"
                + " language_name varchar(255), territory_name varchar(255), primary key (id))");

        newSeeder(ds).reapplyPreset();

        // 漂移表无插入（INSERT 被跳过），但名称 UPDATE 已生效
        Integer languages = jdbc.queryForObject("SELECT COUNT(*) FROM language_identification", Integer.class);
        assertThat(languages).isZero();
        Integer names = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ext_language_identification WHERE language_name IS NOT NULL", Integer.class);
        assertThat(names).isZero();
        ds.close();
    }

    @Test
    @DisplayName("reapplyPreset:空库执行脚本清单得到 22 条语言代码与 22 条名称,zh-CN 名称为简体中文")
    void seeds22LanguagesAndNamesFromScript() throws Exception {
        javax.sql.DataSource ds = newDb();
        newSeeder(ds).reapplyPreset();

        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Integer languages = jdbc.queryForObject("SELECT COUNT(*) FROM language_identification", Integer.class);
        Integer names = jdbc.queryForObject("SELECT COUNT(*) FROM ext_language_identification", Integer.class);
        assertThat(languages).isEqualTo(22);
        assertThat(names).isEqualTo(22);
        String zhScript = jdbc.queryForObject(
                "SELECT l.script_identifier FROM language_identification l WHERE l.language_identifier = 'zh-CN'",
                String.class);
        String zhTerritory = jdbc.queryForObject(
                "SELECT l.geopolitical_territory_identifier FROM language_identification l"
                        + " WHERE l.language_identifier = 'zh-CN'", String.class);
        String zhName = jdbc.queryForObject(
                "SELECT e.language_name FROM ext_language_identification e"
                        + " JOIN language_identification l ON l.id = e.language_id"
                        + " WHERE l.language_identifier = 'zh-CN'", String.class);
        String zhTerritoryName = jdbc.queryForObject(
                "SELECT e.territory_name FROM ext_language_identification e"
                        + " JOIN language_identification l ON l.id = e.language_id"
                        + " WHERE l.language_identifier = 'zh-CN'", String.class);
        assertThat(zhScript).isEqualTo("Hans");
        assertThat(zhTerritory).isEqualTo("CN");
        // 简体中文:V20260919003 播种「中文」后由 localize-language-names.sql 口径修正
        assertThat(zhName).isEqualTo("简体中文");
        assertThat(zhTerritoryName).isEqualTo("中国");
        // 固定 UUID:与 V20260919003 sqlite 脚本中的字面量一致
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT lower(hex(id)) FROM language_identification"
                        + " WHERE language_identifier = 'zh-CN'")) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("228df1d4d1fb4ec3a4ccdd58acd5f531");
        }
    }

    @Test
    @DisplayName("reapplyPreset:重复执行幂等(代码按标识防重、名称按 language_id 防重)")
    void reapplyIsIdempotent() {
        javax.sql.DataSource ds = newDb();
        LanguagePresetSeeder seeder = newSeeder(ds);
        seeder.reapplyPreset();
        seeder.reapplyPreset();

        JdbcTemplate jdbc = new JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM language_identification", Integer.class)).isEqualTo(22);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ext_language_identification", Integer.class)).isEqualTo(22);
    }

    @Test
    @DisplayName("reapplyPreset:既有语言代码(其他 UUID)不被覆盖,仅补名称")
    void doesNotOverwriteExistingCodes() {
        javax.sql.DataSource ds = newDb();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        // 模拟出厂后由 LanguageInitializer 以随机 UUID 重建的 zh-CN
        jdbc.update("INSERT INTO language_identification (id, language_identifier, script_identifier,"
                        + " geopolitical_territory_identifier) VALUES (randomblob(16), 'zh-CN', 'Hans', 'CN')");
        newSeeder(ds).reapplyPreset();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM language_identification", Integer.class)).isEqualTo(22);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ext_language_identification e JOIN language_identification l"
                        + " ON l.id = e.language_id WHERE l.language_identifier = 'zh-CN'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("reapplyPreset:既有「中文」口径经重放修正为「简体中文」(V20260929001 活副本通道)")
    void localizesLegacyZhCnName() {
        javax.sql.DataSource ds = newDb();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        newSeeder(ds).reapplyPreset();
        // 模拟迁移应用前的旧口径
        jdbc.update("UPDATE ext_language_identification SET language_name = '中文'"
                + " WHERE language_name = '简体中文'");
        newSeeder(ds).reapplyPreset();

        String zhName = jdbc.queryForObject(
                "SELECT e.language_name FROM ext_language_identification e"
                        + " JOIN language_identification l ON l.id = e.language_id"
                        + " WHERE l.language_identifier = 'zh-CN'", String.class);
        assertThat(zhName).isEqualTo("简体中文");
    }

    @Test
    @DisplayName("splitStatements:跳过注释与空行,按分号切出全部语句")
    void splitsStatementsSkippingComments() {
        List<String> statements = LanguagePresetSeeder.splitStatements(
                "-- 注释\n\nINSERT INTO t (a) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM t);\n-- 尾注\n");
        assertThat(statements).hasSize(1);
        assertThat(statements.get(0)).contains("INSERT INTO t");
    }
}
