package io.github.matrixlane.metadataregistry.itemmapping;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ItemMappingTest {

    @Test
    void gettersAndSetters_shouldStoreAllFields() {
        ItemMapping mapping = new ItemMapping();
        mapping.setDegree(MappingDegree.DERIVED_FROM);
        mapping.setDescription("Derived from source item for FHIR transformation");
        mapping.setTransformationRule("year := substring(subject, 1, 4)");
        mapping.setTransformationRuleNotation("EBNF");

        assertThat(mapping.getDegree()).isEqualTo(MappingDegree.DERIVED_FROM);
        assertThat(mapping.getDescription()).isEqualTo(
                "Derived from source item for FHIR transformation");
        assertThat(mapping.getTransformationRule()).isEqualTo("year := substring(subject, 1, 4)");
        assertThat(mapping.getTransformationRuleNotation()).isEqualTo("EBNF");
    }

    @Test
    void subjectsAndObjects_shouldSupportMultipleBindings() {
        ItemMapping mapping = new ItemMapping();
        mapping.setDegree(MappingDegree.SAME_AS);

        Item subjectItem1 = mock(Item.class);
        Item subjectItem2 = mock(Item.class);
        Item objectItem = mock(Item.class);

        mapping.getSubjects().add(new ItemMappingSubject(mapping, subjectItem1, "year"));
        mapping.getSubjects().add(new ItemMappingSubject(mapping, subjectItem2, "month"));
        mapping.getObjects().add(new ItemMappingObject(mapping, objectItem, "isodate"));

        assertThat(mapping.getSubjects()).hasSize(2);
        assertThat(mapping.getSubjects().get(0).getItem()).isEqualTo(subjectItem1);
        assertThat(mapping.getSubjects().get(0).getMappingVariableName()).isEqualTo("year");
        assertThat(mapping.getSubjects().get(1).getItem()).isEqualTo(subjectItem2);
        assertThat(mapping.getSubjects().get(1).getMappingVariableName()).isEqualTo("month");
        assertThat(mapping.getSubjects().get(0).getItemMapping()).isEqualTo(mapping);

        assertThat(mapping.getObjects()).hasSize(1);
        assertThat(mapping.getObjects().get(0).getItem()).isEqualTo(objectItem);
        assertThat(mapping.getObjects().get(0).getMappingVariableName()).isEqualTo("isodate");
        assertThat(mapping.getObjects().get(0).getItemMapping()).isEqualTo(mapping);
    }

    @Test
    void subjectsAndObjects_shouldBeInitializedEmpty() {
        ItemMapping mapping = new ItemMapping();

        assertThat(mapping.getSubjects()).isNotNull().isEmpty();
        assertThat(mapping.getObjects()).isNotNull().isEmpty();
    }

    @Test
    void equalsAndHashCode_shouldUseId() {
        ItemMapping mapping1 = new ItemMapping();
        ItemMapping mapping2 = new ItemMapping();

        // Different instances without IDs should not be equal
        assertThat(mapping1).isNotEqualTo(mapping2);
    }

    @Test
    void mappingDegreeEnum_shouldHaveExpectedValues() {
        MappingDegree[] degrees = MappingDegree.values();
        assertThat(degrees).containsExactly(
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
    void isSymmetric_shouldBeTrueOnlyForSameAsAndSemanticallyEquivalent() {
        assertThat(MappingDegree.SAME_AS.isSymmetric()).isTrue();
        assertThat(MappingDegree.SEMANTICALLY_EQUIVALENT.isSymmetric()).isTrue();

        assertThat(MappingDegree.RELATED_TO.isSymmetric()).isFalse();
        assertThat(MappingDegree.SEMANTICALLY_SIMILAR.isSymmetric()).isFalse();
        assertThat(MappingDegree.DERIVED_FROM.isSymmetric()).isFalse();
        assertThat(MappingDegree.HAS_DERIVATION.isSymmetric()).isFalse();
        assertThat(MappingDegree.BROADER_THAN.isSymmetric()).isFalse();
        assertThat(MappingDegree.NARROWER_THAN.isSymmetric()).isFalse();
        assertThat(MappingDegree.CHILD_OF.isSymmetric()).isFalse();
        assertThat(MappingDegree.PARENT_OF.isSymmetric()).isFalse();
        assertThat(MappingDegree.CONTAINS.isSymmetric()).isFalse();
        assertThat(MappingDegree.CONTAINED_IN.isSymmetric()).isFalse();
        assertThat(MappingDegree.INSTANCE_OF.isSymmetric()).isFalse();
        assertThat(MappingDegree.HAS_INSTANCE.isSymmetric()).isFalse();
    }
}
