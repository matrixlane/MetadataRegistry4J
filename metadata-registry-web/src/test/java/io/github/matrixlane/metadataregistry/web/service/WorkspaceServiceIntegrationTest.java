package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationTransitionApproval;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.StewardshipRecord;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionInfo;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineContextEntry;
import io.github.matrixlane.metadataregistry.web.dto.contact.MyContactRequest;
import io.github.matrixlane.metadataregistry.web.dto.context.ContextRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataelement.DataElementResponse;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceItemResponse;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceItemView;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSubmitRequest;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSubmitResult;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSummaryResponse;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionApprovalRepository;
import io.github.matrixlane.metadataregistry.web.repository.StewardshipRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 工作空间服务集成测试（TDD）。
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:sqlite:file:workspace-it?mode=memory&cache=shared",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.datasource.username=",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.properties.hibernate.default_schema=main"
})
@Transactional
@ActiveProfiles("test")
class WorkspaceServiceIntegrationTest {

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private DataElementService dataElementService;

    @Autowired
    private ContextService contextService;

    @Autowired
    private ContactService contactService;

    @Autowired
    private LanguageIdentificationRepository languageRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private NamespaceRepository namespaceRepository;

    @Autowired
    private RegistrationAuthorityRepository registrationAuthorityRepository;

    @Autowired
    private AdministeredItemRepository administeredItemRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private StewardshipRecordRepository stewardshipRecordRepository;

    @Autowired
    private RegistrationTransitionApprovalRepository approvalRepository;

    @Autowired
    private RegistrarRepository registrarRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ContactAccountBindingService contactAccountBindingService;

    private UUID languageId;
    private UUID contextId;
    private Namespace namespace;
    private RegistrationAuthority authority;
    private Account ownerAccount;
    private Contact ownerContact;
    private Account otherAccount;
    private Contact otherContact;
    private Account registrarAccount;
    private Registrar registrar;

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

        authority = new RegistrationAuthority();
        authority.getName().add(Sign.of("测试注册机构"));
        RegistrationAuthorityIdentifier rai = new RegistrationAuthorityIdentifier();
        rai.setInternationalCodeDesignator("0000");
        rai.setOrganizationIdentifier("RA-WORKSPACE");
        authority.setRegistrationAuthorityIdentifier(rai);
        authority = registrationAuthorityRepository.save(authority);

        namespace = new Namespace();
        namespace.setRegistrationAuthority(authority);
        namespace.setNamingAuthority(authority);
        namespace = namespaceRepository.save(namespace);

        Organization org = new Organization();
        org.getName().add(Sign.of("测试组织"));
        org = organizationRepository.save(org);

        ownerAccount = createAccount("owner");
        ownerContact = contactRepository.findById(contactService.createMyContact(ownerAccount.getId(),
                new MyContactRequest(org.getId(), null, null)).id()).orElseThrow();

        otherAccount = createAccount("other");
        otherContact = contactRepository.findById(contactService.createMyContact(otherAccount.getId(),
                new MyContactRequest(org.getId(), null, null)).id()).orElseThrow();

        registrarAccount = createAccount("registrar");
        registrar = new Registrar(authority);
        registrar.setOrganization(org);
        registrar = registrarRepository.save(registrar);
        contactAccountBindingService.bind(registrar.getId(), registrarAccount.getId());
        registrarAccount.setActiveRegistrarId(registrar.getId());
        registrarAccount = accountRepository.save(registrarAccount);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // 三视图可见性
    // =========================================================================

    @Test
    void draftsView_shouldContainOwnedDraftItemsOnly() {
        login(ownerAccount);
        DataElementResponse owned = createDataElement("本人草稿");
        login(otherAccount);
        DataElementResponse notOwned = createDataElement("他人草稿");

        login(ownerAccount);
        PageResponse<WorkspaceItemResponse> page = workspaceService.listItems(
                WorkspaceItemView.DRAFTS, PageRequest.of(0, 10), ownerAccount.getId());

        List<UUID> ids = page.getContent().stream().map(WorkspaceItemResponse::itemId).toList();
        assertThat(ids).contains(owned.id()).doesNotContain(notOwned.id());
    }

