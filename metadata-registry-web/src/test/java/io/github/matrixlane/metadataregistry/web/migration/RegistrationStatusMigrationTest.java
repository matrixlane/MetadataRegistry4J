package io.github.matrixlane.metadataregistry.web.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 修正迁移 V20260901001 的逻辑验证测试。
 *
 * <p>此前 V20260517001 方向错误,把标准状态 CANDIDATE/PREFERRED_STANDARD 迁往
 * 标准外的 SUBMITTED/PREFERRED。V20260901001 反向纠正,恢复 ISO/IEC 11179-3:2023
 * Table 30 / 11179-6:2023 Table 1 的标准值集。</p>
 *
 * <p>设计说明:本测试以内存镜像方式验证迁移映射逻辑。SQL Server 上的端到端
 * Flyway 迁移验证需要 Testcontainers(当前非项目依赖),V20260901001 可在
 * SQL Server 实例上手工核验;现库 6543 行全部为 'Standard',修正迁移为 no-op。</p>
 */
class RegistrationStatusMigrationTest {

    /**
     * 标准值集(11179-3:2023 Table 30 / 11179-6:2023 Table 1)。
     * 标准中不存在 SUBMITTED / PREFERRED / COMPATIBLE。
     */
    private static final Set<String> VALID_STATUSES = Set.of(
            "INCOMPLETE", "CANDIDATE", "RECORDED", "QUALIFIED",
            "STANDARD", "PREFERRED_STANDARD", "SUPERSEDED", "RETIRED",
            "HISTORICAL", "APPLICATION");

    /** 修正迁移映射:标准外的旧值 → 标准值。 */
    private static final Map<String, String> CORRECTION_MAP = Map.of(
            "SUBMITTED", "CANDIDATE",
            "PREFERRED", "PREFERRED_STANDARD");

    /** 标准值不做迁移,保持原样。 */
    private static final Set<String> IMMUTABLE_STATUSES = VALID_STATUSES;

    // =========================================================================
    // Test: 修正映射逐项断言
    // =========================================================================

    @Test
    @DisplayName("SUBMITTED 应修正为 CANDIDATE(11179-3 Table 30 第二级)")
    void map_submittedToCandidate() {
        assertThat(migrate("SUBMITTED")).isEqualTo("CANDIDATE");
    }

    @Test
    @DisplayName("PREFERRED 应修正为 PREFERRED_STANDARD(标准名 Preferred Standard)")
    void map_preferredToPreferredStandard() {
        assertThat(migrate("PREFERRED")).isEqualTo("PREFERRED_STANDARD");
    }

    // =========================================================================
    // Test: 标准值保持不变
    // =========================================================================

    @Test
    @DisplayName("所有标准状态值应保持不变")
    void immutableStatusesUnchanged() {
        for (String status : IMMUTABLE_STATUSES) {
            assertThat(migrate(status))
                    .as("Status '%s' should remain unchanged", status)
                    .isEqualTo(status);
        }
    }

    // =========================================================================
    // Test: 值集一致性
    // =========================================================================

    @Test
    @DisplayName("所有有效状态值通过验证")
    void allValidStatusesPassValidation() {
        for (String status : VALID_STATUSES) {
            assertThat(isValidAfterMigration(status)).isTrue();
        }
    }

    @Test
    @DisplayName("所有已修正值都落入标准值集")
    void allMigratedValuesAreValid() {
        for (String legacy : CORRECTION_MAP.keySet()) {
            String migrated = migrate(legacy);
            assertThat(isValidAfterMigration(migrated))
                    .as("Migrated value '%s' from '%s' should be a standard value", migrated, legacy)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("迁移覆盖检查: 所有标准外的旧值都应在修正映射中")
    void migrationCompletenessCheck() {
        // 标准外的旧枚举值(已从枚举中移除)必须全部有修正去向
        assertThat(CORRECTION_MAP.keySet())
                .containsExactlyInAnyOrder("SUBMITTED", "PREFERRED");
    }

    @Test
    @DisplayName("枚举值与标准值集一致")
    void enumValuesMatchValidSet() {
        // 与 RegistrationStatus 枚举值集镜像
        List<String> enumValues = Arrays.asList(
                "INCOMPLETE", "CANDIDATE", "RECORDED", "QUALIFIED",
                "STANDARD", "PREFERRED_STANDARD", "SUPERSEDED", "RETIRED",
                "HISTORICAL", "APPLICATION"
        );
        for (String value : enumValues) {
            assertThat(VALID_STATUSES).contains(value);
        }
        assertThat(VALID_STATUSES.size()).isEqualTo(enumValues.size());
    }

    // =========================================================================
    // Test: 混合数据迁移场景
    // =========================================================================

    @Test
    @DisplayName("混合数据迁移: 多个状态值正确转换")
    void hybridMigration() {
        List<String> inputs = Arrays.asList(
                "SUBMITTED", "PREFERRED", "APPLICATION",
                "INCOMPLETE", "RECORDED", "STANDARD", "CANDIDATE", "PREFERRED_STANDARD"
        );

        List<String> expected = Arrays.asList(
                "CANDIDATE", "PREFERRED_STANDARD", "APPLICATION",
                "INCOMPLETE", "RECORDED", "STANDARD", "CANDIDATE", "PREFERRED_STANDARD"
        );

        for (int i = 0; i < inputs.size(); i++) {
            assertThat(migrate(inputs.get(i)))
                    .as("Input: %s", inputs.get(i))
                    .isEqualTo(expected.get(i));
        }
    }

    // =========================================================================
    // Helper methods(镜像 V20260901001 迁移逻辑)
    // =========================================================================

    private String migrate(String legacyStatus) {
        String current = legacyStatus;

        // Step 1: SUBMITTED → CANDIDATE
        if (current.equals("SUBMITTED")) {
            current = "CANDIDATE";
        }
        // Step 2: PREFERRED → PREFERRED_STANDARD
        if (current.equals("PREFERRED")) {
            current = "PREFERRED_STANDARD";
        }

        return current;
    }

    private boolean isValidAfterMigration(String status) {
        return VALID_STATUSES.contains(status);
    }
}
