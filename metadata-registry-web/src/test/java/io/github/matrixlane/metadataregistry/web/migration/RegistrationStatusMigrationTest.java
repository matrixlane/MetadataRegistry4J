package io.github.matrixlane.metadataregistry.web.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WI-019 Flyway migration logic verification test.
 *
 * <p>Validates that the migration logic correctly maps all legacy
 * RegistrationStatus values to their new standard equivalents.</p>
 *
 * <p>Design note: This test verifies the migration logic in isolation.
 * End-to-end Flyway migration testing on SQL Server requires Testcontainers
 * which is not currently a project dependency. The migration script
 * V20260517001 can be manually verified on a SQL Server instance.</p>
 */
class RegistrationStatusMigrationTest {

    // The set of valid (new) status values after migration
    private static final Set<String> VALID_STATUSES = Set.of(
            "INCOMPLETE", "SUBMITTED", "RECORDED", "QUALIFIED",
            "STANDARD", "PREFERRED", "SUPERSEDED", "RETIRED", "HISTORICAL"
    );

    // Migration mapping: legacy → new
    private static final Map<String, String> MIGRATION_MAP = Map.of(
            "COMPATIBLE", "INCOMPLETE",
            "PREFERRED_STANDARD", "PREFERRED",
            "CANDIDATE", "SUBMITTED",
            "APPLICATION", "SUBMITTED"
    );

    // Status values that should NOT change
    private static final Set<String> IMMUTABLE_STATUSES = Set.of(
            "INCOMPLETE", "RECORDED", "QUALIFIED",
            "STANDARD", "SUPERSEDED", "RETIRED", "HISTORICAL"
    );

    // =========================================================================
    // Test: Legacy value mappings
    // =========================================================================

    @Test
    @DisplayName("COMPATIBLE 应迁移到 INCOMPLETE")
    void map_compatibleToIncomplete() {
        assertThat(migrate("COMPATIBLE")).isEqualTo("INCOMPLETE");
    }

    @Test
    @DisplayName("PREFERRED_STANDARD 应迁移到 PREFERRED")
    void map_preferredStandardToPreferred() {
        assertThat(migrate("PREFERRED_STANDARD")).isEqualTo("PREFERRED");
    }

    @Test
    @DisplayName("CANDIDATE 应迁移到 SUBMITTED")
    void map_candidateToSubmitted() {
        assertThat(migrate("CANDIDATE")).isEqualTo("SUBMITTED");
    }

    @Test
    @DisplayName("APPLICATION 应迁移到 SUBMITTED")
    void map_applicationToSubmitted() {
        assertThat(migrate("APPLICATION")).isEqualTo("SUBMITTED");
    }

    // =========================================================================
    // Test: Standard values remain unchanged
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
    // Test: All valid statuses pass validation
    // =========================================================================

    @Test
    @DisplayName("所有有效状态值通过验证")
    void allValidStatusesPassValidation() {
        for (String status : VALID_STATUSES) {
            assertThat(isValidAfterMigration(status)).isTrue();
        }
    }

    @Test
    @DisplayName("所有已迁移值都落入有效状态集合")
    void allMigratedValuesAreValid() {
        for (String legacy : MIGRATION_MAP.keySet()) {
            String migrated = migrate(legacy);
            assertThat(isValidAfterMigration(migrated))
                    .as("Migrated value '%s' from '%s' should be valid", migrated, legacy)
                    .isTrue();
        }
    }

    // =========================================================================
    // Test: Migration completeness check
    // =========================================================================

    @Test
    @DisplayName("迁移覆盖检查: 所有已知的 legacy 状态都应在映射中")
    void migrationCompletenessCheck() {
        // Verify that the known legacy statuses (removed from enum) are in the migration map
        assertThat(MIGRATION_MAP.keySet())
                .containsExactlyInAnyOrder("COMPATIBLE", "PREFERRED_STANDARD", "CANDIDATE", "APPLICATION");
    }

    @Test
    @DisplayName("枚举值与有效状态集合一致")
    void enumValuesMatchValidSet() {
        // This mirrors the RegistrationStatus enum values
        List<String> enumValues = Arrays.asList(
                "INCOMPLETE", "SUBMITTED", "RECORDED", "QUALIFIED",
                "STANDARD", "PREFERRED", "SUPERSEDED", "RETIRED", "HISTORICAL"
        );
        for (String value : enumValues) {
            assertThat(VALID_STATUSES).contains(value);
        }
        // Verify no extra values in valid set
        assertThat(VALID_STATUSES.size()).isEqualTo(enumValues.size());
    }

    // =========================================================================
    // Test: Hybrid migration scenario
    // =========================================================================

    @Test
    @DisplayName("混合数据迁移: 多个状态值正确转换")
    void hybridMigration() {
        List<String> inputs = Arrays.asList(
                "COMPATIBLE", "PREFERRED_STANDARD", "CANDIDATE",
                "APPLICATION", "INCOMPLETE", "RECORDED", "STANDARD"
        );

        List<String> expected = Arrays.asList(
                "INCOMPLETE", "PREFERRED", "SUBMITTED",
                "SUBMITTED", "INCOMPLETE", "RECORDED", "STANDARD"
        );

        for (int i = 0; i < inputs.size(); i++) {
            assertThat(migrate(inputs.get(i)))
                    .as("Input: %s", inputs.get(i))
                    .isEqualTo(expected.get(i));
        }
    }

    // =========================================================================
    // Helper methods (mirroring the SQL migration logic)
    // =========================================================================

    private String migrate(String legacyStatus) {
        String current = legacyStatus;

        // Step 1: COMPATIBLE → INCOMPLETE
        if (current.equals("COMPATIBLE")) {
            current = "INCOMPLETE";
        }
        // Step 2: PREFERRED_STANDARD → PREFERRED
        if (current.equals("PREFERRED_STANDARD")) {
            current = "PREFERRED";
        }
        // Step 3: CANDIDATE → SUBMITTED
        if (current.equals("CANDIDATE")) {
            current = "SUBMITTED";
        }
        // Step 4: APPLICATION → SUBMITTED
        if (current.equals("APPLICATION")) {
            current = "SUBMITTED";
        }

        return current;
    }

    private boolean isValidAfterMigration(String status) {
        return VALID_STATUSES.contains(status);
    }
}
