package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScopedIdentifierTest {

    @Test
    void shouldSetFields() {
        ScopedIdentifier si = new ScopedIdentifier();
        si.setIdentifier("urn:example:123");
        si.setVersion("1.0");
        assertThat(si.getIdentifier()).isEqualTo("urn:example:123");
        assertThat(si.getVersion()).isEqualTo("1.0");
    }

    @Test
    void shouldSetNamespace() {
        ScopedIdentifier si = new ScopedIdentifier();
        Namespace ns = new Namespace();
        si.setNamespace(ns);
        assertThat(si.getNamespace()).isEqualTo(ns);
    }

    @Test
    void shouldSetIdentifiedItem() {
        ScopedIdentifier si = new ScopedIdentifier();
        IdentifiedItem ii = new IdentifiedItem();
        si.setIdentifiedItem(ii);
        assertThat(si.getIdentifiedItem()).isEqualTo(ii);
    }
}
