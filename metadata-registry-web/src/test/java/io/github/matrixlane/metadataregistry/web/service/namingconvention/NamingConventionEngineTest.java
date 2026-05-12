package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NamingConventionEngineTest {

    @Test
    void defaultEngine_shouldContainLengthAndCharsetRules() {
        NamingConventionEngine engine = new NamingConventionEngine();
        assertThat(engine.getRules()).hasSize(2);
        assertThat(engine.getRules().stream().map(NamingConventionRule::getName))
                .containsExactly("length", "charset");
    }

    @Test
    void validate_shouldReturnAllRuleResults() {
        NamingConventionEngine engine = new NamingConventionEngine();
        List<NamingConventionRuleResult> results = engine.validate(Sign.of("patient_name"));
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(NamingConventionRuleResult::isPassed);
    }

    @Test
    void validate_shouldReturnFailureForInvalidChars() {
        NamingConventionEngine engine = new NamingConventionEngine();
        List<NamingConventionRuleResult> results = engine.validate(Sign.of("patient@name"));
        assertThat(results).hasSize(2);
        assertThat(results.get(0).isPassed()).isTrue(); // length passes
        assertThat(results.get(1).isPassed()).isFalse(); // charset fails
    }

    @Test
    void isValid_shouldReturnTrueForValidSign() {
        NamingConventionEngine engine = new NamingConventionEngine();
        assertThat(engine.isValid(Sign.of("patient_name_123"))).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseForInvalidSign() {
        NamingConventionEngine engine = new NamingConventionEngine();
        assertThat(engine.isValid(Sign.of(""))).isFalse();
    }

    @Test
    void customEngine_shouldUseProvidedRules() {
        NamingConventionEngine engine = new NamingConventionEngine(List.of(
                new RegexNamingConventionRule("uppercase", "^[A-Z]+$", "全大写")
        ));
        assertThat(engine.getRules()).hasSize(1);
        assertThat(engine.isValid(Sign.of("ABC"))).isTrue();
        assertThat(engine.isValid(Sign.of("abc"))).isFalse();
    }

    @Test
    void customEngine_withNullRules_shouldBeEmpty() {
        NamingConventionEngine engine = new NamingConventionEngine(null);
        assertThat(engine.getRules()).isEmpty();
        assertThat(engine.isValid(Sign.of("anything"))).isTrue();
    }
}
