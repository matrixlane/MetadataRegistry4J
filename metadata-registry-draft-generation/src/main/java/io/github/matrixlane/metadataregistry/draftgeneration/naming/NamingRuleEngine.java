package io.github.matrixlane.metadataregistry.draftgeneration.naming;

import io.github.matrixlane.metadataregistry.draftgeneration.model.NamingSuggestion;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 命名规则引擎。
 *
 * <p>管理一组命名规则，依次对名称应用所有规则，收集所有建议。
 * 规则顺序决定优先级，第一个匹配的建议被采纳为默认建议。
 */
@Slf4j
public class NamingRuleEngine {

    private final List<NamingRule> rules;

    public NamingRuleEngine() {
        this(buildDefaultRules());
    }

    public NamingRuleEngine(List<NamingRule> rules) {
        this.rules = rules == null ? Collections.emptyList() : new ArrayList<>(rules);
    }

    /**
     * 对给定名称应用所有启用的命名规则。
     *
     * @param originalName 原始名称
     * @return 所有适用的命名建议列表
     */
    public List<NamingSuggestion> suggest(String originalName) {
        List<NamingSuggestion> suggestions = new ArrayList<>();
        if (originalName == null || originalName.isBlank()) {
            return suggestions;
        }

        for (NamingRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            try {
                Optional<NamingSuggestion> suggestion = rule.apply(originalName);
                suggestion.ifPresent(suggestions::add);
            } catch (Exception e) {
                log.warn("命名规则 '{}' 应用失败: {}", rule.getName(), e.getMessage());
            }
        }

        return suggestions;
    }

    /**
     * 获取第一个建议（优先级最高）。
     *
     * @param originalName 原始名称
     * @return 第一个适用的建议，如果没有则返回 null
     */
    public NamingSuggestion getFirstSuggestion(String originalName) {
        return suggest(originalName).stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * 对名称应用所有规则并返回最终结果。
     * 如果没有任何规则匹配，返回原始名称。
     */
    public String normalize(String originalName) {
        NamingSuggestion first = getFirstSuggestion(originalName);
        return first != null ? first.getSuggestedName() : originalName;
    }

    /**
     * 获取已注册的所有规则（不可修改视图）。
     */
    public List<NamingRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    private static List<NamingRule> buildDefaultRules() {
        return List.of(
                new RemovePrefixRule("tbl_"),
                new RemovePrefixRule("TBL_"),
                new SnakeToCamelRule()
        );
    }
}
