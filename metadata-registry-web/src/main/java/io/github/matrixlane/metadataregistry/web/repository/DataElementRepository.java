package io.github.matrixlane.metadataregistry.web.repository;

import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface DataElementRepository
        extends JpaRepository<DataElement, UUID>, JpaSpecificationExecutor<DataElement> {

    /** data_element_meaning（ISO/IEC 11179-31:2023 §7.6.3.2）反向：关联到指定数据元概念的全部数据元。 */
    List<DataElement> findByDataElementConcept_Id(UUID dataElementConceptId);

    /** data_element_domain（ISO/IEC 11179-31:2023 §7.6.3.1）反向：关联到指定值域的全部数据元。 */
    List<DataElement> findByValueDomain_Id(UUID valueDomainId);
}
