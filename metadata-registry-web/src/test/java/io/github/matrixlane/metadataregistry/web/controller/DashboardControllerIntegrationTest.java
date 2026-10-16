package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.dataspecification.DataElementConcept;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.ObjectClass;
import io.github.matrixlane.metadataregistry.dataspecification.Property;
import io.github.matrixlane.metadataregistry.web.repository.DataElementRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ObjectClassRepository;
import io.github.matrixlane.metadataregistry.web.repository.PropertyRepository;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 概览页统计（{@code GET /api/v1/dashboard}）集成测试：
 * 统计卡片计数、注册状态分布、近 7 天创建窗口、近期变更排序与首选指称解析。
 *
 * <p>回归背景：该接口曾有响应但概览页从未消费（前端硬编码演示数字）；
 * 本测试锁定接口契约，防止接线后再次漂移为假数据。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.config.import=",
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml",
        "spring.datasource.url=jdbc:sqlite:file:dashboard-mvc-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main",
        "app.seed.demo-data.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataElementRepository dataElementRepository;

    @Autowired
    private ObjectClassRepository objectClassRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private io.github.matrixlane.metadataregistry.web.repository.ValueDomainRepository valueDomainRepository;

    @Autowired
    private io.github.matrixlane.metadataregistry.web.repository.ConceptualDomainRepository conceptualDomainRepository;

    @Autowired
    private io.github.matrixlane.metadataregistry.web.repository.DataElementConceptRepository dataElementConceptRepository;

    @Autowired
    private io.github.matrixlane.metadataregistry.web.repository.DataTypeRepository dataTypeRepository;

    @Autowired
    private LanguageIdentificationRepository languageRepository;

    /** InitializationWizardInterceptor 依赖：模拟系统已初始化，避免 499 拦截。 */
    @MockBean
    private SystemSettingService systemSettingService;

    private LanguageIdentification zh;

    private DataElement newItemWithAdministered(String identifier, String name,
                                                RegistrationStatus status,
                                                LocalDateTime created, LocalDateTime changed) {
        DataElement de = new DataElement();
        Designation designation = new Designation();
        designation.setSign(Sign.of(name));
        designation.setItem(de);
        designation.setLanguage(zh);
        de.getDesignations().add(designation);
        AdministeredItem ai = new AdministeredItem(de);
        ai.setCreationDateTime(created);
        ai.setLastChangeDateTime(changed);
        if (status != null) {
            RegistrationState state = new RegistrationState();
            state.setRegistrationStatus(status);
            state.setEffectiveDate(created);
            ai.setRegistrationState(state);
        }
        de.setIdentifiedItem(ai);
        ScopedIdentifier si = new ScopedIdentifier(identifier, null, ai);
        si.setVersion("1.0.0");
        ai.getScopedIdentifiers().add(si);
        return de;
    }

    private ObjectClass newObjectClass(String name) {
        ObjectClass oc = new ObjectClass();
        Designation designation = new Designation();
        designation.setSign(Sign.of(name));
        designation.setItem(oc);
        designation.setLanguage(zh);
        oc.getDesignations().add(designation);
        AdministeredItem ai = new AdministeredItem(oc);
        ai.setCreationDateTime(LocalDateTime.now());
        oc.setIdentifiedItem(ai);
        return oc;
    }

    private Property newProperty(String name) {
        Property p = new Property();
        Designation designation = new Designation();
        designation.setSign(Sign.of(name));
        designation.setItem(p);
        designation.setLanguage(zh);
        p.getDesignations().add(designation);
        AdministeredItem ai = new AdministeredItem(p);
        ai.setCreationDateTime(LocalDateTime.now());
        p.setIdentifiedItem(ai);
        return p;
    }

    private DataElementConcept newDataElementConcept(String name) {
        DataElementConcept dec = new DataElementConcept();
        Designation designation = new Designation();
        designation.setSign(Sign.of(name));
        designation.setItem(dec);
        designation.setLanguage(zh);
        dec.getDesignations().add(designation);
        AdministeredItem ai = new AdministeredItem(dec);
        ai.setCreationDateTime(LocalDateTime.now());
        dec.setIdentifiedItem(ai);
        return dec;
    }

    private DescribedValueDomain newValueDomain(String name, DataType dataType) {
        DescribedValueDomain vd = new DescribedValueDomain();
        vd.setDataType(dataType);
        Designation designation = new Designation();
        designation.setSign(Sign.of(name));
        designation.setItem(vd);
        designation.setLanguage(zh);
        vd.getDesignations().add(designation);
        AdministeredItem ai = new AdministeredItem(vd);
        ai.setCreationDateTime(LocalDateTime.now());
        vd.setIdentifiedItem(ai);
        return vd;
    }

    private DescribedConceptualDomain newConceptualDomain(String name) {
        DescribedConceptualDomain cd = new DescribedConceptualDomain();
        Designation designation = new Designation();
        designation.setSign(Sign.of(name));
        designation.setItem(cd);
        designation.setLanguage(zh);
        cd.getDesignations().add(designation);
        AdministeredItem ai = new AdministeredItem(cd);
        ai.setCreationDateTime(LocalDateTime.now());
        cd.setIdentifiedItem(ai);
        return cd;
    }

    @Test
    void getStats_returnsRealCountsDistributionAndRecentChanges() throws Exception {
        when(systemSettingService.isInitialized()).thenReturn(true);
        zh = new LanguageIdentification();
        zh.setLanguageIdentifier("zh-CN");
        zh = languageRepository.save(zh);

        LocalDateTime now = LocalDateTime.now();
        dataElementRepository.save(newItemWithAdministered("DE.001", "血压", RegistrationStatus.STANDARD,
                now.minusDays(1), now.minusHours(2)));
        dataElementRepository.save(newItemWithAdministered("DE.002", "心率", RegistrationStatus.INCOMPLETE,
                now.minusDays(30), now.minusHours(1)));

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataElements").value(2))
                .andExpect(jsonPath("$.data.objectClasses").value(0))
                .andExpect(jsonPath("$.data.properties").value(0))
                .andExpect(jsonPath("$.data.administeredItems").value(2))
                .andExpect(jsonPath("$.data.administeredItemsCreatedThisWeek").value(1))
                .andExpect(jsonPath("$.data.dataElementsCreatedThisWeek").value(1))
                .andExpect(jsonPath("$.data.registrationStatusDistribution.length()").value(2))
                .andExpect(jsonPath("$.data.registrationStatusDistribution[*].status",
                        hasItem("STANDARD")))
                .andExpect(jsonPath("$.data.registrationStatusDistribution[*].status",
                        hasItem("INCOMPLETE")))
                // 近期变更按 lastChangeDateTime 倒序：心率(-1h) 先于血压(-2h)
                .andExpect(jsonPath("$.data.recentChanges[0].name").value("心率"))
                .andExpect(jsonPath("$.data.recentChanges[1].name").value("血压"));
    }

    @Test
    void getStats_countsFiveCardTypesByType() throws Exception {
        when(systemSettingService.isInitialized()).thenReturn(true);
        zh = new LanguageIdentification();
        zh.setLanguageIdentifier("zh-CN");
        zh = languageRepository.save(zh);

        LocalDateTime now = LocalDateTime.now();
        dataElementRepository.save(newItemWithAdministered("DE.010", "体温", null,
                now.minusDays(1), now.minusHours(3)));
        objectClassRepository.save(newObjectClass("患者"));
        propertyRepository.save(newProperty("年龄"));
        DataType dataType = dataTypeRepository.save(new DataType());
        valueDomainRepository.save(newValueDomain("体温值域", dataType));
        conceptualDomainRepository.save(newConceptualDomain("体温概念域"));
        dataElementConceptRepository.save(newDataElementConcept("体温概念"));

        mockMvc.perform(get("/api/v1/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataElements").value(1))
                .andExpect(jsonPath("$.data.valueDomains").value(1))
                .andExpect(jsonPath("$.data.conceptualDomains").value(1))
                .andExpect(jsonPath("$.data.dataElementConcepts").value(1))
                // 受管项总数涵盖全部条目类型（含卡片外的对象类/特性）
                .andExpect(jsonPath("$.data.administeredItems").value(6))
                .andExpect(jsonPath("$.data.dataElementsCreatedThisWeek").value(1))
                .andExpect(jsonPath("$.data.valueDomainsCreatedThisWeek").value(1))
                .andExpect(jsonPath("$.data.conceptualDomainsCreatedThisWeek").value(1))
                .andExpect(jsonPath("$.data.dataElementConceptsCreatedThisWeek").value(1))
                // 唯一条目无注册状态：分布只有 status 为 null 的一组
                .andExpect(jsonPath("$.data.registrationStatusDistribution.length()").value(1))
                .andExpect(jsonPath("$.data.registrationStatusDistribution[0].status").doesNotExist());
    }
}
