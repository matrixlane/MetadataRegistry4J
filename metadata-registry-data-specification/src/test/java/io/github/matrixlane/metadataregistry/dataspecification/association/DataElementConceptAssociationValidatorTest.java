package io.github.matrixlane.metadataregistry.dataspecification.association;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.dataspecification.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataElementConceptAssociationValidatorTest {

    private DataElementConceptAssociationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DataElementConceptAssociationValidator();
    }

    @Test
    void supports_onlyDataElementConceptTypes() {
        assertThat(validator.supports(AssociationType.DATA_ELEMENT_CONCEPT_TO_OBJECT_CLASS)).isTrue();
        assertThat(validator.supports(AssociationType.DATA_ELEMENT_CONCEPT_TO_PROPERTY)).isTrue();
        assertThat(validator.supports(AssociationType.DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN)).isTrue();
        assertThat(validator.supports(AssociationType.DATA_ELEMENT_TO_VALUE_DOMAIN)).isFalse();
    }

    @Test
    void validate_objectClassAssociationWithCorrectTypes_passes() {
        DataElementConcept dec = new DataElementConcept();
        ObjectClass oc = new ObjectClass();

        assertThatNoException().isThrownBy(() ->
                validator.validate(AssociationType.DATA_ELEMENT_CONCEPT_TO_OBJECT_CLASS, dec, oc));
    }

    @Test
    void validate_propertyAssociationWithCorrectTypes_passes() {
        DataElementConcept dec = new DataElementConcept();
        Property p = new Property();

        assertThatNoException().isThrownBy(() ->
                validator.validate(AssociationType.DATA_ELEMENT_CONCEPT_TO_PROPERTY, dec, p));
    }

    @Test
    void validate_conceptualDomainAssociationWithCorrectTypes_passes() {
        DataElementConcept dec = new DataElementConcept();
        ConceptualDomain cd = new ConceptualDomain();

        assertThatNoException().isThrownBy(() ->
                validator.validate(AssociationType.DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN, dec, cd));
    }

    @Test
    void validate_objectClassAssociationWithWrongTargetType_throws() {
        DataElementConcept dec = new DataElementConcept();
        Item wrongTarget = new Property();

        assertThatThrownBy(() ->
                validator.validate(AssociationType.DATA_ELEMENT_CONCEPT_TO_OBJECT_CLASS, dec, wrongTarget))
                .isInstanceOf(MetadataAssociationValidationException.class)
                .hasMessageContaining("ObjectClass");
    }

    @Test
    void validate_conceptualDomainAssociationWithWrongSourceType_throws() {
        DataElement de = new DataElement();
        ConceptualDomain cd = new ConceptualDomain();

        assertThatThrownBy(() ->
                validator.validate(AssociationType.DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN, de, cd))
                .isInstanceOf(MetadataAssociationValidationException.class)
                .hasMessageContaining("DataElementConcept");
    }

    @Test
    void validateCompleteness_acceptsNullObjectClassAndProperty() {
        DataElementConcept dec = new DataElementConcept();

        assertThatNoException().isThrownBy(() -> validator.validateCompleteness(dec));
    }

    @Test
    void validateCompleteness_acceptsBothPresent() {
        DataElementConcept dec = new DataElementConcept();
        dec.setObjectClass(new ObjectClass());
        dec.setProperty(new Property());

        assertThatNoException().isThrownBy(() -> validator.validateCompleteness(dec));
    }
}
