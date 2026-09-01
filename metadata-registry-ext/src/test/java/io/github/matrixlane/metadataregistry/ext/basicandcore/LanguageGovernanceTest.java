package io.github.matrixlane.metadataregistry.ext.basicandcore;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageGovernanceTest {

    @Test
    void defaultsToEnabled() {
        LanguageGovernance governance = new LanguageGovernance();
        assertThat(governance.getEnabled()).isTrue();
    }

    @Test
    void holdsLanguageValueKey() {
        UUID languageId = UUID.randomUUID();
        LanguageGovernance governance = new LanguageGovernance(languageId);
        assertThat(governance.getLanguageId()).isEqualTo(languageId);
        governance.setEnabled(false);
        assertThat(governance.getEnabled()).isFalse();
    }
}
