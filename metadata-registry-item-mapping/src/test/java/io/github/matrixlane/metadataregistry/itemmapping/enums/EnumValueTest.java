package io.github.matrixlane.metadataregistry.itemmapping.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumValueTest {

    @Test
    void interopMappingStatus_shouldHaveExpectedValues() {
        InteropMappingStatus[] values = InteropMappingStatus.values();
        assertThat(values).containsExactly(
                InteropMappingStatus.AUTO_MATCHED,
                InteropMappingStatus.PENDING,
                InteropMappingStatus.CONFIRMED,
                InteropMappingStatus.REJECTED,
                InteropMappingStatus.UNMATCHED
        );
    }

    @Test
    void interopMappingStatus_valueOf_shouldWork() {
        assertThat(InteropMappingStatus.valueOf("PENDING"))
                .isEqualTo(InteropMappingStatus.PENDING);
        assertThat(InteropMappingStatus.valueOf("AUTO_MATCHED"))
                .isEqualTo(InteropMappingStatus.AUTO_MATCHED);
        assertThat(InteropMappingStatus.valueOf("CONFIRMED"))
                .isEqualTo(InteropMappingStatus.CONFIRMED);
        assertThat(InteropMappingStatus.valueOf("REJECTED"))
                .isEqualTo(InteropMappingStatus.REJECTED);
    }

    @Test
    void mappingDegree_shouldHaveExpectedValues() {
        MappingDegree[] values = MappingDegree.values();
        assertThat(values).containsExactly(
                MappingDegree.RELATED_TO,
                MappingDegree.SAME_AS,
                MappingDegree.SEMANTICALLY_EQUIVALENT,
                MappingDegree.SEMANTICALLY_SIMILAR,
                MappingDegree.DERIVED_FROM,
                MappingDegree.HAS_DERIVATION,
                MappingDegree.BROADER_THAN,
                MappingDegree.NARROWER_THAN,
                MappingDegree.CHILD_OF,
                MappingDegree.PARENT_OF,
                MappingDegree.CONTAINS,
                MappingDegree.CONTAINED_IN,
                MappingDegree.INSTANCE_OF,
                MappingDegree.HAS_INSTANCE
        );
    }

    @Test
    void mappingDegree_valueOf_shouldWork() {
        assertThat(MappingDegree.valueOf("SAME_AS"))
                .isEqualTo(MappingDegree.SAME_AS);
        assertThat(MappingDegree.valueOf("DERIVED_FROM"))
                .isEqualTo(MappingDegree.DERIVED_FROM);
        assertThat(MappingDegree.valueOf("CONTAINS"))
                .isEqualTo(MappingDegree.CONTAINS);
        assertThat(MappingDegree.valueOf("HAS_INSTANCE"))
                .isEqualTo(MappingDegree.HAS_INSTANCE);
    }

    @Test
    void mappingDegree_standardValue_shouldMatchIso11179Table35() {
        assertThat(MappingDegree.RELATED_TO.standardValue()).isEqualTo("related to");
        assertThat(MappingDegree.SAME_AS.standardValue()).isEqualTo("same as");
        assertThat(MappingDegree.SEMANTICALLY_EQUIVALENT.standardValue())
                .isEqualTo("semantically equivalent");
        assertThat(MappingDegree.SEMANTICALLY_SIMILAR.standardValue())
                .isEqualTo("semantically similar");
        assertThat(MappingDegree.DERIVED_FROM.standardValue()).isEqualTo("derived from");
        assertThat(MappingDegree.HAS_DERIVATION.standardValue()).isEqualTo("has derivation");
        assertThat(MappingDegree.BROADER_THAN.standardValue()).isEqualTo("broader than");
        assertThat(MappingDegree.NARROWER_THAN.standardValue()).isEqualTo("narrower than");
        assertThat(MappingDegree.CHILD_OF.standardValue()).isEqualTo("child of");
        assertThat(MappingDegree.PARENT_OF.standardValue()).isEqualTo("parent of");
        assertThat(MappingDegree.CONTAINS.standardValue()).isEqualTo("contains");
        assertThat(MappingDegree.CONTAINED_IN.standardValue()).isEqualTo("contained in");
        assertThat(MappingDegree.INSTANCE_OF.standardValue()).isEqualTo("instance of");
        assertThat(MappingDegree.HAS_INSTANCE.standardValue()).isEqualTo("has instance");
        assertThat(MappingDegree.values())
                .allSatisfy(d -> assertThat(d.standardValue()).doesNotContain("_"));
    }

    @Test
    void mappingDegree_fromStandardValue_shouldResolveLowercaseAndLegacyNames() {
        assertThat(MappingDegree.fromStandardValue("related to")).isEqualTo(MappingDegree.RELATED_TO);
        assertThat(MappingDegree.fromStandardValue("semantically equivalent"))
                .isEqualTo(MappingDegree.SEMANTICALLY_EQUIVALENT);
        assertThat(MappingDegree.fromStandardValue("SAME_AS")).isEqualTo(MappingDegree.SAME_AS);
        assertThat(MappingDegree.fromStandardValue("Derived From"))
                .isEqualTo(MappingDegree.DERIVED_FROM);
        assertThat(MappingDegree.fromStandardValue(null)).isNull();
        assertThat(MappingDegree.fromStandardValue("  ")).isNull();
        assertThatThrownBy(() -> MappingDegree.fromStandardValue("not a degree"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
