package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementResponse;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.seed.SystemDefaultNamespaceInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
@SpringBootTest(properties = {
        // 取消用户主目录 application-local.yml 导入并显式锁定内存 SQLite,
        // 使本测试与宿主机数据源配置无关(导入的配置优先级高于非 profile 默认值)。
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:namespace-gate-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main"
})
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

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private RegistrarRepository registrarRepository;
    @Autowired
    private ContactAccountBindingService contactAccountBindingService;

    private UUID languageId;
    private RegistrationAuthority registrationAuthority;
    private Account account;

    @BeforeEach
    void setUp() {
        LanguageIdentification lang = new LanguageIdentification();
        lang.setLanguageIdentifier("zh-CN");
        lang.setScriptIdentifier("Hans");
        lang.setGeopoliticalTerritoryIdentifier("CN");
        languageId = languageRepository.save(lang).getId();

        RegistrationAuthority ra = new RegistrationAuthority();
        ra.getName().add(Sign.of("测试注册机构"));
        RegistrationAuthorityIdentifier rai = new RegistrationAuthorityIdentifier();
        rai.setInternationalCodeDesignator("0000");
        rai.setOrganizationIdentifier("RA-TEST");
        ra.setRegistrationAuthorityIdentifier(rai);
        registrationAuthority = registrationAuthorityRepository.save(ra);

        account = new Account();
        account.setUsername("namespace-gate-test");
        account.setPasswordHash("x");
        account.setSysRole(SystemRole.SUPER_ADMIN);
        account = accountRepository.save(account);

        Registrar registrar = new Registrar(registrationAuthority);
        registrar.setIdentifier("registrar-test");
        registrar = registrarRepository.save(registrar);
        contactAccountBindingService.bind(registrar.getId(), account.getId());

        AuthenticatedUser principal = new AuthenticatedUser(
                account.getId(), account.getUsername(), SystemRole.SUPER_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
        systemDefault.setSchemeReference(Sign.of(SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_SCHEME));
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
        external.setSchemeReference(Sign.of("urn:example:external-standard"));
        namespaceRepository.save(external);
        assertThat(namespaceRepository.existsByRegistrationAuthorityIsNotNull()).isFalse();

        DataElementResponse response = dataElementService.create(requestFor(external.getId()));

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotNull();
    }

    private Namespace createRaNamespace() {
        Namespace ns = new Namespace();
        ns.setRegistrationAuthority(registrationAuthority);
        ns.setNamingAuthority(registrationAuthority);
        return namespaceRepository.save(ns);
    }

    private DataElementRequest requestFor(UUID namespaceId) {
        return new DataElementRequest(
                null,
                null,
                null,
                List.of(new TextLine(languageId, "患者姓名", null, null, null, true)),
                null,
                new ItemRegistrationRequest(namespaceId, "DE-GATE-001", "1.0.0", List.of(), List.of())
        );
    }
}
