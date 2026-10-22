package io.github.matrixlane.metadataregistry.web.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 迁移 V20261023001（删除 mdr schema 下 registration_transition_* 孤儿副本）验证。
 *
 * <p>背景：V20261009005 的迁移守卫（ext 同名表已存在则跳过 ALTER SCHEMA TRANSFER）
 * 使部分既有库残留 mdr 空壳副本，且 ddl-auto 把 ext.registration_transition_rule_approver
 * 的外键误连到 mdr 副本。本迁移在双副本并存守卫下拆误连外键并改指 ext，再删 mdr 副本。
 * SQLite 无 schema 概念（单副本天然正确），达梦以用户即模式（同 V20261009005 空转）。</p>
 */
class DropMdrRegistrationTransitionOrphansMigrationTest {

    private static final String VERSION = "V20261023001";
    private static final String NAME = "Drop_mdr_registration_transition_orphans.sql";

    @Test
    @DisplayName("三方言脚本齐备且同版本号（sqlite 无产物：无 schema 孤儿概念）")
    void dialectScriptsShareVersion() throws Exception {
        assertThat(new ClassPathResource("db/migration/sqlserver/" + VERSION + "__" + NAME).exists())
                .as("sqlserver 分支脚本应存在").isTrue();
        assertThat(new ClassPathResource("db/migration/dm/" + VERSION + "__" + NAME).exists())
                .as("dm 分支脚本应存在（空转）").isTrue();
        assertThat(new ClassPathResource("db/migration/sqlite/" + VERSION + "__" + NAME).exists())
                .as("sqlite 不应有本迁移产物").isFalse();
    }

    @Test
    @DisplayName("SQL Server 版静态核对：双副本守卫/外键改指 ext/空表守卫删表齐备")
    void sqlServerScriptStructure() throws Exception {
        String sql = new String(new ClassPathResource(
                "db/migration/sqlserver/" + VERSION + "__" + NAME)
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql)
                // 数据安全守卫：mdr 与 ext 副本并存才执行，绝不删除唯一副本
                .contains("TABLE_SCHEMA = 'mdr' AND TABLE_NAME = 'registration_transition_rule'")
                .contains("TABLE_SCHEMA = 'ext' AND TABLE_NAME = 'registration_transition_rule'")
                // 误连外键按 sys.foreign_keys 动态解析拆除，改指 ext 副本
                .contains("referenced_object_id = OBJECT_ID('mdr.registration_transition_rule')")
                .contains("REFERENCES ext.registration_transition_rule (id)")
                // 三张 mdr 孤儿副本删除 + 空表守卫
                .contains("IF NOT EXISTS (SELECT 1 FROM mdr.registration_transition_rule_approver)")
                .contains("IF NOT EXISTS (SELECT 1 FROM mdr.registration_transition_approval)")
                .contains("IF NOT EXISTS (SELECT 1 FROM mdr.registration_transition_rule)")
                .contains("DROP TABLE mdr.registration_transition_rule_approver")
                .contains("DROP TABLE mdr.registration_transition_approval")
                .contains("DROP TABLE mdr.registration_transition_rule");
    }
}