    @Test
    void rejectedView_shouldContainOwnedRejectedItemsOnly() {
        login(ownerAccount);
        DataElementResponse owned = createDataElement("本人拒绝项");
        setAdminStatus(owned.id(), AdministrativeStatus.REJECTED, RegistrationStatus.RECORDED);

        login(otherAccount);
        DataElementResponse notOwned = createDataElement("他人拒绝项");
        setAdminStatus(notOwned.id(), AdministrativeStatus.REJECTED, RegistrationStatus.RECORDED);

        login(ownerAccount);
        PageResponse<WorkspaceItemResponse> page = workspaceService.listItems(
                WorkspaceItemView.REJECTED, PageRequest.of(0, 10), ownerAccount.getId());

        List<UUID> ids = page.getContent().stream().map(WorkspaceItemResponse::itemId).toList();
        assertThat(ids).contains(owned.id()).doesNotContain(notOwned.id());
    }

    @Test
    void pendingView_shouldContainProvisionallyQualifiedAndPendingApprovalItemsForRegistrar() {
        login(ownerAccount);
        DataElementResponse qualified = createDataElement("申请合格项");
        setAdminStatus(qualified.id(), AdministrativeStatus.PROVISIONALLY_QUALIFIED, RegistrationStatus.RECORDED);
        AdministeredItem qualifiedAi = administeredItemRepository.findByItem_Id(qualified.id()).orElseThrow();
        qualifiedAi.setRegistrationAuthority(authority);
        administeredItemRepository.save(qualifiedAi);

        DataElementResponse pendingApproval = createDataElement("待批准迁移项");
        setAdminStatus(pendingApproval.id(), null, RegistrationStatus.RECORDED);
        AdministeredItem pendingAi = administeredItemRepository.findByItem_Id(pendingApproval.id()).orElseThrow();
        pendingAi.setRegistrationAuthority(authority);
        administeredItemRepository.save(pendingAi);
        createApproval(pendingAi, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED);

        login(registrarAccount);
        PageResponse<WorkspaceItemResponse> page = workspaceService.listItems(
                WorkspaceItemView.PENDING, PageRequest.of(0, 10), registrarAccount.getId());

        List<UUID> ids = page.getContent().stream().map(WorkspaceItemResponse::itemId).toList();
        assertThat(ids).contains(qualified.id(), pendingApproval.id());
    }

