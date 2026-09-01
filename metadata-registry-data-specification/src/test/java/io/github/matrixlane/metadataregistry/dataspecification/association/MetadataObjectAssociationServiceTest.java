package io.github.matrixlane.metadataregistry.dataspecification.association;

import io.github.matrixlane.metadataregistry.dataspecification.ConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.dataspecification.Property;
import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;

class MetadataObjectAssociationServiceTest {

    private MetadataObjectAssociationService service;

    @BeforeEach
    void setUp() {
        service = MetadataObjectAssociationService.withDefaults();
    }

    @Test
    void associate_dataElementToConcept_setsConcept() {
        DataElement de = new DataElement();
        DataElementConcept dec = new DataElementConcept();

        service.associate(AssociationType.DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT, de, dec);

        assertThat(de.getDataElementConcept()).isEqualTo(dec);
    }

    @Test
    void associate_dataElementToValueDomain_setsValueDomain() {
        DataElement de = new DataElement();
        ValueDomain vd = new DescribedValueDomain();

        service.associate(AssociationType.DATA_ELEMENT_TO_VALUE_DOMAIN, de, vd);

        assertThat(de.getValueDomain()).isEqualTo(vd);
    }

    @Test
    void associate_dataElementConceptToConceptualDomain_keepsBothSidesInSync() {
        DataElementConcept dec = new DataElementConcept();
        ConceptualDomain cd = new DescribedConceptualDomain();

        service.associate(AssociationType.DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN, dec, cd);

        assertThat(dec.getConceptualDomains()).contains(cd);
        assertThat(cd.getDataElementConcepts()).contains(dec);
    }

    @Test
    void associate_valueDomainToDataType_setsDataType() {
        ValueDomain vd = new DescribedValueDomain();
        DataType dt = new DataType();

        service.associate(AssociationType.VALUE_DOMAIN_TO_DATA_TYPE, vd, dt);

        assertThat(vd.getDataType()).isEqualTo(dt);
    }

    @Test
    void associate_valueDomainToUnitOfMeasure_setsUnitOfMeasure() {
        ValueDomain vd = new DescribedValueDomain();
        UnitOfMeasure uom = new UnitOfMeasure();

        service.associate(AssociationType.VALUE_DOMAIN_TO_UNIT_OF_MEASURE, vd, uom);

        assertThat(vd.getUnitOfMeasure()).isEqualTo(uom);
    }

    @Test
    void associate_unsupportedType_throws() {
        DataElement de = new DataElement();
        DataType dt = new DataType();

        assertThatThrownBy(() -> service.associate(AssociationType.VALUE_DOMAIN_TO_DATA_TYPE, de, dt))
                .isInstanceOf(MetadataAssociationValidationException.class)
                .hasMessageContaining("ValueDomain");
    }

    @Test
    void dissociate_dataElementConcept_removesConcept() {
        DataElement de = new DataElement();
        DataElementConcept dec = new DataElementConcept();
        de.setDataElementConcept(dec);

        service.dissociate(AssociationType.DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT, de, dec);

        assertThat(de.getDataElementConcept()).isNull();
    }

    @Test
    void dissociate_dataElementValueDomain_removesValueDomain() {
        DataElement de = new DataElement();
        ValueDomain vd = new DescribedValueDomain();
        de.setValueDomain(vd);

        service.dissociate(AssociationType.DATA_ELEMENT_TO_VALUE_DOMAIN, de, vd);

        assertThat(de.getValueDomain()).isNull();
    }

    @Test
    void dissociate_conceptualDomain_keepsBothSidesInSync() {
        DataElementConcept dec = new DataElementConcept();
        ConceptualDomain cd = new DescribedConceptualDomain();
        dec.getConceptualDomains().add(cd);
        cd.getDataElementConcepts().add(dec);

        service.dissociate(AssociationType.DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN, dec, cd);

        assertThat(dec.getConceptualDomains()).doesNotContain(cd);
        assertThat(cd.getDataElementConcepts()).doesNotContain(dec);
    }

    @Test
    void dissociate_withMismatchedTarget_throws() {
        DataElement de = new DataElement();
        de.setDataElementConcept(new DataElementConcept());
        DataElementConcept other = new DataElementConcept();

        assertThatThrownBy(() ->
                service.dissociate(AssociationType.DATA_ELEMENT_TO_DATA_ELEMENT_CONCEPT, de, other))
                .isInstanceOf(MetadataAssociationValidationException.class)
                .hasMessageContaining("未关联");
    }

    @Test
    void replace_dataElementValueDomain_replacesTarget() {
        DataElement de = new DataElement();
        ValueDomain oldVd = new DescribedValueDomain();
        ValueDomain newVd = new DescribedValueDomain();
        de.setValueDomain(oldVd);

        service.replace(AssociationType.DATA_ELEMENT_TO_VALUE_DOMAIN, de, oldVd, newVd);

        assertThat(de.getValueDomain()).isEqualTo(newVd);
    }

    @Test
    void replace_dataElementConceptConceptualDomain_replacesTarget() {
        DataElementConcept dec = new DataElementConcept();
        ConceptualDomain oldCd = new DescribedConceptualDomain();
        ConceptualDomain newCd = new DescribedConceptualDomain();
        dec.getConceptualDomains().add(oldCd);
        oldCd.getDataElementConcepts().add(dec);

        service.replace(AssociationType.DATA_ELEMENT_CONCEPT_TO_CONCEPTUAL_DOMAIN, dec, oldCd, newCd);

        assertThat(dec.getConceptualDomains()).contains(newCd).doesNotContain(oldCd);
        assertThat(newCd.getDataElementConcepts()).contains(dec);
        assertThat(oldCd.getDataElementConcepts()).doesNotContain(dec);
    }

    @Test
    void validateCompleteness_acceptsEmptyAssociations() {
        DataElement de = new DataElement();
        DataElementConcept dec = new DataElementConcept();

        assertThatNoException().isThrownBy(() -> {
            service.validateCompleteness(de);
            service.validateCompleteness(dec);
        });
    }
}
