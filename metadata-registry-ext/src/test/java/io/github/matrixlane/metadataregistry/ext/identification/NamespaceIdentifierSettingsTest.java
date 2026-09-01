package io.github.matrixlane.metadataregistry.ext.identification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamespaceIdentifierSettingsTest {

    @Test
    void shouldDefaultToSemverAndAutoWithOverride() {
        NamespaceIdentifierSettings settings = new NamespaceIdentifierSettings();
        assertThat(settings.getVersioningPolicy()).isEqualTo("SEMVER");
        assertThat(settings.getIdentifierInputMode()).isEqualTo("AUTO_WITH_OVERRIDE");
        assertThat(settings.getVersionInputMode()).isEqualTo("AUTO_WITH_OVERRIDE");
        assertThat(settings.getManualIdentifierPattern()).isNull();
        assertThat(settings.getNamespaceId()).isNull();
        assertThat(settings.getId()).isNull();
    }
}
