package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisteredItemTest {

    @Test
    void shouldConstructWithItem() {
        Context item = new Context();
        RegisteredItem ri = new RegisteredItem(item);
        assertThat(ri).isNotNull();
    }

    @Test
    void shouldInitializeSubmissionRecords() {
        RegisteredItem ri = new RegisteredItem();
        assertThat(ri.getSubmissionRecords()).isEmpty();
    }

    @Test
    void shouldInheritIdentifiedItemBehaviour() {
        Context item = new Context();
        RegisteredItem ri = new RegisteredItem(item);
        assertThat(ri.getItem()).isEqualTo(item);
    }
}
