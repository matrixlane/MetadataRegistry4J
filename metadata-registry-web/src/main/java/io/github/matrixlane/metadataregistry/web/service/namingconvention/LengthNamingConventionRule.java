package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import lombok.Getter;

/**
 * 长度检查命名规范规则。
 *
 * <p>检查 Sign 值的长度是否在允许的范围内。
 */
@Getter
public class LengthNamingConventionRule implements NamingConventionRule {

    public static final int DEFAULT_MIN_LENGTH = 1;
    public static final int DEFAULT_MAX_LENGTH = 255;

    private final int minLength;
    private final int maxLength;

    public LengthNamingConventionRule() {
        this(DEFAULT_MIN_LENGTH, DEFAULT_MAX_LENGTH);
    }

    public LengthNamingConventionRule(int minLength, int maxLength) {
        if (minLength < 0) {
            throw new IllegalArgumentException("minLength must be >= 0");
        }
        if (maxLength < minLength) {
            throw new IllegalArgumentException("maxLength must be >= minLength");
        }
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    @Override
    public String getName() {
        return "length";
    }

    @Override
    public String getDescription() {
        return "名称长度应在 %d 到 %d 个字符之间".formatted(minLength, maxLength);
    }

    @Override
    public NamingConventionRuleResult validate(Sign sign) {
        String value = sign == null ? null : sign.getValue();
        if (value == null || value.isBlank()) {
            return NamingConventionRuleResult.fail(getName(), "名称不能为空");
        }
        int length = value.length();
        if (length < minLength) {
            return NamingConventionRuleResult.fail(getName(),
                    "名称长度 %d 小于最小限制 %d".formatted(length, minLength));
        }
        if (length > maxLength) {
            return NamingConventionRuleResult.fail(getName(),
                    "名称长度 %d 超过最大限制 %d".formatted(length, maxLength));
        }
        return NamingConventionRuleResult.pass(getName());
    }
}