    @Test
    void pendingView_shouldBeEmptyForNonRegistrar() {
        login(ownerAccount);
        DataElementResponse qualified = createDataElement("申请合格项");
        setAdminStatus(qualified.id(), AdministrativeStatus.PROVISIONALLY_QUALIFIED, RegistrationStatus.RECORDED);
        AdministeredItem qualifiedAi = administeredItemRepository.findByItem_Id(qualified.id()).orElseThrow();
        qualifiedAi.setRegistrationAuthority(authority);
        administeredItemRepository.save(qualifiedAi);

        PageResponse<WorkspaceItemResponse> page = workspaceService.listItems(
                WorkspaceItemView.PENDING, PageRequest.of(0, 10), ownerAccount.getId());

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void summary_shouldReturnThreeViewCounts() {
        login(registrarAccount);
        DataElementResponse draft = createDataElement("草稿项");
        DataElementResponse rejected = createDataElement("拒绝项");
        setAdminStatus(rejected.id(), AdministrativeStatus.REJECTED, RegistrationStatus.RECORDED);

        DataElementResponse qualified = createDataElement("申请合格项");
        setAdminStatus(qualified.id(), AdministrativeStatus.PROVISIONALLY_QUALIFIED, RegistrationStatus.RECORDED);
        AdministeredItem qualifiedAi = administeredItemRepository.findByItem_Id(qualified.id()).orElseThrow();
        qualifiedAi.setRegistrationAuthority(authority);
        administeredItemRepository.save(qualifiedAi);

        WorkspaceSummaryResponse summary = workspaceService.getSummary(registrarAccount.getId());

        assertThat(summary.drafts()).isEqualTo(1L);
        assertThat(summary.rejected()).isEqualTo(1L);
        assertThat(summary.pending()).isEqualTo(1L);
    }

    // =========================================================================
    // 行级操作标志
    // =========================================================================

    @Test
    void flags_forDraftIncompleteWithStewardship_shouldShowCanSubmitAndRequestCandidate() {
        login(ownerAccount);
        DataElementResponse item = createDataElement("草稿候选项");
        setStewardship(item.id(), ownerContact);

        PageResponse<WorkspaceItemResponse> page = workspaceService.listItems(
                WorkspaceItemView.DRAFTS, PageRequest.of(0, 10), ownerAccount.getId());

        WorkspaceItemResponse row = page.getContent().get(0);
        assertThat(row.flags().canSubmit()).isTrue();
        assertThat(row.flags().canRequestCandidate()).isTrue();
    }

    @Test
    void flags_forDraftWithoutStewardship_shouldNotShowRequestCandidate() {
        login(ownerAccount);
        createDataElement("无主管草稿项");

        PageResponse<WorkspaceItemResponse> page = workspaceService.listItems(
                WorkspaceItemView.DRAFTS, PageRequest.of(0, 10), ownerAccount.getId());

        WorkspaceItemResponse row = page.getContent().get(0);
        assertThat(row.flags().canRequestCandidate()).isFalse();
    }

    @Test
    void flags_forPendingQualifiedAsRegistrar_shouldShowApproveAndReject() {
        login(ownerAccount);
        DataElementResponse item = createDataElement("待审核项");
        setAdminStatus(item.id(), AdministrativeStatus.PROVISIONALLY_QUALIFIED, RegistrationStatus.RECORDED);
        AdministeredItem ai = administeredItemRepository.findByItem_Id(item.id()).orElseThrow();
        ai.setRegistrationAuthority(authority);
        administeredItemRepository.save(ai);
        setStewardship(item.id(), ownerContact);

        login(registrarAccount);
        PageResponse<WorkspaceItemResponse> page = workspaceService.listItems(
                WorkspaceItemView.PENDING, PageRequest.of(0, 10), registrarAccount.getId());

        WorkspaceItemResponse row = page.getContent().get(0);
        assertThat(row.flags().canApprove()).isTrue();
        assertThat(row.flags().canReject()).isTrue();
    }

    @Test
    void flags_forRejected_shouldShowRollback() {
        login(ownerAccount);
        DataElementResponse item = createDataElement("可回退拒绝项");
        setAdminStatus(item.id(), AdministrativeStatus.REJECTED, RegistrationStatus.RECORDED);

        PageResponse<WorkspaceItemResponse> page = workspaceService.listItems(
                WorkspaceItemView.REJECTED, PageRequest.of(0, 10), ownerAccount.getId());

        WorkspaceItemResponse row = page.getContent().get(0);
        assertThat(row.flags().canRollback()).isTrue();
    }

    // =========================================================================
    // 批量提交
    // =========================================================================

    @Test
    void submitBatch_incompleteDraft_shouldLandInitial() {
        login(ownerAccount);
        DataElementResponse item = createDataElement("待提交草稿");

        List<WorkspaceSubmitResult> results = workspaceService.submit(
                new WorkspaceSubmitRequest(List.of(item.id()), null), ownerAccount.getId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).success()).isTrue();
        AdministeredItem ai = administeredItemRepository.findByItem_Id(item.id()).orElseThrow();
        assertThat(ai.getRegistrationState().getAdministrativeStatus()).isEqualTo(AdministrativeStatus.INITIAL);
    }

