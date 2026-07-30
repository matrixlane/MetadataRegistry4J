package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 语义关键词包含规则。
 *
 * <p>要求名称必须包含指定的一个或多个关键词，用于从 semanticRule 等标准属性
 * 生成轻量级可执行规则。
 */
@Getter
public class ContainsNamingConventionRule implements NamingConventionRule {

    private final String ruleName;
    private final List<String> keywords;
    private final boolean requireAll;

    public ContainsNamingConventionRule(String ruleName, String keywords, boolean requireAll) {
        if (ruleName == null || ruleName.isBlank()) {
            throw new IllegalArgumentException("ruleName must not be blank");
        }
        this.ruleName = ruleName;
        this.keywords = splitKeywords(keywords);
        this.requireAll = requireAll;
    }

    private static List<String> splitKeywords(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("[,;，；|\\s]+"))
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .toList();
    }

    @Override
    public String getName() {
        return ruleName;
    }

    @Override
    public String getDescription() {
        return "名称应包含%s关键词：".formatted(requireAll ? "全部" : "任意") + String.join("、", keywords);
    }

    @Override
    public NamingConventionRuleResult validate(Sign sign) {
        String value = sign == null ? null : sign.getValue();
        if (value == null || value.isBlank()) {
            return NamingConventionRuleResult.fail(getName(), "名称不能为空");
        }
        if (keywords.isEmpty()) {
            return NamingConventionRuleResult.pass(getName());
        }
        long found = keywords.stream().filter(value::contains).count();
        if (requireAll && found != keywords.size()) {
            return NamingConventionRuleResult.fail(getName(),
                    "名称缺少必要关键词：" + String.join("、", keywords));
        }
        if (!requireAll && found == 0) {
            return NamingConventionRuleResult.fail(getName(),
                    "名称应至少包含一个关键词：" + String.join("、", keywords));
        }
        return NamingConventionRuleResult.pass(getName());
    }
}