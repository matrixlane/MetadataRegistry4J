package io.github.matrixlane.metadataregistry.commonfacilities.identification;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentifiedItemTest {

    @Test
    void shouldConstructWithItem() {
        Context item = new Context();
        AdministeredItem identifiedItem = new AdministeredItem(item);
        assertThat(identifiedItem.getItem()).isEqualTo(item);
    }

    @Test
    void shouldInitializeScopedIdentifiers() {
        AdministeredItem identifiedItem = new AdministeredItem();
        assertThat(identifiedItem.getScopedIdentifiers()).isEmpty();
    }

    @Test
    void shouldAddScopedIdentifier() {
        AdministeredItem identifiedItem = new AdministeredItem();
        ScopedIdentifier si = new ScopedIdentifier();
        identifiedItem.getScopedIdentifiers().add(si);
        assertThat(identifiedItem.getScopedIdentifiers()).hasSize(1);
    }
}
