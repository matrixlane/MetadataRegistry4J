package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 命名规范引擎。
 *
 * <p>V1 采用策略模式，硬编码注册基础规则集，预留扩展接口供 V2 替换。
 * 同时支持从 {@link NamingConvention} 实体的标准属性或 {@code rulesJson} 生成规则。
 */
@Component
public class NamingConventionEngine {

    private final List<NamingConventionRule> defaultRules;
    private final ObjectMapper objectMapper;

    public NamingConventionEngine() {
        this(buildDefaultRules(), new ObjectMapper());
    }

    public NamingConventionEngine(ObjectMapper objectMapper) {
        this(buildDefaultRules(), objectMapper);
    }

    public NamingConventionEngine(List<NamingConventionRule> defaultRules, ObjectMapper objectMapper) {
        this.defaultRules = defaultRules == null ? Collections.emptyList() : new ArrayList<>(defaultRules);
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /**
     * 使用默认规则对给定的 Sign 执行校验。
     */
    public List<NamingConventionRuleResult> validate(Sign sign) {
        return validateWithRules(sign, defaultRules);
    }

    /**
     * 根据给定的 {@link NamingConvention} 生成规则并执行校验。
     * <p>
     * 优先使用 {@code rulesJson} 中定义的机器规则；若不存在，则根据
     * semanticRule / syntacticRule / lexicalRule 标准属性生成规则。
     *
     * @param sign       待校验的名称
     * @param convention 命名规范实体
     * @return 校验结果列表
     */
    public List<NamingConventionRuleResult> validate(Sign sign, NamingConvention convention) {
        if (convention == null) {
            return validate(sign);
        }
        List<NamingConventionRule> rules = buildRulesFromConvention(convention);
        if (rules.isEmpty()) {
            return validate(sign);
        }
        return validateWithRules(sign, rules);
    }

    /**
     * 使用指定规则列表对 Sign 执行校验。
     */
    public List<NamingConventionRuleResult> validateWithRules(Sign sign, List<NamingConventionRule> rules) {
        List<NamingConventionRuleResult> results = new ArrayList<>();
        for (NamingConventionRule rule : rules) {
            results.add(rule.validate(sign));
        }
        return results;
    }

    /**
     * 快速校验：只要有一个规则不通过即返回 false。
     */
    public boolean isValid(Sign sign) {
        return validate(sign).stream().allMatch(NamingConventionRuleResult::isPassed);
    }

    /**
     * 根据命名规范实体判断名称是否有效。
     */
    public boolean isValid(Sign sign, NamingConvention convention) {
        return validate(sign, convention).stream().allMatch(NamingConventionRuleResult::isPassed);
    }

    /**
     * 获取已注册的所有默认规则。
     */
    public List<NamingConventionRule> getDefaultRules() {
        return Collections.unmodifiableList(defaultRules);
    }

    /**
     * 根据命名规范实体生成规则列表。
     */
    public List<NamingConventionRule> buildRulesFromConvention(NamingConvention convention) {
        if (convention == null) {
            return List.copyOf(defaultRules);
        }
        if (StringUtils.hasText(convention.getRulesJson())) {
            try {
                return parseJsonRules(convention.getRulesJson());
            } catch (RuntimeException e) {
                // 如果 JSON 规则解析失败，回退到从标准属性生成规则
            }
        }
        return buildRulesFromStandardAttributes(convention);
    }

    private List<NamingConventionRule> parseJsonRules(String rulesJson) {
        List<JsonRule> jsonRules;
        try {
            jsonRules = objectMapper.readValue(rulesJson, new TypeReference<>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid rulesJson: " + e.getMessage(), e);
        }
        List<NamingConventionRule> rules = new ArrayList<>(jsonRules.size());
        for (JsonRule rule : jsonRules) {
            switch (rule.type() == null ? "" : rule.type().toLowerCase()) {
                case "length" -> rules.add(new LengthNamingConventionRule(
                        rule.min() == null ? LengthNamingConventionRule.DEFAULT_MIN_LENGTH : rule.min(),
                        rule.max() == null ? LengthNamingConventionRule.DEFAULT_MAX_LENGTH : rule.max()));
                case "regex" -> {
                    if (StringUtils.hasText(rule.pattern())) {
                        rules.add(new RegexNamingConventionRule(
                                rule.name() == null ? "regex" : rule.name(),
                                rule.pattern(),
                                rule.description()));
                    }
                }
                case "contains" -> {
                    if (StringUtils.hasText(rule.keywords())) {
                        rules.add(new ContainsNamingConventionRule(
                                rule.name() == null ? "contains" : rule.name(),
                                rule.keywords(),
                                rule.requireAll() == null || rule.requireAll()));
                    }
                }
                default -> {
                    // 未知规则类型忽略
                }
            }
        }
        return rules;
    }

    private List<NamingConventionRule> buildRulesFromStandardAttributes(NamingConvention convention) {
        List<NamingConventionRule> rules = new ArrayList<>();
        addRegexRuleIfValid(rules, "lexical", textValue(convention.getLexicalRule()));
        addRegexRuleIfValid(rules, "syntactic", textValue(convention.getSyntacticRule()));
        addSemanticRule(rules, textValue(convention.getSemanticRule()));
        return rules;
    }

    private static void addRegexRuleIfValid(List<NamingConventionRule> rules, String name, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String pattern = extractRegex(value);
        if (pattern != null) {
            try {
                rules.add(new RegexNamingConventionRule(name, pattern, value));
            } catch (IllegalArgumentException e) {
                // 无效正则则忽略
            }
        }
    }

    private static void addSemanticRule(List<NamingConventionRule> rules, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        // 语义规则默认要求包含任意一个关键词
        rules.add(new ContainsNamingConventionRule("semantic", value, false));
    }

    /**
     * 如果文本以 / 开头和结尾，则中间部分作为正则表达式返回；否则返回 null。
     */
    private static String extractRegex(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("/") && trimmed.endsWith("/")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return null;
    }

    private static String textValue(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text text) {
        return text == null ? null : text.getValue();
    }

    private static List<NamingConventionRule> buildDefaultRules() {
        return List.of(
                new LengthNamingConventionRule(),
                new CharsetNamingConventionRule(),
                new RegexNamingConventionRule()
        );
    }

    /**
     * JSON 规则定义。字段稀疏，仅使用对应规则类型所需的字段。
     */
    private record JsonRule(
            String type,
            String name,
            Integer min,
            Integer max,
            String pattern,
            String description,
            String keywords,
            Boolean requireAll) {
    }
}