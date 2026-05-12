package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemTest {

    /**
     * Concrete test subclass of abstract {@link Item} for testing base behaviour.
     */
    private static class TestItem extends Item {
    }

    @Test
    void shouldInitializeCollections() {
        Item item = new TestItem();
        assertThat(item.getSlots()).isEmpty();
        assertThat(item.getDesignations()).isEmpty();
        assertThat(item.getDefinitions()).isEmpty();
    }

    @Test
    void shouldAddSlot() {
        Item item = new TestItem();
        Slot slot = new Slot("attr", "val", "xsd:string", item);
        item.getSlots().add(slot);
        assertThat(item.getSlots()).hasSize(1);
    }

    @Test
    void toStringShouldDelegateToDesignation() {
        Item item = new TestItem();
        Designation d = new Designation();
        Sign sign = new Sign();
        sign.setValue("Test Name");
        d.setSign(sign);
        item.getDesignations().add(d);
        assertThat(item.toString()).isEqualTo("Test Name");
    }

    @Test
    void toStringShouldFallBackToSuperWhenNoDesignations() {
        Item item = new TestItem();
        assertThat(item.toString()).contains(item.getClass().getSimpleName());
    }
}
