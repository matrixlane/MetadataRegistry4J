package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.web.repository.DataElementRepository;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回归:HTTP 层 {@code sort=meta.identifier,asc} / {@code sort=meta.label,desc}
 * 经 ItemSortPageableResolver 改写后由 JPA 执行,不再抛 PropertyReferenceException
 * (对应 GlobalExceptionHandler 的「不支持的排序或查询字段」)。
 *
 * <p>匿名访问:安全过滤器关闭,ForcePasswordResetInterceptor 对匿名请求放行。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:item-sort-mvc-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main",
        "app.seed.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Transactional
class ItemSortMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataElementRepository dataElementRepository;

    /** InitializationWizardInterceptor 依赖:模拟系统已初始化,避免 499 拦截 */
    @MockBean
    private SystemSettingService systemSettingService;

    private DataElement newDataElement(String identifier, String name) {
        DataElement de = new DataElement();
        Designation designation = new Designation();
        designation.setSign(Sign.of(name));
        designation.setItem(de);
        de.getDesignations().add(designation);
        IdentifiedItem ii = new IdentifiedItem(de);
        de.setIdentifiedItem(ii);
        ScopedIdentifier si = new ScopedIdentifier(identifier, null, ii);
        si.setVersion("1.0.0");
        ii.getScopedIdentifiers().add(si);
        return de;
    }

    @Test
    void list_sortByMetaIdentifier_isRewrittenAndExecutes() throws Exception {
        when(systemSettingService.isInitialized()).thenReturn(true);
        dataElementRepository.save(newDataElement("PB.003", "gamma"));
        dataElementRepository.save(newDataElement("PB.001", "alpha"));
        dataElementRepository.save(newDataElement("PB.002", "beta"));

        mockMvc.perform(get("/api/v1/data-elements")
                        .param("sort", "meta.identifier,asc")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].meta.identifier").value("PB.001"))
                .andExpect(jsonPath("$.data.content[2].meta.identifier").value("PB.003"));
    }

    @Test
    void list_sortByMetaLabel_isRewrittenAndExecutes() throws Exception {
        when(systemSettingService.isInitialized()).thenReturn(true);
        dataElementRepository.save(newDataElement("PB.003", "gamma"));
        dataElementRepository.save(newDataElement("PB.001", "alpha"));
        dataElementRepository.save(newDataElement("PB.002", "beta"));

        mockMvc.perform(get("/api/v1/data-elements")
                        .param("sort", "meta.label,desc")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].meta.identifier").value("PB.003"))
                .andExpect(jsonPath("$.data.content[2].meta.identifier").value("PB.001"));
    }
}
