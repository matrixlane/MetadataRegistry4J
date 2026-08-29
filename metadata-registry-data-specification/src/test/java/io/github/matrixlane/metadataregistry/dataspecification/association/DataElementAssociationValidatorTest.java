package io.github.matrixlane.metadataregistry.dataspecification.association;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataElementAssociationValidatorTest {

    private DataElementAssociationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DataElementAssociationValidator();
    }

    @Test
    void supports_onlyDataElementTypes() {
        assertThat(validator.supports(AssociationType.DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT)).isTrue();
        assertThat(validator.supports(AssociationType.DATA_ELEMENT_TO_VALUE_DOMAIN)).isTrue();
        assertThat(validator.supports(AssociationType.DATA_ELEMENT_CONCEPT_TO_OBJECT_CLASS)).isFalse();
        assertThat(validator.supports(AssociationType.VALUE_DOMAIN_TO_DATA_TYPE)).isFalse();
    }

    @Test
    void validate_conceptAssociationWithCorrectTypes_passes() {
        DataElement de = new DataElement();
        DataElementConcept dec = new DataElementConcept();

        assertThatNoException().isThrownBy(() ->
                validator.validate(AssociationType.DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT, de, dec));
    }

    @Test
    void validate_valueDomainAssociationWithCorrectTypes_passes() {
        DataElement de = new DataElement();
        ValueDomain vd = new ValueDomain();

        assertThatNoException().isThrownBy(() ->
                validator.validate(AssociationType.DATA_ELEMENT_TO_VALUE_DOMAIN, de, vd));
    }

    @Test
    void validate_conceptAssociationWithWrongTargetType_throws() {
        DataElement de = new DataElement();
        Item wrongTarget = new ConceptualDomain();

        assertThatThrownBy(() ->
                validator.validate(AssociationType.DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT, de, wrongTarget))
                .isInstanceOf(MetadataAssociationValidationException.class)
                .hasMessageContaining("DataElementConcept");
    }

    @Test
    void validate_valueDomainAssociationWithWrongSourceType_throws() {
        DataElementConcept dec = new DataElementConcept();
        ValueDomain vd = new ValueDomain();

        assertThatThrownBy(() ->
                validator.validate(AssociationType.DATA_ELEMENT_TO_VALUE_DOMAIN, dec, vd))
                .isInstanceOf(MetadataAssociationValidationException.class)
                .hasMessageContaining("DataElement");
    }

    @Test
    void validateCompleteness_acceptsNullConceptAndValueDomain() {
        DataElement de = new DataElement();

        assertThatNoException().isThrownBy(() -> validator.validateCompleteness(de));
    }

    @Test
    void validateCompleteness_acceptsBothPresent() {
        DataElement de = new DataElement();
        de.setDataElementConcept(new DataElementConcept());
        de.setValueDomain(new ValueDomain());

        assertThatNoException().isThrownBy(() -> validator.validateCompleteness(de));
    }
}
