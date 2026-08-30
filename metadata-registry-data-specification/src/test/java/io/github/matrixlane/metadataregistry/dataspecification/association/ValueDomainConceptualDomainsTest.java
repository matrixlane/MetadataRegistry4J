package io.github.matrixlane.metadataregistry.dataspecification.association;

import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.LocalEnumeratedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ReferenceEnumeratedValueDomain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ValueDomainConceptualDomains} 单元测试。
 * <p>
 * 覆盖 ISO/IEC 11179-31:2023 §7.4.4.1 value_domain_meaning 的统一读写，
 * 以及 §7.4.5.2.1 值域/概念域子类型一致性约束。
 */
class ValueDomainConceptualDomainsTest {

    @Test
    void describedPairShouldSetGetAndClear() {
        DescribedValueDomain vd = new DescribedValueDomain();
        DescribedConceptualDomain cd = new DescribedConceptualDomain();

        ValueDomainConceptualDomains.set(vd, cd);
        assertThat(ValueDomainConceptualDomains.get(vd)).isSameAs(cd);
        assertThat(vd.getDescribedConceptualDomain()).isSameAs(cd);

        ValueDomainConceptualDomains.clear(vd);
        assertThat(ValueDomainConceptualDomains.get(vd)).isNull();
        assertThat(vd.getDescribedConceptualDomain()).isNull();
    }

    @Test
    void localEnumeratedPairShouldRoundTrip() {
        LocalEnumeratedValueDomain vd = new LocalEnumeratedValueDomain();
        LocalEnumeratedConceptualDomain cd = new LocalEnumeratedConceptualDomain();

        ValueDomainConceptualDomains.set(vd, cd);
        assertThat(ValueDomainConceptualDomains.get(vd)).isSameAs(cd);
        assertThat(vd.getLocalEnumeratedConceptualDomain()).isSameAs(cd);

        ValueDomainConceptualDomains.clear(vd);
        assertThat(vd.getLocalEnumeratedConceptualDomain()).isNull();
    }

    @Test
    void referenceEnumeratedPairShouldRoundTrip() {
        ReferenceEnumeratedValueDomain vd = new ReferenceEnumeratedValueDomain();
        ReferenceEnumeratedConceptualDomain cd = new ReferenceEnumeratedConceptualDomain();

        ValueDomainConceptualDomains.set(vd, cd);
        assertThat(ValueDomainConceptualDomains.get(vd)).isSameAs(cd);
        assertThat(vd.getReferencedConceptualDomain()).isSameAs(cd);

        ValueDomainConceptualDomains.clear(vd);
        assertThat(vd.getReferencedConceptualDomain()).isNull();
    }

    @Test
    void mismatchedSubtypesShouldBeRejectedPerConstraint1() {
        // §7.4.5.2.1：描述型值域只能关联描述型概念域，以此类推。
        assertThatThrownBy(() -> ValueDomainConceptualDomains.set(
                new DescribedValueDomain(), new LocalEnumeratedConceptualDomain()))
                .isInstanceOf(MetadataAssociationValidationException.class);
        assertThatThrownBy(() -> ValueDomainConceptualDomains.set(
                new LocalEnumeratedValueDomain(), new DescribedConceptualDomain()))
                .isInstanceOf(MetadataAssociationValidationException.class);
        assertThatThrownBy(() -> ValueDomainConceptualDomains.set(
                new ReferenceEnumeratedValueDomain(), new LocalEnumeratedConceptualDomain()))
                .isInstanceOf(MetadataAssociationValidationException.class);
        assertThatThrownBy(() -> ValueDomainConceptualDomains.set(
                new LocalEnumeratedValueDomain(), new ReferenceEnumeratedConceptualDomain()))
                .isInstanceOf(MetadataAssociationValidationException.class);
    }

    @Test
    void associationServiceShouldApplyValueDomainMeaning() {
        MetadataObjectAssociationService service = MetadataObjectAssociationService.withDefaults();
        LocalEnumeratedValueDomain vd = new LocalEnumeratedValueDomain();
        LocalEnumeratedConceptualDomain cd = new LocalEnumeratedConceptualDomain();

        service.associate(AssociationType.VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN, vd, cd);
        assertThat(vd.getLocalEnumeratedConceptualDomain()).isSameAs(cd);

        service.dissociate(AssociationType.VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN, vd, cd);
        assertThat(vd.getLocalEnumeratedConceptualDomain()).isNull();
    }

    @Test
    void dissociateWithoutCurrentLinkShouldFail() {
        MetadataObjectAssociationService service = MetadataObjectAssociationService.withDefaults();
        DescribedValueDomain vd = new DescribedValueDomain();
        DescribedConceptualDomain cd = new DescribedConceptualDomain();

        assertThatThrownBy(() -> service.dissociate(
                AssociationType.VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN, vd, cd))
                .isInstanceOf(MetadataAssociationValidationException.class);
    }
}
