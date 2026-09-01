package io.github.matrixlane.metadataregistry.ext.identification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScopedIdentifierLifecycleTest {

    @Test
    void shouldDefaultToDraftVersionStatus() {
        ScopedIdentifierLifecycle lifecycle = new ScopedIdentifierLifecycle();
        assertThat(lifecycle.getVersionStatus()).isEqualTo(VersionStatus.DRAFT);
        assertThat(lifecycle.getIdentifierCreationMode()).isNull();
        assertThat(lifecycle.getVersionCreationMode()).isNull();
        assertThat(lifecycle.getCreatedAt()).isNull();
    }
}
