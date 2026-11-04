package io.github.matrixlane.metadataregistry.web.seed;

import com.zaxxer.hikari.HikariDataSource;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BaselineScriptPresetService} 测试（baseline-data-init-rebuild / D3）：
 * 通用基准脚本执行器按当前方言执行 {@code data/scripts/common/<方言>/00-common.sql}，
 * 目录/脚本缺失 WARN 跳过不阻断，执行失败抛出，重跑幂等。
 */
class BaselineScriptPresetServiceTest {

    @TempDir
    private Path scriptsRoot;

    private HikariDataSource newDb() {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setJdbcUrl("jdbc:sqlite::memory:");
        // 单连接常驻:SQLite 内存库随最后一个连接关闭而消失
        ds.setMaximumPoolSize(1);
        ds.setMinimumIdle(1);
        return ds;
    }

    private DatabaseEnvironment sqliteEnv() {
        return new DatabaseEnvironment("", "jdbc:sqlite:memdb", "", "");
    }

    private Path writeCommonScript(String dialect, String sql) throws IOException {
        Path dir = scriptsRoot.resolve("common").resolve(dialect);
        Files.createDirectories(dir);
        Path script = dir.resolve("00-common.sql");
        Files.writeString(script, sql);
        return script;
    }

    private static final String GUARDED_INSERT =
            "-- 注释行应被跳过\n"
                    + "\n"
                    + "INSERT INTO t (a) SELECT 'x' WHERE NOT EXISTS (SELECT 1 FROM t WHERE a = 'x');\n";

    @Test
    @DisplayName("applyCommonBaseline:按当前方言(SQLite)执行 00-common.sql 并落行")
    void executesCommonScriptForSqliteVendor() throws IOException {
        HikariDataSource ds = newDb();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE t (a varchar(255) primary key)");
        writeCommonScript("sqlite", GUARDED_INSERT);

        new BaselineScriptPresetService(ds, sqliteEnv(), scriptsRoot.toString()).applyCommonBaseline();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t WHERE a = 'x'", Integer.class)).isOne();
        ds.close();
    }

    @Test
    @DisplayName("applyCommonBaseline:SQL Server 方言选 sqlserver 子目录")
    void executesCommonScriptForSqlserverVendor() throws IOException {
        HikariDataSource ds = newDb();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE t (a varchar(255) primary key)");
        writeCommonScript("sqlserver", GUARDED_INSERT);
        DatabaseEnvironment sqlServerEnv =
                new DatabaseEnvironment("sqlserver", "jdbc:sqlserver://localhost:1433", "", "");

        new BaselineScriptPresetService(ds, sqlServerEnv, scriptsRoot.toString()).applyCommonBaseline();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t WHERE a = 'x'", Integer.class)).isOne();
        ds.close();
    }

    @Test
    @DisplayName("applyCommonBaseline:脚本目录缺失时 WARN 跳过不抛异常")
    void missingDirectorySkipsWithoutThrowing() {
        HikariDataSource ds = newDb();
        BaselineScriptPresetService service =
                new BaselineScriptPresetService(ds, sqliteEnv(), scriptsRoot.resolve("absent").toString());

        assertThatCode(service::applyCommonBaseline).doesNotThrowAnyException();
        ds.close();
    }

    @Test
    @DisplayName("applyCommonBaseline:目录存在但 00-common.sql 缺失时 WARN 跳过不抛异常")
    void missingScriptFileSkipsWithoutThrowing() throws IOException {
        HikariDataSource ds = newDb();
        Files.createDirectories(scriptsRoot.resolve("common").resolve("sqlite"));
        BaselineScriptPresetService service =
                new BaselineScriptPresetService(ds, sqliteEnv(), scriptsRoot.toString());

        assertThatCode(service::applyCommonBaseline).doesNotThrowAnyException();
        ds.close();
    }

    @Test
    @DisplayName("applyCommonBaseline:UTF-8 脚本中的中文文本按 UTF-8 落库(不受平台默认字符集影响)")
    void utf8ScriptTextIsReadAsUtf8() throws IOException {
        HikariDataSource ds = newDb();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE t (a varchar(255) primary key)");
        writeCommonScript("sqlite",
                "INSERT INTO t (a) SELECT '简体中文' WHERE NOT EXISTS (SELECT 1 FROM t WHERE a = '简体中文');\n");

        new BaselineScriptPresetService(ds, sqliteEnv(), scriptsRoot.toString()).applyCommonBaseline();

        assertThat(jdbc.queryForObject("SELECT a FROM t", String.class)).isEqualTo("简体中文");
        ds.close();
    }

    @Test
    @DisplayName("applyCommonBaseline:重跑幂等(脚本自身 NOT EXISTS 守卫)")
    void rerunIsIdempotent() throws IOException {
        HikariDataSource ds = newDb();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE t (a varchar(255) primary key)");
        writeCommonScript("sqlite", GUARDED_INSERT);
        BaselineScriptPresetService service =
                new BaselineScriptPresetService(ds, sqliteEnv(), scriptsRoot.toString());

        service.applyCommonBaseline();
        service.applyCommonBaseline();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t", Integer.class)).isOne();
        ds.close();
    }

    @Test
    @DisplayName("applyCommonBaseline:脚本执行失败抛出(不完整基准不得静默通过)")
    void failurePropagates() throws IOException {
        HikariDataSource ds = newDb();
        writeCommonScript("sqlite", "INSERT INTO no_such_table (a) VALUES ('x');\n");
        BaselineScriptPresetService service =
                new BaselineScriptPresetService(ds, sqliteEnv(), scriptsRoot.toString());

        assertThatThrownBy(service::applyCommonBaseline).isInstanceOf(RuntimeException.class);
        ds.close();
    }
}
