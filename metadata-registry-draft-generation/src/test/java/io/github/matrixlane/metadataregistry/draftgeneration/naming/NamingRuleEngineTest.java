package io.github.matrixlane.metadataregistry.draftgeneration.naming;

import io.github.matrixlane.metadataregistry.draftgeneration.model.NamingSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NamingRuleEngineTest {

    private NamingRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new NamingRuleEngine();
    }

    @Test
    void constructor_shouldInitializeWithDefaultRules() {
        assertThat(engine.getRules()).hasSize(3);
    }

    @Test
    void suggest_shouldApplySnakeToCamelRule() {
        List<NamingSuggestion> suggestions = engine.suggest("user_name");

        assertThat(suggestions).hasSizeGreaterThanOrEqualTo(1);
        assertThat(suggestions.get(0).getSuggestedName()).isEqualTo("userName");
    }

    @Test
    void suggest_shouldApplyRemovePrefixRule() {
        List<NamingSuggestion> suggestions = engine.suggest("tbl_user");

        assertThat(suggestions).hasSizeGreaterThanOrEqualTo(1);
        assertThat(suggestions.get(0).getSuggestedName()).isEqualTo("user");
    }

    @Test
    void suggest_shouldReturnMultipleSuggestions() {
        // tbl_user_name matches both RemovePrefixRule and SnakeToCamelRule
        List<NamingSuggestion> suggestions = engine.suggest("tbl_user_name");

        assertThat(suggestions).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void suggest_shouldReturnEmptyForNull() {
        List<NamingSuggestion> suggestions = engine.suggest(null);

        assertThat(suggestions).isEmpty();
    }

    @Test
    void suggest_shouldReturnEmptyForBlank() {
        List<NamingSuggestion> suggestions = engine.suggest("   ");

        assertThat(suggestions).isEmpty();
    }

    @Test
    void getFirstSuggestion_shouldReturnFirstMatch() {
        NamingSuggestion suggestion = engine.getFirstSuggestion("user_name");

        assertThat(suggestion).isNotNull();
        assertThat(suggestion.getSuggestedName()).isEqualTo("userName");
    }

    @Test
    void getFirstSuggestion_shouldReturnNullForNoMatch() {
        NamingSuggestion suggestion = engine.getFirstSuggestion("user");

        assertThat(suggestion).isNull();
    }

    @Test
    void normalize_shouldReturnSuggestedName() {
        String result = engine.normalize("user_name");

        assertThat(result).isEqualTo("userName");
    }

    @Test
    void normalize_shouldReturnOriginalWhenNoMatch() {
        String result = engine.normalize("user");

        assertThat(result).isEqualTo("user");
    }

    @Test
    void constructor_withCustomRules_shouldUseProvidedRules() {
        NamingRuleEngine customEngine = new NamingRuleEngine(List.of(new SnakeToCamelRule()));

        assertThat(customEngine.getRules()).hasSize(1);
    }

    @Test
    void constructor_withNullRules_shouldUseEmptyList() {
        NamingRuleEngine emptyEngine = new NamingRuleEngine(null);

        assertThat(emptyEngine.getRules()).isEmpty();
        assertThat(emptyEngine.suggest("user_name")).isEmpty();
    }

    @Test
    void getRules_shouldReturnUnmodifiableList() {
        List<NamingRule> rules = engine.getRules();

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> rules.add(new SnakeToCamelRule()));
    }
}
