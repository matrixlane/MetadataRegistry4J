package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 字符集检查命名规范规则。
 *
 * <p>检查 Sign 值仅包含允许的字符集。
 */
@Getter
public class CharsetNamingConventionRule implements NamingConventionRule {

    /**
     * 默认允许的字符集：字母、数字、下划线、连字符、空格、中文。
     */
    public static final String DEFAULT_ALLOWED_CHARSET = "A-Za-z0-9_\\- \\u4e00-\\u9fa5";
    public static final String DEFAULT_ALLOWED_PATTERN = "^[A-Za-z0-9_\\- \\u4e00-\\u9fa5]+$";

    private final Set<CharacterSet> allowedSets;
    private final Pattern pattern;
    private final String allowedCharsDescription;

    public CharsetNamingConventionRule() {
        this(Set.of(CharacterSet.ALPHANUMERIC, CharacterSet.UNDERSCORE,
                CharacterSet.HYPHEN, CharacterSet.SPACE, CharacterSet.CJK));
    }

    public CharsetNamingConventionRule(Set<CharacterSet> allowedSets) {
        if (allowedSets == null || allowedSets.isEmpty()) {
            throw new IllegalArgumentException("allowedSets must not be empty");
        }
        this.allowedSets = Set.copyOf(allowedSets);
        this.pattern = buildPattern(this.allowedSets);
        this.allowedCharsDescription = buildDescription(this.allowedSets);
    }

    @Override
    public String getName() {
        return "charset";
    }

    @Override
    public String getDescription() {
        return "名称只能包含以下字符类型：" + allowedCharsDescription;
    }

    @Override
    public NamingConventionRuleResult validate(Sign sign) {
        String value = sign == null ? null : sign.getValue();
        if (value == null || value.isBlank()) {
            return NamingConventionRuleResult.fail(getName(), "名称不能为空");
        }
        if (!pattern.matcher(value).matches()) {
            return NamingConventionRuleResult.fail(getName(),
                    "名称包含非法字符，只允许：" + allowedCharsDescription);
        }
        return NamingConventionRuleResult.pass(getName());
    }

    private static Pattern buildPattern(Set<CharacterSet> sets) {
        StringBuilder sb = new StringBuilder("^[");
        for (CharacterSet cs : sets) {
            sb.append(cs.regexClass);
        }
        sb.append("]+$");
        return Pattern.compile(sb.toString());
    }

    private static String buildDescription(Set<CharacterSet> sets) {
        List<String> parts = new ArrayList<>();
        for (CharacterSet cs : sets) {
            parts.add(cs.description);
        }
        return String.join("、", parts);
    }

    public enum CharacterSet {
        ALPHANUMERIC("A-Za-z0-9", "字母和数字"),
        UNDERSCORE("_", "下划线"),
        HYPHEN("\\-", "连字符"),
        SPACE(" ", "空格"),
        CJK("\\u4e00-\\u9fa5", "中日韩文字"),
        DOT("\\.", "点号"),
        SLASH("/", "斜杠");

        private final String regexClass;
        private final String description;

        CharacterSet(String regexClass, String description) {
            this.regexClass = regexClass;
            this.description = description;
        }
    }
}
