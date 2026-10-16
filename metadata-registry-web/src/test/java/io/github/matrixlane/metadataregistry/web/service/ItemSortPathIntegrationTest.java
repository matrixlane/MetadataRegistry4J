package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.web.repository.DataElementRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归:验证 Spring Data JPA 3.3 对 Item 继承链的嵌套集合路径排序
 * (identifiedItem.scopedIdentifiers.identifier / designations.sign.value)
 * 是否可直接用于 repository.findAll(spec, pageable)。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:item-sort-probe-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main",
        "app.seed.demo-data.enabled=false"
})
@ActiveProfiles("test")
class ItemSortPathIntegrationTest {

    @Autowired
    private DataElementRepository dataElementRepository;

    private DataElement newDataElement(String identifier, String name) {
        DataElement de = new DataElement();
        Designation designation = new Designation();
        designation.setSign(Sign.of(name));
        designation.setItem(de);
        de.getDesignations().add(designation);
        AdministeredItem ii = new AdministeredItem(de);
        ii.setCreationDateTime(LocalDateTime.now());
        de.setIdentifiedItem(ii);
        ScopedIdentifier si = new ScopedIdentifier(identifier, null, ii);
        si.setVersion("1.0.0");
        ii.getScopedIdentifiers().add(si);
        return de;
    }

    @Test
    @Transactional
    void sortByNestedCollectionPaths() {
        // 故意以乱序落库
        dataElementRepository.save(newDataElement("PB.003", "gamma"));
        dataElementRepository.save(newDataElement("PB.001", "alpha"));
        dataElementRepository.save(newDataElement("PB.002", "beta"));

        Page<DataElement> byIdentifier = dataElementRepository.findAll(Specification.where(null),
                PageRequest.of(0, 10, Sort.by("identifiedItem.scopedIdentifiers.identifier")));
        assertThat(byIdentifier.getContent())
                .flatExtracting(e -> e.getIdentifiedItem().getScopedIdentifiers())
                .extracting(ScopedIdentifier::getIdentifier)
                .containsExactly("PB.001", "PB.002", "PB.003");

        Page<DataElement> byNameDesc = dataElementRepository.findAll(Specification.where(null),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "designations.sign.value")));
        assertThat(byNameDesc.getContent())
                .flatExtracting(DataElement::getDesignations)
                .extracting(d -> d.getSign().getValue())
                .containsExactly("gamma", "beta", "alpha");
    }
}
