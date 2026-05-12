package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CharsetNamingConventionRuleTest {

    @Test
    void defaultRule_shouldPassForAlphanumeric() {
        CharsetNamingConventionRule rule = new CharsetNamingConventionRule();
        NamingConventionRuleResult result = rule.validate(Sign.of("patient_name_123"));
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void defaultRule_shouldPassForChinese() {
        CharsetNamingConventionRule rule = new CharsetNamingConventionRule();
        NamingConventionRuleResult result = rule.validate(Sign.of("患者姓名"));
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void defaultRule_shouldPassForMixedChineseAndEnglish() {
        CharsetNamingConventionRule rule = new CharsetNamingConventionRule();
        NamingConventionRuleResult result = rule.validate(Sign.of("患者姓名_patient_name"));
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void defaultRule_shouldFailForSpecialChars() {
        CharsetNamingConventionRule rule = new CharsetNamingConventionRule();
        NamingConventionRuleResult result = rule.validate(Sign.of("patient@name"));
        assertThat(result.isPassed()).isFalse();
        assertThat(result.getMessages()).anyMatch(m -> m.contains("非法字符"));
    }

    @Test
    void defaultRule_shouldFailForNullSign() {
        CharsetNamingConventionRule rule = new CharsetNamingConventionRule();
        NamingConventionRuleResult result = rule.validate(null);
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    void defaultRule_shouldFailForEmptyString() {
        CharsetNamingConventionRule rule = new CharsetNamingConventionRule();
        NamingConventionRuleResult result = rule.validate(Sign.of(""));
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    void customRule_withDot_shouldPass() {
        CharsetNamingConventionRule rule = new CharsetNamingConventionRule(
                Set.of(CharsetNamingConventionRule.CharacterSet.ALPHANUMERIC,
                        CharsetNamingConventionRule.CharacterSet.DOT));
        NamingConventionRuleResult result = rule.validate(Sign.of("patient.name"));
        assertThat(result.isPassed()).isTrue();
    }

    @Test
    void customRule_withoutSpace_shouldFailForSpace() {
        CharsetNamingConventionRule rule = new CharsetNamingConventionRule(
                Set.of(CharsetNamingConventionRule.CharacterSet.ALPHANUMERIC,
                        CharsetNamingConventionRule.CharacterSet.UNDERSCORE));
        NamingConventionRuleResult result = rule.validate(Sign.of("patient name"));
        assertThat(result.isPassed()).isFalse();
    }

    @Test
    void constructor_shouldRejectEmptySet() {
        assertThatThrownBy(() -> new CharsetNamingConventionRule(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedSets must not be empty");
    }

    @Test
    void constructor_shouldRejectNullSet() {
        assertThatThrownBy(() -> new CharsetNamingConventionRule(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedSets must not be empty");
    }
}
