package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlotTest {

    @Test
    void shouldCreateWithConstructor() {
        Item item = new Context();
        Slot slot = new Slot("color", "red", "xsd:string", item);
        assertThat(slot.getName()).isEqualTo("color");
        assertThat(slot.getValue()).containsExactly("red");
        assertThat(slot.getValue_datatype()).isEqualTo("xsd:string");
        assertThat(slot.getItem()).isEqualTo(item);
    }

    @Test
    void shouldSetFields() {
        Slot slot = new Slot();
        slot.setName("size");
        slot.getValue().add("42");
        slot.setValue_datatype("xsd:integer");
        slot.setType("legacy-type");
        assertThat(slot.getName()).isEqualTo("size");
        assertThat(slot.getValue()).containsExactly("42");
        assertThat(slot.getValue_datatype()).isEqualTo("xsd:integer");
        assertThat(slot.getType()).isEqualTo("legacy-type");
    }

    @Test
    void noArgConstructorShouldInitialize() {
        Slot slot = new Slot();
        assertThat(slot.getId()).isNull();
        assertThat(slot.getName()).isNull();
        assertThat(slot.getValue()).isEmpty();
        assertThat(slot.getValue_datatype()).isNull();
        assertThat(slot.getItem()).isNull();
    }

    @Test
    void shouldAllowMultipleSlotsForSameItemWithDifferentNames() {
        Item item = new Context();
        Slot slot1 = new Slot("color", "red", "xsd:string", item);
        Slot slot2 = new Slot("size", "42", "xsd:integer", item);
        assertThat(slot1.getItem()).isEqualTo(item);
        assertThat(slot2.getItem()).isEqualTo(item);
        assertThat(slot1.getName()).isNotEqualTo(slot2.getName());
    }

    @Test
    void shouldAllowSameSlotNameForDifferentItems() {
        Item item1 = new Context();
        Item item2 = new Context();
        Slot slot1 = new Slot("color", "red", "xsd:string", item1);
        Slot slot2 = new Slot("color", "blue", "xsd:string", item2);
        assertThat(slot1.getName()).isEqualTo(slot2.getName());
        assertThat(slot1.getItem()).isNotEqualTo(slot2.getItem());
    }

    @Test
    void shouldUpdateItemReference() {
        Slot slot = new Slot();
        Item item = new Context();
        slot.setItem(item);
        assertThat(slot.getItem()).isEqualTo(item);
    }

    @Test
    void shouldUpdateValue() {
        Slot slot = new Slot("status", "active", "xsd:string", new Context());
        slot.getValue().clear();
        slot.getValue().add("inactive");
        assertThat(slot.getValue()).containsExactly("inactive");
    }

    @Test
    void shouldSupportNullValue() {
        Slot slot = new Slot("optional", null, "xsd:string", new Context());
        assertThat(slot.getValue()).isEmpty();
    }

    @Test
    void shouldSupportEmptyStringValue() {
        Slot slot = new Slot("empty", "", "xsd:string", new Context());
        assertThat(slot.getValue()).containsExactly("");
    }

    @Test
    void deprecatedTypeFieldShouldBeAccessible() {
        Slot slot = new Slot();
        slot.setType("old-type");
        assertThat(slot.getType()).isEqualTo("old-type");
    }
}
