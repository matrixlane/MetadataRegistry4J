package io.github.matrixlane.metadataregistry.draftgeneration.naming;

import io.github.matrixlane.metadataregistry.draftgeneration.model.NamingSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RemovePrefixRuleTest {

    @Test
    void apply_shouldRemoveTblPrefix() {
        RemovePrefixRule rule = new RemovePrefixRule("tbl_");

        Optional<NamingSuggestion> result = rule.apply("tbl_user");

        assertThat(result).isPresent();
        assertThat(result.get().getSuggestedName()).isEqualTo("user");
        assertThat(result.get().getRuleName()).isEqualTo("REMOVE_PREFIX_TBL_");
    }

    @Test
    void apply_shouldRemoveTblPrefixCaseInsensitive() {
        RemovePrefixRule rule = new RemovePrefixRule("tbl_");

        Optional<NamingSuggestion> result = rule.apply("TBL_USER");

        assertThat(result).isPresent();
        assertThat(result.get().getSuggestedName()).isEqualTo("USER");
    }

    @Test
    void apply_shouldReturnEmptyWhenNoPrefix() {
        RemovePrefixRule rule = new RemovePrefixRule("tbl_");

        Optional<NamingSuggestion> result = rule.apply("user");

        assertThat(result).isEmpty();
    }

    @Test
    void apply_shouldReturnEmptyForNull() {
        RemovePrefixRule rule = new RemovePrefixRule("tbl_");

        Optional<NamingSuggestion> result = rule.apply(null);

        assertThat(result).isEmpty();
    }

    @Test
    void apply_shouldReturnEmptyForEmptyPrefix() {
        RemovePrefixRule rule = new RemovePrefixRule("");

        Optional<NamingSuggestion> result = rule.apply("tbl_user");

        assertThat(result).isEmpty();
    }

    @Test
    void apply_shouldRespectCaseSensitive() {
        RemovePrefixRule rule = new RemovePrefixRule("tbl_", true);

        Optional<NamingSuggestion> exact = rule.apply("tbl_user");
        Optional<NamingSuggestion> differentCase = rule.apply("TBL_USER");

        assertThat(exact).isPresent();
        assertThat(differentCase).isEmpty();
    }

    @Test
    void apply_shouldReturnEmptyIfOnlyPrefix() {
        RemovePrefixRule rule = new RemovePrefixRule("tbl_");

        Optional<NamingSuggestion> result = rule.apply("tbl_");

        assertThat(result).isEmpty();
    }
}