    @Test
    void submitBatch_recordedDraft_shouldClearStatus() {
        login(ownerAccount);
        DataElementResponse item = createDataElement("已记录草稿");
        setAdminStatus(item.id(), AdministrativeStatus.DRAFT, RegistrationStatus.RECORDED);

        List<WorkspaceSubmitResult> results = workspaceService.submit(
                new WorkspaceSubmitRequest(List.of(item.id()), null), ownerAccount.getId());

        assertThat(results.get(0).success()).isTrue();
        AdministeredItem ai = administeredItemRepository.findByItem_Id(item.id()).orElseThrow();
        assertThat(ai.getRegistrationState().getAdministrativeStatus()).isNull();
    }

    @Test
    void submitBatch_partialFailure_shouldLeaveSuccessesInEffect() {
        login(ownerAccount);
        DataElementResponse draft = createDataElement("可提交草稿");
        DataElementResponse notDraft = createDataElement("非草稿项");
        setAdminStatus(notDraft.id(), null, RegistrationStatus.RECORDED);

        List<WorkspaceSubmitResult> results = workspaceService.submit(
                new WorkspaceSubmitRequest(List.of(draft.id(), notDraft.id()), null), ownerAccount.getId());

        assertThat(results).hasSize(2);
        WorkspaceSubmitResult success = results.stream().filter(r -> r.itemId().equals(draft.id())).findFirst().orElseThrow();
        WorkspaceSubmitResult failure = results.stream().filter(r -> r.itemId().equals(notDraft.id())).findFirst().orElseThrow();
        assertThat(success.success()).isTrue();
        assertThat(failure.success()).isFalse();
        assertThat(failure.errorCode()).isEqualTo(ApiCodes.WORKSPACE_ITEM_NOT_DRAFT);

        AdministeredItem ai = administeredItemRepository.findByItem_Id(draft.id()).orElseThrow();
        assertThat(ai.getRegistrationState().getAdministrativeStatus()).isEqualTo(AdministrativeStatus.INITIAL);
    }

    @Test
    void submitBatch_withOtherContact_shouldRejectWith422() {
        login(ownerAccount);
        DataElementResponse item = createDataElement("待提交草稿");

        SubmissionInfo otherContactSubmission = new SubmissionInfo(otherContact.getId(), null);

        assertThatThrownBy(() -> workspaceService.submit(
                new WorkspaceSubmitRequest(List.of(item.id()), otherContactSubmission), ownerAccount.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED));
    }

    // =========================================================================
    // 请求成为候选
    // =========================================================================

    @Test
    void requestCandidate_withStewardshipAndOwnership_shouldBecomeCandidate() {
        login(ownerAccount);
        DataElementResponse item = createDataElement("请求候选项");
        setStewardship(item.id(), ownerContact);

        workspaceService.requestCandidate(item.id(), new SubmissionInfo(ownerContact.getId(), null), ownerAccount.getId());

        AdministeredItem ai = administeredItemRepository.findByItem_Id(item.id()).orElseThrow();
        assertThat(ai.getRegistrationState().getRegistrationStatus()).isEqualTo(RegistrationStatus.CANDIDATE);
        assertThat(ai.getRegistrationState().getAdministrativeStatus()).isNull();
    }

    @Test
    void requestCandidate_withoutStewardship_shouldReject() {
        login(ownerAccount);
        DataElementResponse item = createDataElement("无主管候选项");

        assertThatThrownBy(() -> workspaceService.requestCandidate(item.id(), null, ownerAccount.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ApiCodes.CANDIDATE_MINIMUM_REQUIREMENT_NOT_MET));
    }

    @Test
    void requestCandidate_notOwned_shouldReject() {
        login(ownerAccount);
        DataElementResponse item = createDataElement("他人候选项");
        setStewardship(item.id(), ownerContact);

        assertThatThrownBy(() -> workspaceService.requestCandidate(item.id(), null, otherAccount.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ApiCodes.SUBMITTER_OWNERSHIP_REQUIRED));
    }

