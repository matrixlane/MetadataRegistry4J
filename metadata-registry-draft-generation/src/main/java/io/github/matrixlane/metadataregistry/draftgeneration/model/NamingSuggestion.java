package io.github.matrixlane.metadataregistry.draftgeneration.model;

import lombok.Builder;
import lombok.Data;

/**
 * 命名规范化建议。
 */
@Data
@Builder
public class NamingSuggestion {

    /**
     * 原始名称。
     */
    private String originalName;

    /**
     * 建议名称。
     */
    private String suggestedName;

    /**
     * 应用的规则名称。
     */
    private String ruleName;

    /**
     * 用户是否采纳此建议。
     */
    @Builder.Default
    private boolean accepted = false;
}
