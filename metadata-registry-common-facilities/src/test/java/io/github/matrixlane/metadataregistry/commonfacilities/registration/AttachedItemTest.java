package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttachedItemTest {

    @Test
    void shouldConstructWithItem() {
        Context item = new Context();
        AttachedItem attached = new AttachedItem(item);
        assertThat(attached).isNotNull();
    }

    @Test
    void shouldSetAdministeredItem() {
        AttachedItem attached = new AttachedItem(new Context());
        AdministeredItem ai = new AdministeredItem();
        attached.setAdministeredItem(ai);
        assertThat(attached.getAdministeredItem()).isEqualTo(ai);
    }
}
