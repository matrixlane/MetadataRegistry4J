package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifiedItemTest {

    @Test
    void shouldConstructWithItem() {
        Context item = new Context();
        IdentifiedItem identifiedItem = new IdentifiedItem(item);
        assertThat(identifiedItem.getItem()).isEqualTo(item);
    }

    @Test
    void shouldInitializeScopedIdentifiers() {
        IdentifiedItem identifiedItem = new IdentifiedItem();
        assertThat(identifiedItem.getScopedIdentifiers()).isEmpty();
    }

    @Test
    void shouldAddScopedIdentifier() {
        IdentifiedItem identifiedItem = new IdentifiedItem();
        ScopedIdentifier si = new ScopedIdentifier();
        identifiedItem.getScopedIdentifiers().add(si);
        assertThat(identifiedItem.getScopedIdentifiers()).hasSize(1);
    }
}
