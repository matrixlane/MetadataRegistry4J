package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LengthNamingConventionRuleTest {

    @Test
    void defaultRule_shouldPassForNormalLength() {
        LengthNamingConventionRule rule = new LengthNamingConventionRule();
        NamingConventionRuleResult result = rule.validate(Sign.of("patient_name"));
        assertThat(result.isPassed()).isTrue();
        assertThat(result.getRuleName()).isEqualTo("length");
    }

    @Test
    void defaultRule_shouldFailForEmptyString() {
        LengthNamingConventionRule rule = new LengthNamingConventionRule();
        NamingConventionRuleResult result = rule.validate(Sign.of(""));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessages()).anyMatch(m -> m.contains("不能为空"));
    }

    @Test
    void defaultRule_shouldFailForNullSign() {
        LengthNamingConventionRule rule = new LengthNamingConventionRule();
        NamingConventionRuleResult result = rule.validate(null);
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    void defaultRule_shouldPassAtBoundaryLength() {
        LengthNamingConventionRule rule = new LengthNamingConventionRule();
        String value = "a".repeat(255);
        NamingConventionRuleResult result = rule.validate(Sign.of(value));
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void defaultRule_shouldFailWhenExceedsMaxLength() {
        LengthNamingConventionRule rule = new LengthNamingConventionRule();
        String value = "a".repeat(256);
        NamingConventionRuleResult result = rule.validate(Sign.of(value));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessages()).anyMatch(m -> m.contains("超过最大限制"));
    }

    @Test
    void customRule_shouldFailWhenBelowMinLength() {
        LengthNamingConventionRule rule = new LengthNamingConventionRule(3, 50);
        NamingConventionRuleResult result = rule.validate(Sign.of("ab"));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessages()).anyMatch(m -> m.contains("小于最小限制"));
    }

    @Test
    void customRule_shouldPassWithinRange() {
        LengthNamingConventionRule rule = new LengthNamingConventionRule(3, 50);
        NamingConventionRuleResult result = rule.validate(Sign.of("abc"));
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void constructor_shouldRejectInvalidRange() {
        assertThatThrownBy(() -> new LengthNamingConventionRule(10, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxLength must be >= minLength");
    }

    @Test
    void constructor_shouldRejectNegativeMinLength() {
        assertThatThrownBy(() -> new LengthNamingConventionRule(-1, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minLength must be >= 0");
    }
}
