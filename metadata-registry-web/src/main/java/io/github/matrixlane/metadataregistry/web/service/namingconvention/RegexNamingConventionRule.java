package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import lombok.Getter;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 正则表达式命名规范规则。
 *
 * <p>使用自定义正则表达式检查 Sign 值是否符合特定模式。
 */
@Getter
public class RegexNamingConventionRule implements NamingConventionRule {

    public static final String DEFAULT_RULE_NAME = "regex";
    public static final String DEFAULT_REGEX = "^[a-zA-Z_\\u4e00-\\u9fa5][a-zA-Z0-9_\\-\\u4e00-\\u9fa5]*$";
    public static final String DEFAULT_PATTERN_DESCRIPTION = "以字母、下划线或中文开头，后续为字母、数字、下划线、连字符或中文";

    private final String ruleName;
    private final String patternDescription;
    private final Pattern pattern;

    /**
     * 使用默认正则规则构造：标识符格式检查。
     */
    public RegexNamingConventionRule() {
        this(DEFAULT_RULE_NAME, DEFAULT_REGEX, DEFAULT_PATTERN_DESCRIPTION);
    }

    public RegexNamingConventionRule(String ruleName, String regex, String patternDescription) {
        if (ruleName == null || ruleName.isBlank()) {
            throw new IllegalArgumentException("ruleName must not be blank");
        }
        if (regex == null || regex.isBlank()) {
            throw new IllegalArgumentException("regex must not be blank");
        }
        this.ruleName = ruleName;
        this.patternDescription = patternDescription == null ? regex : patternDescription;
        try {
            this.pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex: " + regex, e);
        }
    }

    @Override
    public String getName() {
        return ruleName;
    }

    @Override
    public String getDescription() {
        return "名称必须匹配正则模式：" + patternDescription;
    }

    @Override
    public NamingConventionRuleResult validate(Sign sign) {
        String value = sign == null ? null : sign.getValue();
        if (value == null || value.isBlank()) {
            return NamingConventionRuleResult.fail(getName(), "名称不能为空");
        }
        if (!pattern.matcher(value).matches()) {
            return NamingConventionRuleResult.fail(getName(),
                    "名称不匹配正则模式：" + patternDescription);
        }
        return NamingConventionRuleResult.pass(getName());
    }
}
