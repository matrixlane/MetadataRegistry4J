package io.github.matrixlane.metadataregistry.draftgeneration.naming;

import io.github.matrixlane.metadataregistry.draftgeneration.model.NamingSuggestion;

import java.util.Optional;

/**
 * 下划线命名转驼峰命名规则。
 *
 * <p>示例：
 * <ul>
 *   <li>user_name → userName</li>
 *   <li>order_item_detail → orderItemDetail</li>
 *   <li>USER_NAME → userName</li>
 * </ul>
 */
public class SnakeToCamelRule implements NamingRule {

    @Override
    public String getName() {
        return "SNAKE_TO_CAMEL";
    }

    @Override
    public String getDescription() {
        return "下划线命名转小驼峰命名 (snake_case → camelCase)";
    }

    @Override
    public Optional<NamingSuggestion> apply(String originalName) {
        if (originalName == null || !originalName.contains("_")) {
            return Optional.empty();
        }

        String[] parts = originalName.split("_");
        if (parts.length < 2) {
            return Optional.empty();
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            if (i == 0) {
                result.append(part.toLowerCase());
            } else {
                result.append(capitalize(part));
            }
        }

        String suggestedName = result.toString();
        if (suggestedName.equals(originalName)) {
            return Optional.empty();
        }

        return Optional.of(NamingSuggestion.builder()
                .originalName(originalName)
                .suggestedName(suggestedName)
                .ruleName(getName())
                .build());
    }

    private String capitalize(String str) {
        if (str.length() <= 1) {
            return str.toUpperCase();
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
