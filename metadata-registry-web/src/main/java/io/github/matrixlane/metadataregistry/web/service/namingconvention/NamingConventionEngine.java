package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 命名规范引擎。
 *
 * <p>V1 采用策略模式，硬编码注册基础规则集，预留扩展接口供 V2 替换。
 */
@Component
public class NamingConventionEngine {

    private final List<NamingConventionRule> rules;

    public NamingConventionEngine() {
        this(buildDefaultRules());
    }

    public NamingConventionEngine(List<NamingConventionRule> rules) {
        this.rules = rules == null ? Collections.emptyList() : new ArrayList<>(rules);
    }

    /**
     * 对给定的 Sign 执行所有已注册规则的校验。
     *
     * @param sign 待校验的名称
     * @return 所有规则的校验结果
     */
    public List<NamingConventionRuleResult> validate(Sign sign) {
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
     * 获取已注册的所有规则。
     */
    public List<NamingConventionRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    private static List<NamingConventionRule> buildDefaultRules() {
        return List.of(
                new LengthNamingConventionRule(),
                new CharsetNamingConventionRule(),
                new RegexNamingConventionRule()
        );
    }
}
