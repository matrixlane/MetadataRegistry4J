package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 命名规范规则校验结果。
 */
@Getter
public class NamingConventionRuleResult {

    private final boolean passed;
    private final String ruleName;
    private final List<String> messages;

    private NamingConventionRuleResult(boolean passed, String ruleName, List<String> messages) {
        this.passed = passed;
        this.ruleName = ruleName;
        this.messages = messages;
    }

    public static NamingConventionRuleResult pass(String ruleName) {
        return new NamingConventionRuleResult(true, ruleName, Collections.emptyList());
    }

    public static NamingConventionRuleResult fail(String ruleName, String message) {
        return new NamingConventionRuleResult(false, ruleName, List.of(message));
    }

    public static NamingConventionRuleResult fail(String ruleName, List<String> messages) {
        return new NamingConventionRuleResult(false, ruleName, new ArrayList<>(messages));
    }
}
