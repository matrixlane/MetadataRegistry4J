package io.github.matrixlane.metadataregistry.draftgeneration.naming;

import io.github.matrixlane.metadataregistry.draftgeneration.model.NamingSuggestion;

import java.util.Optional;

/**
 * 去除指定前缀的命名规则。
 *
 * <p>示例（前缀为 "tbl_"）：
 * <ul>
 *   <li>tbl_user → user</li>
 *   <li>TBL_USER → USER</li>
 *   <li>user → （不匹配）</li>
 * </ul>
 */
public class RemovePrefixRule implements NamingRule {

    private final String prefix;
    private final boolean caseSensitive;

    public RemovePrefixRule(String prefix) {
        this(prefix, false);
    }

    public RemovePrefixRule(String prefix, boolean caseSensitive) {
        this.prefix = prefix;
        this.caseSensitive = caseSensitive;
    }

    @Override
    public String getName() {
        return "REMOVE_PREFIX_" + prefix.toUpperCase();
    }

    @Override
    public String getDescription() {
        return "去除前缀: " + prefix;
    }

    @Override
    public Optional<NamingSuggestion> apply(String originalName) {
        if (originalName == null || prefix == null || prefix.isEmpty()) {
            return Optional.empty();
        }

        boolean matches = caseSensitive
                ? originalName.startsWith(prefix)
                : originalName.toLowerCase().startsWith(prefix.toLowerCase());

        if (!matches) {
            return Optional.empty();
        }

        String suggestedName = originalName.substring(prefix.length());
        if (suggestedName.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(NamingSuggestion.builder()
                .originalName(originalName)
                .suggestedName(suggestedName)
                .ruleName(getName())
                .build());
    }
}
