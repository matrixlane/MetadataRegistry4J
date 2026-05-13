package io.github.matrixlane.metadataregistry.draftgeneration.naming;

import io.github.matrixlane.metadataregistry.draftgeneration.model.NamingSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SnakeToCamelRuleTest {

    private SnakeToCamelRule rule;

    @BeforeEach
    void setUp() {
        rule = new SnakeToCamelRule();
    }

    @Test
    void getName_shouldReturnCorrectValue() {
        assertThat(rule.getName()).isEqualTo("SNAKE_TO_CAMEL");
    }

    @Test
    void getDescription_shouldReturnCorrectValue() {
        assertThat(rule.getDescription()).isEqualTo("下划线命名转小驼峰命名 (snake_case → camelCase)");
    }

    @Test
    void apply_shouldConvertSimpleSnakeCase() {
        Optional<NamingSuggestion> result = rule.apply("user_name");

        assertThat(result).isPresent();
        assertThat(result.get().getSuggestedName()).isEqualTo("userName");
        assertThat(result.get().getOriginalName()).isEqualTo("user_name");
        assertThat(result.get().getRuleName()).isEqualTo("SNAKE_TO_CAMEL");
    }

    @Test
    void apply_shouldConvertMultipleUnderscores() {
        Optional<NamingSuggestion> result = rule.apply("order_item_detail");

        assertThat(result).isPresent();
        assertThat(result.get().getSuggestedName()).isEqualTo("orderItemDetail");
    }

    @Test
    void apply_shouldConvertUpperCaseSnakeCase() {
        Optional<NamingSuggestion> result = rule.apply("USER_NAME");

        assertThat(result).isPresent();
        assertThat(result.get().getSuggestedName()).isEqualTo("userName");
    }

    @Test
    void apply_shouldReturnEmptyForNoUnderscore() {
        Optional<NamingSuggestion> result = rule.apply("username");

        assertThat(result).isEmpty();
    }

    @Test
    void apply_shouldReturnEmptyForNull() {
        Optional<NamingSuggestion> result = rule.apply(null);

        assertThat(result).isEmpty();
    }

    @Test
    void apply_shouldHandleConsecutiveUnderscores() {
        Optional<NamingSuggestion> result = rule.apply("user__name");

        assertThat(result).isPresent();
        assertThat(result.get().getSuggestedName()).isEqualTo("userName");
    }

    @Test
    void apply_shouldHandleTrailingUnderscore() {
        Optional<NamingSuggestion> result = rule.apply("user_name_");

        assertThat(result).isPresent();
        assertThat(result.get().getSuggestedName()).isEqualTo("userName");
    }
}
