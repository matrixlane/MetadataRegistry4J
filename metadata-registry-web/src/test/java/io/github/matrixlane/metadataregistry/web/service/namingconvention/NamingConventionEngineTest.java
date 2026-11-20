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
    void defaultEngine_shouldContainOnlyLengthRule() {
        NamingConventionEngine engine = new NamingConventionEngine();
        assertThat(engine.getDefaultRules()).hasSize(1);
        assertThat(engine.getDefaultRules().stream().map(NamingConventionRule::getName))
                .containsExactly("length");
    }

    @Test
    void validate_shouldReturnAllRuleResults() {
        NamingConventionEngine engine = new NamingConventionEngine();
        List<NamingConventionRuleResult> results = engine.validate(Sign.of("patient_name"));
        assertThat(results).hasSize(1);
        assertThat(results).allMatch(NamingConventionRuleResult::isPassed);
    }

    @Test
    void validate_shouldPassStandardSpecialChars() {
        // default-naming-rules-standard-alignment：默认集不含字符构成限制，
        // WS 363/364 标准指称中的括号/斜杠/罗马数字等字符必须原样放行
        NamingConventionEngine engine = new NamingConventionEngine();
        assertThat(engine.isValid(Sign.of("医嘱(处方)开立医师姓名"))).isTrue();
        assertThat(engine.isValid(Sign.of("主任（副主任）医师姓名"))).isTrue();
        assertThat(engine.isValid(Sign.of("患者/法定代理人姓名"))).isTrue();
        assertThat(engine.isValid(Sign.of("Ⅰ助姓名"))).isTrue();
        assertThat(engine.isValid(Sign.of("尿铅检测值(ptmol/L)值域"))).isTrue();
        assertThat(engine.isValid(Sign.of("骶耻外径＜18cm"))).isTrue();
    }

    @Test
    void validate_shouldStillRejectBlankAndOverlong() {
        NamingConventionEngine engine = new NamingConventionEngine();
        assertThat(engine.isValid(Sign.of(""))).isFalse();
        assertThat(engine.isValid(Sign.of("x".repeat(256)))).isFalse();
        assertThat(engine.isValid(Sign.of("x".repeat(255)))).isTrue();
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
    void isValid_shouldReturnTrueForSignStartingWithDigit() {
        // 默认集不再包含标识符式正则，数字开头不再受限
        NamingConventionEngine engine = new NamingConventionEngine();
        assertThat(engine.isValid(Sign.of("123patient"))).isTrue();
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
    void buildRulesFromConvention_shouldParseCharsetJsonRule() {
        // charset 机器规则须经命名空间约定显式启用（default-naming-rules-standard-alignment）
        NamingConvention convention = new NamingConvention();
        String rulesJson = "[{\"type\": \"charset\"}]";

        NamingConventionEngine engine = new NamingConventionEngine();
        List<NamingConventionRule> rules = engine.buildRulesFromConvention(convention, rulesJson);

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getName()).isEqualTo("charset");
        assertThat(engine.validateWithRules(Sign.of("患者姓名"), rules).stream()
                .allMatch(NamingConventionRuleResult::isPassed)).isTrue();
        assertThat(engine.validateWithRules(Sign.of("患者/法定代理人"), rules).stream()
                .allMatch(NamingConventionRuleResult::isPassed)).isFalse();
    }

    @Test
    void buildRulesFromConvention_withNullConvention_shouldReturnDefaultRules() {
        NamingConventionEngine engine = new NamingConventionEngine();
        assertThat(engine.buildRulesFromConvention(null)).hasSize(1);
    }
}