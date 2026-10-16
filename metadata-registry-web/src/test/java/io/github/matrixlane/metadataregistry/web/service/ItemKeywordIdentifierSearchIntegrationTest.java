package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ValueDomain;
import io.github.matrixlane.metadataregistry.web.repository.DataElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.DataTypeRepository;
import io.github.matrixlane.metadataregistry.web.repository.ValueDomainRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 列表关键词检索回归：关键词须同时命中名称（指称）与标识符（Scoped_Identifier）。
 *
 * <p>改动前共享的 {@code byKeyword} 只匹配 Designation，指称中不含标识符的条目
 * （WS 363/364 导入的标准条目普遍如此）按标识符搜不到。本测试以真实 JPA 查询覆盖
 * ValueDomainService.list 与 DataElementService.list：两者都走共享实现。</p>
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:item-keyword-identifier-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main"
})
@Transactional
@ActiveProfiles("test")
class ItemKeywordIdentifierSearchIntegrationTest {

    @Autowired
    private ValueDomainService valueDomainService;
    @Autowired
    private DataElementService dataElementService;
    @Autowired
    private ValueDomainRepository valueDomainRepository;
    @Autowired
    private DataElementRepository dataElementRepository;
    @Autowired
    private DataTypeRepository dataTypeRepository;

    private DataType stringType;

    @BeforeEach
    void setUp() {
        stringType = new DataType();
        designate(stringType, "文本型");
        stringType = dataTypeRepository.save(stringType);

        // 名称与标识符刻意不同：只有标识符含 "CV04.30"，按名称搜不到第一条
        valueDomainRepository.save(valueDomain(stringType, "CV04.30.001", "Ordinal score"));
        valueDomainRepository.save(valueDomain(stringType, "CV05.10.007", "Ordinal category"));

        // 第一条挂两条指称 + 两个标识符，用于暴露 exists 与 join 的基数差异
        dataElementRepository.save(dataElement(
                List.of("Patient name", "患者姓名"), List.of("DE-0001", "DE-0001-ALT")));
        dataElementRepository.save(dataElement(List.of("Patient gender"), List.of("DE-0002")));
    }

    @Test
    void valueDomain_list_matchesScopedIdentifierFragment() {
        var page = valueDomainService.list("CV04.30", null, PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).label()).isEqualTo("Ordinal score");
        assertThat(page.getContent().get(0).meta().identifier()).isEqualTo("CV04.30.001");
    }

    @Test
    void valueDomain_list_identifierMatchIsCaseInsensitive() {
        var page = valueDomainService.list("cv04.30", null, PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).meta().identifier()).isEqualTo("CV04.30.001");
    }

    @Test
    void valueDomain_list_stillMatchesDesignation() {
        var page = valueDomainService.list("Ordinal score", null, PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).meta().identifier()).isEqualTo("CV04.30.001");
    }

    @Test
    void valueDomain_list_blankKeyword_returnsAll() {
        assertThat(valueDomainService.list("  ", null, PageRequest.of(0, 20)).getContent()).hasSize(2);
    }

    @Test
    void valueDomain_list_unmatchedKeyword_returnsEmpty() {
        assertThat(valueDomainService.list("CV99.99", null, PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    void dataElement_list_matchesScopedIdentifierFragment() {
        var page = dataElementService.list("DE-0002", PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).label()).isEqualTo("Patient gender");
    }

    @Test
    void dataElement_list_nameAndIdentifierBothMatchWithoutDuplicatingRows() {
        // 第一条同时有两条指称与两个标识符，四种组合都命中它，仍只能返回一行
        var page = dataElementService.list("Patient name", PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);

        var byIdentifier = dataElementService.list("DE-0001", PageRequest.of(0, 20));
        assertThat(byIdentifier.getContent()).hasSize(1);
        assertThat(byIdentifier.getContent().get(0).label()).isEqualTo("Patient name");
    }

    @Test
    void dataElement_list_matchesChineseDesignation() {
        assertThat(dataElementService.list("患者姓名", PageRequest.of(0, 20)).getContent()).hasSize(1);
    }

    @Test
    void dataElement_list_blankKeyword_returnsAll() {
        assertThat(dataElementService.list(null, PageRequest.of(0, 20)).getContent()).hasSize(2);
    }

    private ValueDomain valueDomain(DataType dataType, String identifier, String name) {
        DescribedValueDomain vd = new DescribedValueDomain();
        vd.setDataType(dataType);
        designate(vd, name);
        identify(vd, List.of(identifier));
        return vd;
    }

    private DataElement dataElement(List<String> names, List<String> identifiers) {
        DataElement de = new DataElement();
        names.forEach(name -> designate(de, name));
        identify(de, identifiers);
        return de;
    }

    private void designate(Item item, String name) {
        Designation designation = new Designation();
        designation.setSign(Sign.of(name));
        designation.setItem(item);
        item.getDesignations().add(designation);
    }

    /** 建受管链：Item → AdministeredItem → ScopedIdentifier（ISO/IEC 11179-3 §7.4.1 / §9.4.1.2）。 */
    private void identify(Item item, List<String> identifiers) {
        AdministeredItem administered = new AdministeredItem(item);
        administered.setCreationDateTime(LocalDateTime.now());
        for (String identifier : identifiers) {
            ScopedIdentifier si = new ScopedIdentifier(identifier, null, administered);
            si.setVersion("1.0.0");
            administered.getScopedIdentifiers().add(si);
        }
        item.setIdentifiedItem(administered);
    }
}
