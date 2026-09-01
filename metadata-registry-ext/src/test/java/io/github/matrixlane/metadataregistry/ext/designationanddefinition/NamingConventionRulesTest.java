package io.github.matrixlane.metadataregistry.ext.designationanddefinition;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NamingConventionRulesTest {

    @Test
    void defaultsToNullRulesJson() {
        NamingConventionRules rules = new NamingConventionRules();
        assertThat(rules.getRulesJson()).isNull();
    }

    @Test
    void holdsNamingConventionValueKey() {
        UUID conventionId = UUID.randomUUID();
        NamingConventionRules rules = new NamingConventionRules(conventionId);
        assertThat(rules.getNamingConventionId()).isEqualTo(conventionId);
        rules.setRulesJson("[{\"type\":\"regex\"}]");
        assertThat(rules.getRulesJson()).isEqualTo("[{\"type\":\"regex\"}]");
    }
}
