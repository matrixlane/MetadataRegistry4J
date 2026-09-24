package io.github.matrixlane.metadataregistry.web.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 workspace administrative status 索引迁移脚本可在 SQLite 全新库应用。
 */
class WorkspaceAdministrativeStatusMigrationTest {

    @Test
    void sqliteMigration_shouldApplyOnFreshDatabase() throws Exception {
        String url = "jdbc:sqlite:file:migration-test?mode=memory&cache=shared";
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                    create table registration_state (
                        id blob primary key,
                        administrative_status varchar(255),
                        registration_status varchar(255)
                    )
                    """);
            stmt.execute("""
                    create table administered_item (
                        id blob primary key,
                        stewardship_record_id blob,
                        registration_state_id blob
                    )
                    """);

            String migrationSql = new String(
                    new ClassPathResource("db/migration/sqlite/V20260924001__Workspace_administrative_status_indexes.sql")
                            .getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            for (String sql : migrationSql.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isBlank()) {
                    stmt.execute(trimmed);
                }
            }

            try (ResultSet rs = stmt.executeQuery("""
                    SELECT name FROM sqlite_master
                    WHERE type = 'index'
                      AND name IN ('idx_registration_state_administrative_status',
                                   'idx_administered_item_stewardship_record_id')
                    """)) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                assertThat(count).isEqualTo(2);
            }
        }
    }
}
