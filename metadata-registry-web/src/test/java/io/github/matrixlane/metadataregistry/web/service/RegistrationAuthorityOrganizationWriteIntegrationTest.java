package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfig;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityRequest;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityResponse;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierStrategyConfigRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注册机构即组织（JOINED 继承）：创建一条注册机构记录 MUST 同时产生一条组织记录，
 * 并携带组织（ISO/IEC 11179-3:2023 §6.3.3 表 2）的联系属性。
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:ra-org-attrs-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main",
        "spring.jpa.mapping-resources=orm/orm-sqlite.xml"
})
@Transactional
@ActiveProfiles("test")
class RegistrationAuthorityOrganizationWriteIntegrationTest {

    @Autowired
    private RegistrationAuthorityService registrationAuthorityService;

    @Autowired
    private SystemSettingService systemSettingService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private RegistrationAuthorityRepository registrationAuthorityRepository;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private io.github.matrixlane.metadataregistry.web.repository.NamespaceIdentifierSettingsRepository namespaceIdentifierSettingsRepository;

    @Autowired
    private IdentifierStrategyConfigRepository identifierStrategyConfigRepository;

    @Autowired
    private RegistrarRepository registrarRepository;

    @Test
    void create_persistsOrganizationRowWithContactAttributes() {
        systemSettingService.setEdition(DeploymentEdition.TEAM);

        RegistrationAuthorityRequest request = new RegistrationAuthorityRequest(
                "组织属性测试机构",
                "Org Attr RA EN",
                "北京市海淀区测试路 1 号",
                List.of("a@example.com", "b@example.com"),
                List.of("010-12345678", "13800000000"),
                "https://ra.example.com",
                "0000",
                "RA-ORG-ATTR",
                null,
                null,
                null,
                List.of(),
                null, null, null, null, null, null, null);

        RegistrationAuthorityResponse response =
                registrationAuthorityService.create(request, null, SystemRole.SUPER_ADMIN);

        // 1. 组织行（JOINED 父表）存在且属性落库
        Organization org = organizationRepository.findById(response.id()).orElseThrow();
        assertThat(org.getMailAddress().getValue()).isEqualTo("北京市海淀区测试路 1 号");
        assertThat(org.getEmailAddress()).containsExactly("a@example.com", "b@example.com");
        assertThat(org.getPhoneNumber().stream().map(p -> p.getValue()).toList())
                .containsExactly("010-12345678", "13800000000");
        assertThat(org.getWebAddress()).isEqualTo("https://ra.example.com");
        assertThat(org.getName().stream().map(n -> n.getValue()).toList())
                .containsExactly("组织属性测试机构", "Org Attr RA EN");

        // 2. 注册机构子表行同事务产生（JOINED：父 organization 行 + 子 registration_authority 行）
        assertThat(registrationAuthorityRepository.existsById(response.id())).isTrue();

        // 3. 回显一致（响应再读取）
        RegistrationAuthorityResponse fetched = registrationAuthorityService.get(response.id());
        assertThat(fetched.mailAddress()).isEqualTo("北京市海淀区测试路 1 号");
        assertThat(fetched.emailAddress()).containsExactly("a@example.com", "b@example.com");
        assertThat(fetched.phoneNumber()).containsExactly("010-12345678", "13800000000");
        assertThat(fetched.webAddress()).isEqualTo("https://ra.example.com");
    }

    @Test
    void create_teamEditionWithOwnNamespace_provisionsNamespaceAndStrategyConfig() {
        // ra-creation-wizard：TEAM 创建时 createNamespace=true 同事务开通自有命名空间
        systemSettingService.setEdition(DeploymentEdition.TEAM);

        RegistrationAuthorityRequest request = new RegistrationAuthorityRequest(
                "命名空间测试机构",
                null,
                null,
                List.of(),
                List.of(),
                null,
                "0000",
                "RA-NS-TEST",
                null,
                null,
                null,
                List.of(),
                null,        // selfAsRegistrar
                true,        // createNamespace
                "测试命名空间",
                null,        // identifierInputMode（缺省 AUTO_WITH_OVERRIDE）
                null,        // versionInputMode（缺省 AUTO_WITH_OVERRIDE）
                null,        // versionPolicy（缺省 SEMVER）
                null);       // identifierTemplate（缺省系统默认模板）

        RegistrationAuthorityResponse response =
                registrationAuthorityService.create(request, null, SystemRole.SUPER_ADMIN);

        // 1. ra.namespace 回挂非空，命名空间行存在
        io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority ra =
                registrationAuthorityRepository.findById(response.id()).orElseThrow();
        assertThat(ra.getNamespace()).isNotNull();
        Namespace ns = ra.getNamespace();
        assertThat(namespaceRepository.existsById(ns.getId())).isTrue();
        assertThat(ns.getDesignations()).isNotEmpty();
        assertThat(ns.getDesignations().get(0).getSign().getValue()).isEqualTo("测试命名空间");

        // 2. registrationAuthority / namingAuthority 双挂均指向该 RA
        assertThat(ns.getRegistrationAuthority()).isNotNull();
        assertThat(ns.getRegistrationAuthority().getId()).isEqualTo(ra.getId());
        assertThat(ns.getNamingAuthority()).isNotNull();
        assertThat(ns.getNamingAuthority().getId()).isEqualTo(ra.getId());

        // 3. 标识设置卫星：输入模式缺省 AUTO_WITH_OVERRIDE、版本策略缺省 SEMVER
        var nsSettings = namespaceIdentifierSettingsRepository.findByNamespaceId(ns.getId()).orElseThrow();
        assertThat(nsSettings.getIdentifierInputMode()).isEqualTo("AUTO_WITH_OVERRIDE");
        assertThat(nsSettings.getVersionInputMode()).isEqualTo("AUTO_WITH_OVERRIDE");
        assertThat(nsSettings.getVersioningPolicy()).isEqualTo("SEMVER");

        // 4. 命名空间级标识策略配置已建（SEGMENT_TEMPLATE、PUBLISHED）
        List<IdentifierStrategyConfig> nsConfigs = identifierStrategyConfigRepository.findAll().stream()
                .filter(c -> c.getNamespace() != null && ns.getId().equals(c.getNamespace().getId()))
                .toList();
        assertThat(nsConfigs).hasSize(1);
        assertThat(nsConfigs.get(0).getStrategyType()).isEqualTo("SEGMENT_TEMPLATE");
        assertThat(nsConfigs.get(0).getStatus().name()).isEqualTo("PUBLISHED");
    }

    @Test
    void create_teamEditionSelfAsRegistrarOff_doesNotCreateRegistrar() {
        // ra-creation-wizard：TEAM 团队版 selfAsRegistrar=false 时不建立注册人员
        systemSettingService.setEdition(DeploymentEdition.TEAM);

        RegistrationAuthorityRequest request = new RegistrationAuthorityRequest(
                "注册人员开关测试机构",
                null,
                null,
                List.of(),
                List.of(),
                null,
                "0000",
                "RA-REG-OFF",
                null,
                null,
                null,
                List.of(),
                false,       // selfAsRegistrar
                null,        // createNamespace
                null, null, null, null, null);

        RegistrationAuthorityResponse response =
                registrationAuthorityService.create(request, null, SystemRole.SUPER_ADMIN);

        assertThat(response).isNotNull();
        assertThat(registrarRepository.findAll()).isEmpty();
    }
}
