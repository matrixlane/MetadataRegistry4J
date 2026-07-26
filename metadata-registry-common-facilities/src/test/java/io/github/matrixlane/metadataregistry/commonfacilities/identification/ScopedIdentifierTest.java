package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScopedIdentifierTest {

    @Test
    void shouldSetFields() {
        Namespace ns = new Namespace();
        IdentifiedItem item = new IdentifiedItem();
        ScopedIdentifier si = new ScopedIdentifier("urn:demo:123", ns, item);
        assertThat(si.getIdentifier()).isEqualTo("urn:demo:123");
        assertThat(si.getNamespace()).isEqualTo(ns);
        assertThat(si.getIdentifiedItem()).isEqualTo(item);
    }

    @Test
    void shouldDefaultToDraftVersionStatus() {
        ScopedIdentifier si = new ScopedIdentifier();
        assertThat(si.getVersionStatus()).isEqualTo(VersionStatus.DRAFT);
    }

    @Test
    void shouldAllowVersionUpdate() {
        ScopedIdentifier si = new ScopedIdentifier();
        si.setVersion("2.0.0");
        assertThat(si.getVersion()).isEqualTo("2.0.0");
    }
}
