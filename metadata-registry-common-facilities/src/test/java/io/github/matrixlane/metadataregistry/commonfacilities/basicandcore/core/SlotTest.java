package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlotTest {

    @Test
    void shouldCreateWithConstructor() {
        Item item = new Item();
        Slot slot = new Slot("color", "red", "xsd:string", item);
        assertThat(slot.getName()).isEqualTo("color");
        assertThat(slot.getValue()).isEqualTo("red");
        assertThat(slot.getValue_datatype()).isEqualTo("xsd:string");
        assertThat(slot.getItem()).isEqualTo(item);
    }

    @Test
    void shouldSetFields() {
        Slot slot = new Slot();
        slot.setName("size");
        slot.setValue("42");
        slot.setValue_datatype("xsd:integer");
        slot.setType("legacy-type");
        assertThat(slot.getName()).isEqualTo("size");
        assertThat(slot.getValue()).isEqualTo("42");
        assertThat(slot.getValue_datatype()).isEqualTo("xsd:integer");
        assertThat(slot.getType()).isEqualTo("legacy-type");
    }

    @Test
    void noArgConstructorShouldInitialize() {
        Slot slot = new Slot();
        assertThat(slot.getId()).isNull();
        assertThat(slot.getName()).isNull();
        assertThat(slot.getValue()).isNull();
        assertThat(slot.getValue_datatype()).isNull();
        assertThat(slot.getItem()).isNull();
    }
}