    @Test
    void requestCandidate_notIncomplete_shouldReject() {
        login(ownerAccount);
        DataElementResponse item = createDataElement("非Incomplete候选项");
        setStewardship(item.id(), ownerContact);
        setAdminStatus(item.id(), AdministrativeStatus.DRAFT, RegistrationStatus.RECORDED);

        assertThatThrownBy(() -> workspaceService.requestCandidate(item.id(), null, ownerAccount.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ApiCodes.WORKSPACE_REQUEST_CANDIDATE_INVALID_STATE));
    }

    // =========================================================================
    // 回退
    // =========================================================================

    @Test
    void rollback_notOwned_shouldReject() {
        login(ownerAccount);
        DataElementResponse item = createDataElement("他人回退项");
        setAdminStatus(item.id(), AdministrativeStatus.REJECTED, RegistrationStatus.RECORDED);

        assertThatThrownBy(() -> workspaceService.rollback(item.id(), otherAccount.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ApiCodes.SUBMITTER_OWNERSHIP_REQUIRED));
    }

    @Test
    void rollback_rejectedWithoutBaseline_shouldClearStatus() {
        login(ownerAccount);
        DataElementResponse item = createDataElement("拒绝回退项");
        setAdminStatus(item.id(), AdministrativeStatus.REJECTED, RegistrationStatus.RECORDED);

        workspaceService.rollback(item.id(), ownerAccount.getId());

        AdministeredItem ai = administeredItemRepository.findByItem_Id(item.id()).orElseThrow();
        assertThat(ai.getRegistrationState().getAdministrativeStatus()).isNull();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Account createAccount(String username) {
        Account account = new Account();
        account.setUsername(username + "-" + UUID.randomUUID());
        account.setPasswordHash("x");
        account.setSysRole(SystemRole.SUPER_ADMIN);
        return accountRepository.save(account);
    }

    private void login(Account account) {
        AuthenticatedUser principal = new AuthenticatedUser(
                account.getId(), account.getUsername(), account.getSysRole());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private DataElementResponse createDataElement(String name) {
        DataElementRequest request = new DataElementRequest(
                null,
                null,
                null,
                List.of(new TextLine(languageId, name, null,
                        List.of(new TextLineContextEntry(contextId, Acceptability.PREFERRED)),
                        null, null)),
                null,
                new ItemRegistrationRequest(namespace.getId(), "DE-WORKSPACE-" + UUID.randomUUID(), "1.0.0",
                        List.of(), List.of())
        );
        return dataElementService.create(request);
    }

    private void setAdminStatus(UUID itemId, AdministrativeStatus adminStatus, RegistrationStatus registrationStatus) {
        AdministeredItem ai = administeredItemRepository.findByItem_Id(itemId).orElseThrow();
        RegistrationState state = ai.getRegistrationState();
        if (state == null) {
            state = new RegistrationState();
            ai.setRegistrationState(state);
        }
        state.setAdministrativeStatus(adminStatus);
        if (registrationStatus != null) {
            state.setRegistrationStatus(registrationStatus);
        }
        administeredItemRepository.save(ai);
    }

    private void setStewardship(UUID itemId, Contact stewardContact) {
        AdministeredItem ai = administeredItemRepository.findByItem_Id(itemId).orElseThrow();
        StewardshipRecord stewardship = new StewardshipRecord();
        stewardship.setOrganization(stewardContact.getOrganization());
        stewardship.setContact(stewardContact);
        stewardship = stewardshipRecordRepository.save(stewardship);
        ai.setStewardshipRecord(stewardship);
        administeredItemRepository.save(ai);
    }

    private void createApproval(AdministeredItem ai, RegistrationStatus from, RegistrationStatus to) {
        RegistrationTransitionApproval approval = new RegistrationTransitionApproval();
        approval.setAdministeredItem(ai);
        approval.setFromStatus(from);
        approval.setToStatus(to);
        approval.setRegistrar(registrar);
        approval.setAccountId(registrarAccount.getId());
        approval.setApprovedAt(java.time.LocalDateTime.now());
        approvalRepository.save(approval);
    }
}
