package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.dto.registrar.RegistrarResponse;
import io.github.matrixlane.metadataregistry.web.repository.IndividualRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 注册人员列表关键词检索集成测试（remove-registrar-contact-identifier）。
 *
 * <p>原谓词检索已被删除的非标准列 identifier，改为按 ISO/IEC 11179-3:2023 §6.3.5
 * Contact 的三要素检索：个人姓名、角色名、组织名。组织名是 {@code @ElementCollection}，
 * 若用 join 会按名称条数放大结果，故断言多名称组织下仍只返回一条。
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:registrar-list-search-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main"
})
@Transactional
@ActiveProfiles("test")
class RegistrarListSearchIntegrationTest {

    @Autowired
    private RegistrarService registrarService;
    @Autowired
    private RegistrarRepository registrarRepository;
    @Autowired
    private RegistrationAuthorityRepository registrationAuthorityRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private IndividualRepository individualRepository;
    @Autowired
    private RoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        // 一个带两条名称（zh + en）的组织，暴露 element collection join 的行放大
        Organization organization = new Organization();
        organization.getName().add(Sign.of("测试注册机构"));
        organization.getName().add(Sign.of("Test Registry"));
        organization = organizationRepository.save(organization);

        RegistrationAuthority ra = new RegistrationAuthority();
        ra.getName().add(Sign.of("测试注册机构"));
        RegistrationAuthorityIdentifier rai = new RegistrationAuthorityIdentifier();
        rai.setInternationalCodeDesignator("0000");
        rai.setOrganizationIdentifier("RA-SEARCH");
        ra.setRegistrationAuthorityIdentifier(rai);
        ra = registrationAuthorityRepository.save(ra);

        Individual individual = new Individual();
        individual.setName(Sign.of("李雷"));
        individual = individualRepository.save(individual);

        Role role = new Role();
        role.setRoleName("数据治理主管");
        role = roleRepository.save(role);

        Registrar registrar = new Registrar(ra);
        registrar.setOrganization(organization);
        registrar.setIndividual(individual);
        registrar.setRole(role);
        registrarRepository.save(registrar);

        Registrar other = new Registrar(ra);
        other.setOrganization(organization);
        Individual another = new Individual();
        another.setName(Sign.of("韩梅梅"));
        other.setIndividual(individualRepository.save(another));
        registrarRepository.save(other);
    }

    @Test
    void list_matchesIndividualName() {
        Page<RegistrarResponse> page = registrarService.list("李雷", PageRequest.of(0, 20));
        assertThat(page.getContent()).extracting(RegistrarResponse::individualName).containsExactly("李雷");
    }

    @Test
    void list_matchesRoleName() {
        Page<RegistrarResponse> page = registrarService.list("数据治理", PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).roleName()).isEqualTo("数据治理主管");
    }

    @Test
    void list_matchesOrganizationNameWithoutDuplicatingRows() {
        Page<RegistrarResponse> page = registrarService.list("Test Registry", PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void list_unmatchedKeyword_returnsEmpty() {
        assertThat(registrarService.list("不存在的关键词", PageRequest.of(0, 20)).getContent()).isEmpty();
    }

    @Test
    void list_blankKeyword_returnsAll() {
        assertThat(registrarService.list("  ", PageRequest.of(0, 20)).getContent()).hasSize(2);
    }
}
