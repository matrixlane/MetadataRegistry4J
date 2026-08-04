package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class VersionsTest {

    @Test
    void compare_integerVersions() {
        assertThat(Versions.compare("11", "10")).isPositive();
        assertThat(Versions.compare("10", "10")).isZero();
        assertThat(Versions.compare("9", "10")).isNegative();
        assertThat(Versions.compare("2", "10")).isNegative(); // 数值而非字典序
    }

    @Test
    void compare_semVerVersions() {
        assertThat(Versions.compare("1.0.1", "1.0.0")).isPositive();
        assertThat(Versions.compare("1.0.0", "1.0.0")).isZero();
        assertThat(Versions.compare("1.0.10", "1.0.9")).isPositive();
        assertThat(Versions.compare("0.9.9", "1.0.0")).isNegative();
    }

    @Test
    void compare_nullsAndUnparseable() {
        assertThat(Versions.compare(null, null)).isZero();
        assertThat(Versions.compare(null, "1")).isNegative();
        assertThat(Versions.compare("1", null)).isPositive();
        // 无法解析且不相等：保守视为已更新
        assertThat(Versions.compare("abc", "1")).isPositive();
    }

    @Test
    void bump_integerVersions_bothPolicies() {
        assertThat(Versions.bump("1", "SEQUENTIAL")).isEqualTo("2");
        assertThat(Versions.bump("1", "SEMVER")).isEqualTo("2");
        assertThat(Versions.bump("10", null)).isEqualTo("11");
    }

    @Test
    void bump_semVerVersion() {
        assertThat(Versions.bump("1.0.0", "SEMVER")).isEqualTo("1.0.1");
        assertThat(Versions.bump("2.3.9", "SEMVER")).isEqualTo("2.3.10");
    }

    @Test
    void bump_blankOrNull_returnsInitial() {
        assertThat(Versions.bump(null, "SEQUENTIAL")).isEqualTo("1");
        assertThat(Versions.bump("", "SEMVER")).isEqualTo("1");
    }

    @Test
    void bump_unparseable_keepsCurrent() {
        assertThat(Versions.bump("v1-beta", "SEMVER")).isEqualTo("v1-beta");
    }

    @Test
    void bump_datePolicy() {
        String today = LocalDate.now().toString();
        assertThat(Versions.bump("1", "DATE")).isEqualTo(today);
        assertThat(Versions.bump(today, "DATE")).isEqualTo(today + "-2");
        assertThat(Versions.bump(today + "-2", "DATE")).isEqualTo(today + "-3");
    }

    @Test
    void initial_versions() {
        assertThat(Versions.initial("SEQUENTIAL")).isEqualTo("1");
        assertThat(Versions.initial("SEMVER")).isEqualTo("1");
        assertThat(Versions.initial(null)).isEqualTo("1");
        assertThat(Versions.initial("DATE")).isEqualTo(LocalDate.now().toString());
    }
}
