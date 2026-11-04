package io.github.matrixlane.metadataregistry.web.config;

import com.zaxxer.hikari.HikariDataSource;
import io.github.matrixlane.metadataregistry.web.seed.BaselineScriptPresetService;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LegacyLanguageSchemaRepairer} 集成测试：以真实 SQLite 内存库复刻三代
 * 已实证的漂移形态（本机 language_id+names 代、enabled 代、缺列旧代），验证漂移
 * 签名判定、卫星表数据归位、标准表重建（id 保留）、幂等与方言守卫。
 */
class LegacyLanguageSchemaRepairerTest {

    private static final List<String> STANDARD_COLUMNS = List.of(
            "id", "language_identifier", "script_identifier",
            "geopolitical_territory_identifier", "private_use_qualifier");

    private HikariDataSource newDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setJdbcUrl("jdbc:sqlite::memory:");
        // 单连接常驻:SQLite 内存库随最后一个连接关闭而消失
        ds.setMaximumPoolSize(1);
        ds.setMinimumIdle(1);
        return ds;
    }

    private LegacyLanguageSchemaRepairer newRepairer(javax.sql.DataSource ds, DatabaseEnvironment env) {
        TransactionTemplate txTemplate = new TransactionTemplate(new DataSourceTransactionManager(ds));
        return new LegacyLanguageSchemaRepairer(new JdbcTemplate(ds), env, txTemplate);
    }

    private DatabaseEnvironment sqliteEnv() {
        return new DatabaseEnvironment("", "jdbc:sqlite:memdb", "", "");
    }

    private List<String> columnsOf(JdbcTemplate jdbc, String table) {
        List<Map<String, Object>> rows = jdbc.queryForList("PRAGMA table_info(" + table + ")");
        return rows.stream().map(r -> (String) r.get("name")).toList();
    }

    private void createExtTable(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS ext_language_identification ("
                + "id blob not null, language_id blob not null,"
                + " language_name varchar(255), territory_name varchar(255), primary key (id))");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_liext_language ON ext_language_identification (language_id)");
    }

    private void createGovernanceTable(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS language_governance ("
                + "id blob not null, language_id blob not null,"
                + " enabled boolean not null default true, primary key (id))");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_lg_language ON language_governance (language_id)");
    }

    @Test
    @DisplayName("本机代漂移（language_id+names，含数据行）：重建为标准 5 列、名称归位 ext、id 保留、二次 no-op")
    void repairsLocalGenerationDriftWithData() {
        HikariDataSource ds = newDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        // 本机 2026-10-20 实证形态
        jdbc.execute("CREATE TABLE language_identification ("
                + "id blob not null, language_id blob not null unique,"
                + " language_name varchar(255), territory_name varchar(255),"
                + " geopolitical_territory_identifier varchar(255), language_identifier varchar(255),"
                + " private_use_qualifier varchar(255), script_identifier varchar(255),"
                + " primary key (id))");
        createExtTable(jdbc);
        createGovernanceTable(jdbc);
        byte[] rowId = UUID.randomUUID().toString().replace("-", "").getBytes();
        jdbc.update("INSERT INTO language_identification (id, language_id, language_name, territory_name,"
                        + " language_identifier, script_identifier, geopolitical_territory_identifier) "
                        + "VALUES (?, ?, '中文', '中国', 'zh-CN', 'Hans', 'CN')",
                rowId, UUID.randomUUID().toString().replace("-", "").getBytes());

        LegacyLanguageSchemaRepairer repairer = newRepairer(ds, sqliteEnv());
        repairer.run(null);

        assertThat(columnsOf(jdbc, "language_identification")).containsExactlyInAnyOrderElementsOf(STANDARD_COLUMNS);
        List<Map<String, Object>> extRows = jdbc.queryForList(
                "SELECT language_id, language_name, territory_name FROM ext_language_identification");
        assertThat(extRows).hasSize(1);
        assertThat((byte[]) extRows.get(0).get("language_id")).isEqualTo(rowId);
        assertThat(extRows.get(0).get("language_name")).isEqualTo("中文");
        assertThat(extRows.get(0).get("territory_name")).isEqualTo("中国");
        // 原行保留、遗留列已消失
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT language_identifier, script_identifier FROM language_identification WHERE id = ?", rowId);
        assertThat(row.get("language_identifier")).isEqualTo("zh-CN");

        // 二次运行 no-op：结构不变、ext 不重复
        repairer.run(null);
        assertThat(columnsOf(jdbc, "language_identification")).containsExactlyInAnyOrderElementsOf(STANDARD_COLUMNS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ext_language_identification", Integer.class)).isOne();
        ds.close();
    }

    @Test
    @DisplayName("enabled 列迁入 language_governance：coalesce(null,true)、NOT EXISTS 守卫不重复")
    void migratesEnabledColumnToGovernance() {
        HikariDataSource ds = newDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE language_identification ("
                + "id blob not null, language_identifier varchar(255), script_identifier varchar(255),"
                + " geopolitical_territory_identifier varchar(255), private_use_qualifier varchar(255),"
                + " enabled boolean, primary key (id))");
        createGovernanceTable(jdbc);
        byte[] enabledId = UUID.randomUUID().toString().replace("-", "").getBytes();
        byte[] disabledId = UUID.randomUUID().toString().replace("-", "").getBytes();
        byte[] nullEnabledId = UUID.randomUUID().toString().replace("-", "").getBytes();
        jdbc.update("INSERT INTO language_identification (id, language_identifier, enabled) VALUES (?, 'zh-CN', true)", enabledId);
        jdbc.update("INSERT INTO language_identification (id, language_identifier, enabled) VALUES (?, 'en-US', false)", disabledId);
        jdbc.update("INSERT INTO language_identification (id, language_identifier, enabled) VALUES (?, 'ja-JP', null)", nullEnabledId);

        LegacyLanguageSchemaRepairer repairer = newRepairer(ds, sqliteEnv());
        repairer.run(null);

        assertThat(jdbc.queryForObject(
                "SELECT enabled FROM language_governance WHERE language_id = ?", Boolean.class, enabledId)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT enabled FROM language_governance WHERE language_id = ?", Boolean.class, disabledId)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT enabled FROM language_governance WHERE language_id = ?", Boolean.class, nullEnabledId)).isTrue();
        assertThat(columnsOf(jdbc, "language_identification")).containsExactlyInAnyOrderElementsOf(STANDARD_COLUMNS);

        repairer.run(null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM language_governance", Integer.class)).isEqualTo(3);
        ds.close();
    }

    /**
     * 定位仓库根 {@code data/scripts} 目录：surefire 工作目录为模块目录，
     * IDE 直跑多为仓库根，向上逐级查找两种方式均可命中。
     */
    private Path repoScriptsDir() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (dir != null && !Files.isRegularFile(dir.resolve("data/scripts/common/sqlite/00-common.sql"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("向上查找未找到仓库 data/scripts 目录");
        }
        return dir.resolve("data/scripts");
    }

    @Test
    @DisplayName("端到端：空漂移表修复后经 00-common.sql 通用基准脚本种入 22 语言 + 22 名称（zh-CN=简体中文）")
    void seedsTwentyTwoLanguagesAfterRepair() {
        HikariDataSource ds = newDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE language_identification ("
                + "id blob not null, language_id blob not null unique,"
                + " language_name varchar(255), territory_name varchar(255),"
                + " geopolitical_territory_identifier varchar(255), language_identifier varchar(255),"
                + " private_use_qualifier varchar(255), script_identifier varchar(255),"
                + " primary key (id))");
        createExtTable(jdbc);

        newRepairer(ds, sqliteEnv()).run(null);
        new BaselineScriptPresetService(ds, sqliteEnv(), repoScriptsDir().toString()).applyCommonBaseline();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM language_identification", Integer.class)).isEqualTo(22);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ext_language_identification", Integer.class)).isEqualTo(22);
        String zhName = jdbc.queryForObject(
                "SELECT e.language_name FROM ext_language_identification e"
                        + " JOIN language_identification l ON l.id = e.language_id"
                        + " WHERE l.language_identifier = 'zh-CN'", String.class);
        assertThat(zhName).isEqualTo("简体中文"); // 00-common.sql 口径（与金库一致）
        ds.close();
    }

    @Test
    @DisplayName("健康库（标准 5 列）不触发修复，结构不变")
    void noOpOnHealthySchema() {
        HikariDataSource ds = newDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE language_identification ("
                + "id blob not null, language_identifier varchar(255), script_identifier varchar(255),"
                + " geopolitical_territory_identifier varchar(255), private_use_qualifier varchar(255),"
                + " primary key (id))");
        byte[] id = UUID.randomUUID().toString().replace("-", "").getBytes();
        jdbc.update("INSERT INTO language_identification (id, language_identifier) VALUES (?, 'zh-CN')", id);

        newRepairer(ds, sqliteEnv()).run(null);

        assertThat(columnsOf(jdbc, "language_identification")).containsExactlyInAnyOrderElementsOf(STANDARD_COLUMNS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM language_identification", Integer.class)).isOne();
        ds.close();
    }

    @Test
    @DisplayName("非 SQLite 方言（SQL Server）no-op：不执行任何修复语句，漂移表保持原状")
    void noOpOnNonSqliteVendor() {
        HikariDataSource ds = newDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE language_identification ("
                + "id blob not null, language_id blob not null unique,"
                + " language_name varchar(255), geopolitical_territory_identifier varchar(255),"
                + " language_identifier varchar(255), primary key (id))");
        DatabaseEnvironment sqlServerEnv = new DatabaseEnvironment("sqlserver", "jdbc:sqlserver://localhost:1433", "", "");

        newRepairer(ds, sqlServerEnv).run(null);

        // 方言守卫在任何 SQL 之前返回：漂移列原样保留
        assertThat(columnsOf(jdbc, "language_identification")).contains("language_id", "language_name");
        ds.close();
    }

    @Test
    @DisplayName("唯一索引数据冲突被容忍：修复完成、重复行保留、无异常")
    void toleratesUniqueIndexConflict() {
        HikariDataSource ds = newDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE language_identification ("
                + "id blob not null, language_name varchar(255),"
                + " language_identifier varchar(255), script_identifier varchar(255),"
                + " geopolitical_territory_identifier varchar(255), primary key (id))");
        jdbc.update("INSERT INTO language_identification (id, language_identifier, language_name)"
                + " VALUES (?, 'zh-CN', '中文A')", (Object) UUID.randomUUID().toString().replace("-", "").getBytes());
        jdbc.update("INSERT INTO language_identification (id, language_identifier, language_name)"
                + " VALUES (?, 'zh-CN', '中文B')", (Object) UUID.randomUUID().toString().replace("-", "").getBytes());
        createExtTable(jdbc);

        newRepairer(ds, sqliteEnv()).run(null);

        assertThat(columnsOf(jdbc, "language_identification")).containsExactlyInAnyOrderElementsOf(STANDARD_COLUMNS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM language_identification", Integer.class)).isEqualTo(2);
        ds.close();
    }

    @Test
    @DisplayName("缺列旧代建表：修复后补全 5 列、缺失列 NULL 兜底、可迁移列数据归位")
    void backfillsMissingStandardColumnsWithNull() {
        HikariDataSource ds = newDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        // 极简旧代：无 script/geo/private_use，只有 id + language_identifier + 遗留 language_name
        jdbc.execute("CREATE TABLE language_identification ("
                + "id blob not null, language_identifier varchar(255),"
                + " language_name varchar(255), primary key (id))");
        createExtTable(jdbc);
        byte[] id = UUID.randomUUID().toString().replace("-", "").getBytes();
        jdbc.update("INSERT INTO language_identification (id, language_identifier, language_name) VALUES (?, 'zh-CN', '中文')", id);

        newRepairer(ds, sqliteEnv()).run(null);

        assertThat(columnsOf(jdbc, "language_identification")).containsExactlyInAnyOrderElementsOf(STANDARD_COLUMNS);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT script_identifier, geopolitical_territory_identifier, private_use_qualifier"
                        + " FROM language_identification WHERE id = ?", id);
        assertThat(row.values()).allMatch(v -> v == null);
        assertThat(jdbc.queryForObject(
                "SELECT language_name FROM ext_language_identification WHERE language_id = ?", String.class, id))
                .isEqualTo("中文");
        ds.close();
    }
}

