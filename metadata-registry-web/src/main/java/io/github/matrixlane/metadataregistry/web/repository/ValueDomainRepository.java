package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ValueDomainRepository
        extends JpaRepository<ValueDomain, UUID>, JpaSpecificationExecutor<ValueDomain> {

    /** value_domain_meaning（ISO/IEC 11179-31:2023 §7.4.4.1）反向：描述型值域 → 描述型概念域。 */
    @Query("select v from DescribedValueDomain v where v.describedConceptualDomain.id = :conceptualDomainId")
    List<ValueDomain> findDescribedByConceptualDomainId(@Param("conceptualDomainId") UUID conceptualDomainId);

    /** value_domain_meaning（§7.4.4.1）反向：本地枚举值域 → 本地枚举概念域。 */
    @Query("select v from LocalEnumeratedValueDomain v where v.localEnumeratedConceptualDomain.id = :conceptualDomainId")
    List<ValueDomain> findLocalEnumeratedByConceptualDomainId(@Param("conceptualDomainId") UUID conceptualDomainId);

    /** value_domain_meaning（§7.4.4.1）反向：引用枚举值域 → 引用枚举概念域。 */
    @Query("select v from ReferenceEnumeratedValueDomain v where v.referencedConceptualDomain.id = :conceptualDomainId")
    List<ValueDomain> findReferenceEnumeratedByConceptualDomainId(@Param("conceptualDomainId") UUID conceptualDomainId);
}
