package io.github.matrixlane.metadataregistry.draftgeneration.naming;

import io.github.matrixlane.metadataregistry.draftgeneration.model.NamingSuggestion;

import java.util.Optional;

/**
 * 命名规范化规则接口。
 *
 * <p>每条规则对给定的原始名称进行评估，如果适用则返回建议名称。
 */
public interface NamingRule {

    /**
     * 规则唯一标识名称。
     */
    String getName();

    /**
     * 规则描述。
     */
    String getDescription();

    /**
     * 对原始名称应用命名规则。
     *
     * @param originalName 原始名称
     * @return 如果规则适用，返回 NamingSuggestion；否则返回 Optional.empty()
     */
    Optional<NamingSuggestion> apply(String originalName);

    /**
     * 规则是否启用。
     */
    default boolean isEnabled() {
        return true;
    }
}
