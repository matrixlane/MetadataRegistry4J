package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementResponse;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.seed.SystemDefaultNamespaceInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 自有数据标准的命名空间前置条件（identification 能力）集成测试：
 * 系统中不存在与注册机构关联的命名空间时，创建自有数据标准被阻止；
 * 补建后放行；外部命名空间（导入外部数据标准）不受限。
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class NamespaceGateIntegrationTest {

    @Autowired
    private DataElementService dataElementService;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private RegistrationAuthorityRepository registrationAuthorityRepository;

    @Autowired
    private LanguageIdentificationRepository languageRepository;

    private UUID languageId;

    @BeforeEach
    void setUp() {
        LanguageIdentification lang = new LanguageIdentification();
        lang.setLanguageIdentifier("zh-CN");
        lang.setScriptIdentifier("Hans");
        lang.setGeopoliticalTerritoryIdentifier("CN");
        lang.setEnabled(true);
        languageId = languageRepository.save(lang).getId();
    }

    @Test
    void createDataElement_withoutRaNamespace_blocked() {
        // 初始化时跳过命名空间设置后的状态：系统中没有注册机构关联命名空间。
        // 不指定命名空间的自有数据标准创建 SHALL 被阻止。
        assertThat(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).isFalse();

        assertThatThrownBy(() -> dataElementService.create(requestFor(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("命名空间");
    }

    @Test
    void createDataElement_systemDefaultNamespace_blocked() {
        // system-default 命名空间不计入前置条件。
        Namespace systemDefault = new Namespace();
        systemDefault.setShorthandPrefix(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_PREFIX);
        systemDefault.setSchemeReference(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_SCHEME);
        namespaceRepository.save(systemDefault);

        assertThatThrownBy(() -> dataElementService.create(requestFor(systemDefault.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("命名空间");
    }

    @Test
    void createDataElement_afterCreatingRaNamespace_allowed() {
        // 补建基于注册机构的命名空间后，创建自有数据标准放行。
        Namespace raNamespace = createRaNamespace();

        DataElementResponse response = dataElementService.create(requestFor(raNamespace.getId()));

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
    }

    @Test
    void createDataElement_externalNamespace_notBlocked() {
        // 外部命名空间（无关联注册机构）用于导入/编目外部数据标准，不受门槛限制。
        Namespace external = new Namespace();
        external.setSchemeReference("urn:example:external-standard");
        namespaceRepository.save(external);
        assertThat(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).isFalse();

        DataElementResponse response = dataElementService.create(requestFor(external.getId()));

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
    }

    private Namespace createRaNamespace() {
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.getName().add(Sign.of("测试注册机构"));
        ra = registrationAuthorityRepository.save(ra);
        Namespace ns = new Namespace();
        ns.setRegistrationAuthority(ra);
        ns.setNamingAuthority(ra);
        return namespaceRepository.save(ns);
    }

    private DataElementRequest requestFor(UUID namespaceId) {
        return new DataElementRequest(
                null,
                null,
                List.of(new TextLine(languageId, "患者姓名", null, null, null, true)),
                null,
                new ItemRegistrationRequest(namespaceId, "DE-GATE-001", "1.0.0", List.of(), List.of())
        );
    }
}
