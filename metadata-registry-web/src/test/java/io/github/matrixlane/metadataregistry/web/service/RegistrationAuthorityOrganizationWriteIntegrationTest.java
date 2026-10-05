package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityRequest;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityResponse;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
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
                List.of());

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
}
