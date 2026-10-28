package io.github.matrixlane.metadataregistry.web.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 迁移 V20261028001（清理 dbo schema 遗留表，cleanup-dbo-leftover-tables）验证。
 *
 * <p>背景：2026-05 未限定表名时代的扩展/孤儿表落入 dbo；2026-09-01 mdr/ext 拆分只守卫
 * TABLE_SCHEMA='mdr'，dbo 残留至今（本机实测 12 张全部 0 行）。本迁移幂等清理：
 * 8 张无实体孤儿表与 mapping 两表"存在即删"（先子表后父表）；
 * interop_mapping/version_history 三重守卫（dbo 副本存在 + ext 副本存在 + dbo 0 行，
 * 行数经 sys.partitions+OBJECT_ID 判定，避免编译期引用不存在表）。
 * SQLite 无 schema，清主库同名 10 张非活表，严禁触碰个人版活表；
 * 达梦无 dbo 概念，按 MDR/EXT owner 防御性清理同 10 张。</p>
 */
class DropDboLeftoverTablesMigrationTest {

    private static final String VERSION = "V20261028001";
    private static final String NAME = "Drop_dbo_leftover_tables.sql";

    private static String read(String path) throws Exception {
        return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** 去掉 -- 注释行（注释中提及的表名不参与结构断言）。 */
    private static String withoutComments(String sql) {
        return Arrays.stream(sql.split("\n"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("三方言脚本齐备且同版本号")
    void dialectScriptsShareVersion() {
        assertThat(new ClassPathResource("db/migration/sqlserver/" + VERSION + "__" + NAME).exists())
                .as("sqlserver 分支脚本应存在").isTrue();
        assertThat(new ClassPathResource("db/migration/dm/" + VERSION + "__" + NAME).exists())
                .as("dm 分支脚本应存在").isTrue();
        assertThat(new ClassPathResource("db/migration/sqlite/" + VERSION + "__" + NAME).exists())
                .as("sqlite 分支脚本应存在").isTrue();
    }

    @Test
    @DisplayName("SQL Server 版静态核对：10 张存在即删 + 活表三重守卫齐备")
    void sqlServerScriptStructure() throws Exception {
        String sql = withoutComments(read("db/migration/sqlserver/" + VERSION + "__" + NAME));
        // 孤儿表 + mapping 残留：存在即删（先子表 fhir_binding，后父表 fhir_element/fhir_resource）
        assertThat(sql)
                .contains("TABLE_SCHEMA='dbo' AND TABLE_NAME='fhir_binding'").contains("DROP TABLE dbo.fhir_binding")
                .contains("TABLE_SCHEMA='dbo' AND TABLE_NAME='fhir_element'").contains("DROP TABLE dbo.fhir_element")
                .contains("TABLE_SCHEMA='dbo' AND TABLE_NAME='fhir_resource'").contains("DROP TABLE dbo.fhir_resource")
                .contains("TABLE_SCHEMA='dbo' AND TABLE_NAME='fhir_value_set'").contains("DROP TABLE dbo.fhir_value_set")
                .contains("TABLE_SCHEMA='dbo' AND TABLE_NAME='fhir_code_system'").contains("DROP TABLE dbo.fhir_code_system")
                .contains("TABLE_SCHEMA='dbo' AND TABLE_NAME='data_quality_task'").contains("DROP TABLE dbo.data_quality_task")
                .contains("TABLE_SCHEMA='dbo' AND TABLE_NAME='notification_preference'").contains("DROP TABLE dbo.notification_preference")
                .contains("TABLE_SCHEMA='dbo' AND TABLE_NAME='notification_record'").contains("DROP TABLE dbo.notification_record")
                .contains("TABLE_SCHEMA='dbo' AND TABLE_NAME='mapping_scheme'").contains("DROP TABLE dbo.mapping_scheme")
                .contains("TABLE_SCHEMA='dbo' AND TABLE_NAME='mapping_subscription'").contains("DROP TABLE dbo.mapping_subscription");
        // 活表死副本三重守卫：dbo 存在 + ext 存在 + dbo 0 行（sys.partitions + OBJECT_ID 判定）
        assertThat(sql)
                .contains("TABLE_SCHEMA='ext' AND TABLE_NAME='interop_mapping'")
                .contains("OBJECT_ID('dbo.interop_mapping')")
                .contains("TABLE_SCHEMA='ext' AND TABLE_NAME='version_history'")
                .contains("OBJECT_ID('dbo.version_history')")
                .contains("sys.partitions");
        // 历史表 installed_on 默认值自愈：Flyway 写历史行省略该列、依赖 DEFAULT，缺失则报 515
        assertThat(sql)
                .contains("sys.default_constraints")
                .contains("ADD DEFAULT (GETDATE()) FOR installed_on");
        // 依赖序硬断言：先子表后父表（fhir_binding → fhir_element → fhir_resource）
        assertThat(sql.indexOf("DROP TABLE dbo.fhir_binding"))
                .as("fhir_binding 应先于 fhir_element 删除")
                .isLessThan(sql.indexOf("DROP TABLE dbo.fhir_element"));
        assertThat(sql.indexOf("DROP TABLE dbo.fhir_element"))
                .as("fhir_element 应先于 fhir_resource 删除")
                .isLessThan(sql.indexOf("DROP TABLE dbo.fhir_resource"));
    }

    @Test
    @DisplayName("SQLite 版静态核对：白名单恰为 10 张非活表，严禁触碰个人版活表")
    void sqliteScriptWhitelist() throws Exception {
        String sql = withoutComments(read("db/migration/sqlite/" + VERSION + "__" + NAME));
        assertThat(sql)
                .doesNotContain("interop_mapping")
                .doesNotContain("version_history");
        // 精确集合断言：挡住"误加一张活表 DROP"与"漏删一张残留"
        assertThat(dropTableNames(sql))
                .containsExactlyInAnyOrder(
                        "fhir_binding", "fhir_element", "fhir_resource", "fhir_value_set",
                        "fhir_code_system", "data_quality_task", "notification_preference",
                        "notification_record", "mapping_scheme", "mapping_subscription");
    }

    @Test
    @DisplayName("达梦版静态核对：白名单恰为 10 张非活表 MDR/EXT 双 owner 守卫，不触碰活表")
    void dmScriptWhitelist() throws Exception {
        String sql = withoutComments(read("db/migration/dm/" + VERSION + "__" + NAME));
        assertThat(sql)
                .doesNotContain("INTEROP_MAPPING")
                .doesNotContain("VERSION_HISTORY");
        // 精确集合断言（MDR owner 守卫一处即代表该表存在；共 10 张）
        assertThat(mdrGuardedTables(sql))
                .containsExactlyInAnyOrder(
                        "FHIR_BINDING", "FHIR_ELEMENT", "FHIR_RESOURCE", "FHIR_VALUE_SET",
                        "FHIR_CODE_SYSTEM", "DATA_QUALITY_TASK", "NOTIFICATION_PREFERENCE",
                        "NOTIFICATION_RECORD", "MAPPING_SCHEME", "MAPPING_SUBSCRIPTION");
    }

    private static java.util.List<String> dropTableNames(String sql) {
        return java.util.regex.Pattern.compile("DROP TABLE IF EXISTS (\\w+);")
                .matcher(sql).results().map(m -> m.group(1)).toList();
    }

    private static java.util.List<String> mdrGuardedTables(String sql) {
        return java.util.regex.Pattern.compile("OWNER='MDR' AND TABLE_NAME='(\\w+)'")
                .matcher(sql).results().map(m -> m.group(1)).distinct().toList();
    }
}
