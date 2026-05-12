package io.github.matrixlane.metadataregistry.commonfacilities.basicandcore;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Slot;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.BooleanType;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.IValueType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StandardReferenceTest {

    @Test
    void itemShouldHaveStandardReference() {
        StandardReference ann = Item.class.getAnnotation(StandardReference.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).isEqualTo("ISO/IEC 11179-3:2023, 6.4.1");
    }

    @Test
    void slotShouldHaveStandardReference() {
        StandardReference ann = Slot.class.getAnnotation(StandardReference.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).isEqualTo("ISO/IEC 11179-3:2023, 6.4.6");
    }

    @Test
    void booleanTypeShouldHaveStandardReference() {
        StandardReference ann = BooleanType.class.getAnnotation(StandardReference.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).isEqualTo("ISO/IEC 11179-3:2023, 6.2.1");
    }

    @Test
    void iValueTypeShouldHaveStandardReference() {
        StandardReference ann = IValueType.class.getAnnotation(StandardReference.class);
        assertThat(ann).isNotNull();
        assertThat(ann.value()).isEqualTo("ISO/IEC 11179-3:2023, 6.2");
    }
}
