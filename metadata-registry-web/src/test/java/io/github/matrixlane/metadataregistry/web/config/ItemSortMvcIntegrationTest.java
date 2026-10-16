package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.web.repository.DataElementRepository;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import java.time.LocalDateTime;
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
 * 由服务侧 {@code ItemListSorts} 改写为相关标量子查询排序后由 JPA 执行，
 * 不再抛 PropertyReferenceException（对应 GlobalExceptionHandler 的
 * 「不支持的排序或查询字段」），也不再触发 SQL Server 错误 145
 * （SELECT DISTINCT 的 ORDER BY 项不在选择列表中——旧 join + distinct
 * 实现在带检索词排序时的必现 500）。
 *
 * <p>匿名访问:安全过滤器关闭,ForcePasswordResetInterceptor 对匿名请求放行。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.config.import=",
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml",
        "spring.datasource.url=jdbc:sqlite:file:item-sort-mvc-it?mode=memory&cache=shared",
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
class ItemSortMvcIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataElementRepository dataElementRepository;

    /** InitializationWizardInterceptor 依赖:模拟系统已初始化,避免 499 拦截 */
    @MockBean
    private SystemSettingService systemSettingService;

    private DataElement newDataElement(String identifier, String... names) {
        DataElement de = new DataElement();
        for (String name : names) {
            Designation designation = new Designation();
            designation.setSign(Sign.of(name));
            designation.setItem(de);
            de.getDesignations().add(designation);
        }
        AdministeredItem ii = new AdministeredItem(de);
        ii.setCreationDateTime(LocalDateTime.now());
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

    /**
     * 回归:带检索词(q=PB)再按名称排序——旧实现因 byKeyword 施加 distinct +
     * join 集合排序,SQL Server 报「ORDER BY 项不在 DISTINCT 选择列表中」(500)。
     */
    @Test
    void list_sortByMetaLabel_withKeyword_noLongerFails() throws Exception {
        when(systemSettingService.isInitialized()).thenReturn(true);
        dataElementRepository.save(newDataElement("PB.003", "gamma"));
        dataElementRepository.save(newDataElement("PB.001", "alpha"));
        dataElementRepository.save(newDataElement("PB.002", "beta"));

        mockMvc.perform(get("/api/v1/data-elements")
                        .param("sort", "meta.label,asc")
                        .param("q", "PB")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].meta.identifier").value("PB.001"))
                .andExpect(jsonPath("$.data.content[1].meta.identifier").value("PB.002"))
                .andExpect(jsonPath("$.data.content[2].meta.identifier").value("PB.003"));
    }

    /**
     * 回归:一个条目挂多个指称时,按名称排序不得放大结果基数（旧 join 实现
     * 每多一条指称多出一行,分页内容重复、计数失真；标量子查询每行一个排序键）。
     */
    @Test
    void list_sortByMetaLabel_multiDesignation_noDuplicateRows() throws Exception {
        when(systemSettingService.isInitialized()).thenReturn(true);
        dataElementRepository.save(newDataElement("PB.003", "gamma"));
        dataElementRepository.save(newDataElement("PB.001", "alpha", "zeta"));
        dataElementRepository.save(newDataElement("PB.002", "beta"));

        mockMvc.perform(get("/api/v1/data-elements")
                        .param("sort", "meta.label,asc")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].meta.identifier").value("PB.001"))
                .andExpect(jsonPath("$.data.content[1].meta.identifier").value("PB.002"))
                .andExpect(jsonPath("$.data.content[2].meta.identifier").value("PB.003"));
    }
}
