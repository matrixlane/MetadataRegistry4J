package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegexNamingConventionRuleTest {

    @Test
    void defaultConstructor_shouldCreateRuleWithDefaultSettings() {
        RegexNamingConventionRule rule = new RegexNamingConventionRule();
        assertThat(rule.getName()).isEqualTo("regex");
        assertThat(rule.getDescription()).contains("名称必须匹配正则模式");
    }

    @Test
    void defaultRule_shouldPassForValidIdentifier() {
        RegexNamingConventionRule rule = new RegexNamingConventionRule();
        assertThat(rule.validate(Sign.of("patient_name")).isPassed()).isTrue();
        assertThat(rule.validate(Sign.of("患者名称")).isPassed()).isTrue();
        assertThat(rule.validate(Sign.of("_internal")).isPassed()).isTrue();
    }

    @Test
    void defaultRule_shouldFailForDigitStart() {
        RegexNamingConventionRule rule = new RegexNamingConventionRule();
        NamingConventionRuleResult result = rule.validate(Sign.of("123patient"));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessages()).anyMatch(m -> m.contains("不匹配正则模式"));
    }

    @Test
    void defaultRule_shouldFailForInvalidChars() {
        RegexNamingConventionRule rule = new RegexNamingConventionRule();
        assertThat(rule.validate(Sign.of("patient@name")).isPassed()).isFalse();
        assertThat(rule.validate(Sign.of("patient.name")).isPassed()).isFalse();
    }

    @Test
    void camelCaseRule_shouldPass() {
        RegexNamingConventionRule rule = new RegexNamingConventionRule(
                "camelCase", "^[a-z]+([A-Z][a-z0-9]+)+$", "camelCase 格式");
        NamingConventionRuleResult result = rule.validate(Sign.of("patientName"));
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void camelCaseRule_shouldFailForSnakeCase() {
        RegexNamingConventionRule rule = new RegexNamingConventionRule(
                "camelCase", "^[a-z]+([A-Z][a-z0-9]+)+$", "camelCase 格式");
        NamingConventionRuleResult result = rule.validate(Sign.of("patient_name"));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessages()).anyMatch(m -> m.contains("不匹配正则模式"));
    }

    @Test
    void snakeCaseRule_shouldPass() {
        RegexNamingConventionRule rule = new RegexNamingConventionRule(
                "snake_case", "^[a-z0-9_]+$", "snake_case 格式");
        NamingConventionRuleResult result = rule.validate(Sign.of("patient_name_123"));
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void shouldFailForNullSign() {
        RegexNamingConventionRule rule = new RegexNamingConventionRule(
                "test", "^[a-z]+$", "test");
        NamingConventionRuleResult result = rule.validate(null);
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    void shouldFailForEmptyString() {
        RegexNamingConventionRule rule = new RegexNamingConventionRule(
                "test", "^[a-z]+$", "test");
        NamingConventionRuleResult result = rule.validate(Sign.of(""));
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    void constructor_shouldRejectBlankRuleName() {
        assertThatThrownBy(() -> new RegexNamingConventionRule(null, "^[a-z]+$", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ruleName must not be blank");
    }

    @Test
    void constructor_shouldRejectBlankRegex() {
        assertThatThrownBy(() -> new RegexNamingConventionRule("name", null, "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regex must not be blank");
    }

    @Test
    void constructor_shouldRejectInvalidRegex() {
        assertThatThrownBy(() -> new RegexNamingConventionRule("name", "[invalid", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid regex");
    }
}
