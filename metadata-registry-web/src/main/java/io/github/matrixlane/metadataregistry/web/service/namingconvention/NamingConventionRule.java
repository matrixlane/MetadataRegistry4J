package io.github.matrixlane.metadataregistry.web.service.namingconvention;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;

/**
 * 命名规范规则接口。
 *
 * <p>V1 采用策略模式硬编码规则，预留扩展接口供 V2 替换为更复杂的规则引擎。
 */
public interface NamingConventionRule {

    /**
     * 规则名称。
     */
    String getName();

    /**
     * 规则描述。
     */
    String getDescription();

    /**
     * 对给定的 Sign（名称）执行校验。
     *
     * @param sign 待校验的名称
     * @return 校验结果
     */
    NamingConventionRuleResult validate(Sign sign);
}
