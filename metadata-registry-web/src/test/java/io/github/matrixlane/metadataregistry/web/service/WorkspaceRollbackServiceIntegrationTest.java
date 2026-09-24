package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineContextEntry;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementResponse;
import io.github.matrixlane.metadataregistry.web.dto.version.SnapshotKind;
import io.github.matrixlane.metadataregistry.web.entity.VersionHistory;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.VersionHistoryRepository;
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
 * 工作空间回退服务集成测试（TDD）。
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:workspace-rollback-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main"
})
@Transactional
@ActiveProfiles("test")
class WorkspaceRollbackServiceIntegrationTest {

    @Autowired
    private DataElementService dataElementService;

    @Autowired
    private ContextService contextService;

    @Autowired
    private WorkspaceRollbackService workspaceRollbackService;

    @Autowired
    private VersionHistoryRepository versionHistoryRepository;

    @Autowired
    private AdministeredItemRepository administeredItemRepository;

    @Autowired
    private RegistrationAuthorityRepository registrationAuthorityRepository;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private LanguageIdentificationRepository languageRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RegistrarRepository registrarRepository;

    @Autowired
    private ContactAccountBindingService contactAccountBindingService;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID languageId;
    private UUID contextId;
    private Namespace namespace;
    private Account account;

    @BeforeEach
    void setUp() {
        LanguageIdentification lang = new LanguageIdentification();
        lang.setLanguageIdentifier("zh-CN");
        lang.setScriptIdentifier("Hans");
        lang.setGeopoliticalTerritoryIdentifier("CN");
        languageId = languageRepository.save(lang).getId();

        ContextRequest contextRequest = new ContextRequest(
                List.of(new TextLine(languageId, "测试上下文", null, null, null, true)),
                List.of(),
                null);
        contextId = contextService.create(contextRequest).id();

        RegistrationAuthority ra = new RegistrationAuthority();
        ra.getName().add(Sign.of("测试注册机构"));
        RegistrationAuthorityIdentifier rai = new RegistrationAuthorityIdentifier();
        rai.setInternationalCodeDesignator("0000");
        rai.setOrganizationIdentifier("RA-ROLLBACK");
        ra.setRegistrationAuthorityIdentifier(rai);
        ra = registrationAuthorityRepository.save(ra);

        namespace = new Namespace();
        namespace.setRegistrationAuthority(ra);
        namespace.setNamingAuthority(ra);
        namespace = namespaceRepository.save(namespace);

        account = new Account();
        account.setUsername("rollback-test");
        account.setPasswordHash("x");
        account.setSysRole(SystemRole.SUPER_ADMIN);
        account = accountRepository.save(account);

        Registrar registrar = new Registrar(ra);
        registrar.setIdentifier("registrar-rollback");
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
    void rollbackDraft_shouldRestoreBaselineAndClearStatus() throws Exception {
        DataElementResponse created = createDataElement("原始名称");
        UUID itemId = created.id();
        setRegistrationState(itemId, RegistrationStatus.RECORDED, null);

        // 编辑进入草稿：应先捕获 BASELINE，再写入 DRAFT_EDIT
        DataElementResponse updated = dataElementService.update(itemId,
                requestWithDesignation("编辑后名称"));

        List<VersionHistory> versions = versionHistoryRepository.findByItemIdOrderByVersionNumberDesc(itemId);
        assertThat(versions).hasSize(3);
        assertThat(readKind(versions.get(0))).isEqualTo(SnapshotKind.DRAFT_EDIT);
        assertThat(readKind(versions.get(1))).isEqualTo(SnapshotKind.BASELINE);
        assertThat(readKind(versions.get(2))).isEqualTo(SnapshotKind.SUBMITTED);
        assertThat(updated.meta().administrativeStatus()).isEqualTo("draft");

        workspaceRollbackService.rollback(itemId);

        DataElementResponse rolledBack = dataElementService.get(itemId);
        assertThat(rolledBack.label()).isEqualTo("原始名称");
        assertThat(rolledBack.meta().administrativeStatus()).isNull();
        assertThat(rolledBack.meta().registrationStatus()).isEqualTo(RegistrationStatus.RECORDED);

        List<VersionHistory> afterRollback = versionHistoryRepository.findByItemIdOrderByVersionNumberDesc(itemId);
        assertThat(afterRollback).hasSize(4);
        assertThat(readKind(afterRollback.get(0))).isEqualTo(SnapshotKind.ROLLED_BACK);
    }

    @Test
    void rollbackRejected_withoutBaseline_shouldClearStatusOnly() {
        DataElementResponse created = createDataElement("拒绝前名称");
        UUID itemId = created.id();
        setRegistrationState(itemId, RegistrationStatus.RECORDED, AdministrativeStatus.REJECTED);

        workspaceRollbackService.rollback(itemId);

        AdministeredItem ai = administeredItemRepository.findByItem_Id(itemId).orElseThrow();
        assertThat(ai.getRegistrationState().getAdministrativeStatus()).isNull();
        assertThat(ai.getRegistrationState().getRegistrationStatus()).isEqualTo(RegistrationStatus.RECORDED);

        DataElementResponse after = dataElementService.get(itemId);
        assertThat(after.label()).isEqualTo("拒绝前名称");
    }

    @Test
    void rollbackDraft_withoutBaseline_shouldThrowNoVersionToRollback() {
        DataElementResponse created = createDataElement("无基线名称");
        UUID itemId = created.id();
        setRegistrationState(itemId, RegistrationStatus.RECORDED, AdministrativeStatus.DRAFT);

        assertThatThrownBy(() -> workspaceRollbackService.rollback(itemId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.NO_VERSION_TO_ROLLBACK);
                });
    }

    @Test
    void rollback_shouldRejectInvalidRegistrationState() {
        DataElementResponse created = createDataElement("Incomplete名称");
        UUID itemId = created.id();
        // 创建后默认 INCOMPLETE + DRAFT，不允许回退
        assertThatThrownBy(() -> workspaceRollbackService.rollback(itemId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getCode()).isEqualTo(ApiCodes.INVALID_ROLLBACK_STATE);
                });
    }

    private DataElementResponse createDataElement(String name) {
        return dataElementService.create(requestWithDesignation(name));
    }

    private DataElementRequest requestWithDesignation(String name) {
        return new DataElementRequest(
                null,
                null,
                null,
                List.of(new TextLine(languageId, name, null,
                        List.of(new TextLineContextEntry(contextId, Acceptability.PREFERRED)),
                        null, null)),
                null,
                new ItemRegistrationRequest(namespace.getId(), "DE-ROLLBACK-" + UUID.randomUUID(), "1.0.0",
                        List.of(), List.of())
        );
    }

    private void setRegistrationState(UUID itemId, RegistrationStatus status, AdministrativeStatus adminStatus) {
        AdministeredItem ai = administeredItemRepository.findByItem_Id(itemId).orElseThrow();
        RegistrationState state = ai.getRegistrationState();
        if (state == null) {
            state = new RegistrationState();
            ai.setRegistrationState(state);
        }
        state.setRegistrationStatus(status);
        state.setAdministrativeStatus(adminStatus);
        administeredItemRepository.save(ai);
    }

    private SnapshotKind readKind(VersionHistory vh) throws Exception {
        JsonNode root = objectMapper.readTree(vh.getSnapshotJson());
        JsonNode meta = root.get("meta");
        if (meta == null) {
            return null;
        }
        return SnapshotKind.valueOf(meta.get("kind").asText());
    }
}
