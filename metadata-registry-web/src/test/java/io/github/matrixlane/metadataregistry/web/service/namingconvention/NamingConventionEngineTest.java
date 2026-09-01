package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NamingConventionEngineTest {

    @Test
    void defaultEngine_shouldContainLengthCharsetAndRegexRules() {
        NamingConventionEngine engine = new NamingConventionEngine();
        assertThat(engine.getDefaultRules()).hasSize(3);
        assertThat(engine.getDefaultRules().stream().map(NamingConventionRule::getName))
                .containsExactly("length", "charset", "regex");
    }

    @Test
    void validate_shouldReturnAllRuleResults() {
        NamingConventionEngine engine = new NamingConventionEngine();
        List<NamingConventionRuleResult> results = engine.validate(Sign.of("patient_name"));
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(NamingConventionRuleResult::isPassed);
    }

    @Test
    void validate_shouldReturnFailureForInvalidChars() {
        NamingConventionEngine engine = new NamingConventionEngine();
        List<NamingConventionRuleResult> results = engine.validate(Sign.of("patient@name"));
        assertThat(results).hasSize(3);
        assertThat(results.get(0).isPassed()).isTrue(); // length passes
        assertThat(results.get(1).isPassed()).isFalse(); // charset fails
        assertThat(results.get(2).isPassed()).isFalse(); // regex fails
    }

    @Test
    void isValid_shouldReturnTrueForValidSign() {
        NamingConventionEngine engine = new NamingConventionEngine();
        assertThat(engine.isValid(Sign.of("patient_name_123"))).isTrue();
    }

    @Test
    void isValid_shouldReturnTrueForValidChineseSign() {
        NamingConventionEngine engine = new NamingConventionEngine();
        assertThat(engine.isValid(Sign.of("患者名称"))).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseForSignStartingWithDigit() {
        NamingConventionEngine engine = new NamingConventionEngine();
        assertThat(engine.isValid(Sign.of("123patient"))).isFalse();
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
        ), new ObjectMapper());
        assertThat(engine.getDefaultRules()).hasSize(1);
        assertThat(engine.isValid(Sign.of("ABC"))).isTrue();
        assertThat(engine.isValid(Sign.of("abc"))).isFalse();
    }

    @Test
    void customEngine_withNullRules_shouldBeEmpty() {
        NamingConventionEngine engine = new NamingConventionEngine(null, new ObjectMapper());
        assertThat(engine.getDefaultRules()).isEmpty();
        assertThat(engine.isValid(Sign.of("anything"))).isTrue();
    }

    @Test
    void buildRulesFromConvention_shouldGenerateRulesFromStandardAttributes() {
        NamingConvention convention = new NamingConvention();
        convention.setLexicalRule(Text.of("/^[\\u4e00-\\u9fa5]+$/"));
        convention.setSemanticRule(Text.of("患者,姓名"));

        NamingConventionEngine engine = new NamingConventionEngine();
        List<NamingConventionRule> rules = engine.buildRulesFromConvention(convention);

        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).getName()).isEqualTo("lexical");
        assertThat(rules.get(1).getName()).isEqualTo("semantic");
        assertThat(engine.isValid(Sign.of("患者姓名"), convention)).isTrue();
        assertThat(engine.isValid(Sign.of("PatientName"), convention)).isFalse();
    }

    @Test
    void buildRulesFromConvention_shouldParseJsonRules() {
        NamingConvention convention = new NamingConvention();
        String rulesJson = "[{\"type\": \"regex\", \"pattern\": \"^[A-Z]+$\", \"description\": \"全大写\"}]";

        NamingConventionEngine engine = new NamingConventionEngine();
        List<NamingConventionRule> rules = engine.buildRulesFromConvention(convention, rulesJson);

        assertThat(rules).hasSize(1);
        assertThat(engine.validateWithRules(Sign.of("ABC"), rules).stream()
                .allMatch(NamingConventionRuleResult::isPassed)).isTrue();
        assertThat(engine.validateWithRules(Sign.of("abc"), rules).stream()
                .allMatch(NamingConventionRuleResult::isPassed)).isFalse();
    }

    @Test
    void buildRulesFromConvention_withNullConvention_shouldReturnDefaultRules() {
        NamingConventionEngine engine = new NamingConventionEngine();
        assertThat(engine.buildRulesFromConvention(null)).hasSize(3);
    }
}