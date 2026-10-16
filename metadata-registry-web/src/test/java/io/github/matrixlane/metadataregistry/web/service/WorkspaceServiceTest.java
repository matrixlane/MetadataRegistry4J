package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.StewardshipRecord;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionInfo;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSubmitRequest;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegisteredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionApprovalRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionRuleRepository;
import io.github.matrixlane.metadataregistry.web.repository.VersionHistoryRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * acg-submission：提交信息校验（validateSubmissionInfo）经 submit / requestCandidate
 * 两个入口的行为，与 ItemAdministrationService.resolveContactAndOrganization 保持一致。
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock private AdministeredItemRepository administeredItemRepository;
    @Mock private VersionHistoryRepository versionHistoryRepository;
    @Mock private RegistrationTransitionApprovalRepository approvalRepository;
    @Mock private RegistrationTransitionRuleRepository ruleRepository;
    @Mock private ActiveRegistrarService activeRegistrarService;
    @Mock private ItemAdministrationService itemAdministrationService;
    @Mock private AdministrativeStatusService administrativeStatusService;
    @Mock private RegistrationProcessService registrationProcessService;
    @Mock private WorkspaceRollbackService workspaceRollbackService;
    @Mock private ContactAccountService contactAccountService;
    @Mock private ContactRepository contactRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private DesignationRepository designationRepository;
    @Mock private RegisteredItemRepository registeredItemRepository;

    @InjectMocks
    private WorkspaceService workspaceService;

    private UUID accountId;
    private UUID itemId;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        itemId = UUID.randomUUID();
    }

    private Contact newContact(UUID id, Organization organization) {
        Contact contact = new Contact();
        contact.setId(id);
        contact.setOrganization(organization);
        return contact;
    }

    private Organization newOrganization(UUID id) {
        Organization organization = new Organization();
        organization.setId(id);
        return organization;
    }

    private AdministeredItem newDraftItem() {
        Item item = new Item() {};
        item.setId(itemId);
        AdministeredItem ai = new AdministeredItem(item);
        ai.setCreationDateTime(LocalDateTime.now());
        item.setIdentifiedItem(ai);
        RegistrationState state = new RegistrationState();
        state.setRegistrationStatus(RegistrationStatus.INCOMPLETE);
        state.setAdministrativeStatus(AdministrativeStatus.DRAFT);
        ai.setRegistrationState(state);
        when(administeredItemRepository.findByItem_Id(itemId)).thenReturn(Optional.of(ai));
        when(itemAdministrationService.isItemOwnedByAccount(any(), any())).thenReturn(true);
        return ai;
    }

    @Test
    void submit_organizationWithoutIdentity_rejectsWithGuidance() {
        UUID orgId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(newOrganization(orgId)));
        when(contactAccountService.boundContactIdsOfAccount(accountId)).thenReturn(List.of());

        WorkspaceSubmitRequest request = new WorkspaceSubmitRequest(
                List.of(itemId), new SubmissionInfo(null, orgId));

        assertThatThrownBy(() -> workspaceService.submit(request, accountId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先创建为该机构联系人");
        verify(itemAdministrationService, never()).recordSubmissionForBatch(any(), any());
    }

    @Test
    void submit_contactNotUnderOrganization_rejects() {
        Organization orgA = newOrganization(UUID.randomUUID());
        Organization orgB = newOrganization(UUID.randomUUID());
        Contact identityInA = newContact(UUID.randomUUID(), orgA);
        Contact contactInB = newContact(UUID.randomUUID(), orgB);
        when(organizationRepository.findById(orgA.getId())).thenReturn(Optional.of(orgA));
        when(contactAccountService.boundContactIdsOfAccount(accountId))
                .thenReturn(List.of(identityInA.getId()));
        when(contactRepository.findAllById(List.of(identityInA.getId()))).thenReturn(List.of(identityInA));
        when(contactRepository.findById(contactInB.getId())).thenReturn(Optional.of(contactInB));
        when(contactAccountService.accountIdOfContact(contactInB.getId()))
                .thenReturn(Optional.of(accountId));

        WorkspaceSubmitRequest request = new WorkspaceSubmitRequest(
                List.of(itemId), new SubmissionInfo(contactInB.getId(), orgA.getId()));

        assertThatThrownBy(() -> workspaceService.submit(request, accountId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须属于所选提交组织");
        verify(itemAdministrationService, never()).recordSubmissionForBatch(any(), any());
    }

    @Test
    void submit_organizationAndOwnContact_passesAndDelegatesBatchRecord() {
        // contactId + organizationId 一致（该组织下的本人身份）→ 校验通过，正常提交
        Organization org = newOrganization(UUID.randomUUID());
        Contact identity = newContact(UUID.randomUUID(), org);
        newDraftItem();
        when(organizationRepository.findById(org.getId())).thenReturn(Optional.of(org));
        when(contactAccountService.boundContactIdsOfAccount(accountId))
                .thenReturn(List.of(identity.getId()));
        when(contactRepository.findAllById(List.of(identity.getId()))).thenReturn(List.of(identity));
        when(contactRepository.findById(identity.getId())).thenReturn(Optional.of(identity));
        when(contactAccountService.accountIdOfContact(identity.getId()))
                .thenReturn(Optional.of(accountId));

        WorkspaceSubmitRequest request = new WorkspaceSubmitRequest(
                List.of(itemId), new SubmissionInfo(identity.getId(), org.getId()));

        workspaceService.submit(request, accountId);

        verify(itemAdministrationService).recordSubmissionForBatch(
                any(), any());
    }

    @Test
    void submit_contactOnly_keepsLegacyValidation() {
        // 仅 contactId：沿用"联系人必须属于当前账号"校验，通过后正常提交
        Organization org = newOrganization(UUID.randomUUID());
        Contact contact = newContact(UUID.randomUUID(), org);
        newDraftItem();
        when(contactRepository.findById(contact.getId())).thenReturn(Optional.of(contact));
        when(contactAccountService.accountIdOfContact(contact.getId()))
                .thenReturn(Optional.of(accountId));

        WorkspaceSubmitRequest request = new WorkspaceSubmitRequest(
                List.of(itemId), new SubmissionInfo(contact.getId(), null));

        workspaceService.submit(request, accountId);

        verify(itemAdministrationService).recordSubmissionForBatch(
                any(), any());
    }

    @Test
    void submit_contactBoundToOtherAccount_rejects() {
        // 回归：仅 contactId 时"联系人必须属于当前账号"校验保留
        Organization org = newOrganization(UUID.randomUUID());
        Contact contact = newContact(UUID.randomUUID(), org);
        when(contactRepository.findById(contact.getId())).thenReturn(Optional.of(contact));
        when(contactAccountService.accountIdOfContact(contact.getId())).thenReturn(Optional.empty());

        WorkspaceSubmitRequest request = new WorkspaceSubmitRequest(
                List.of(itemId), new SubmissionInfo(contact.getId(), null));

        assertThatThrownBy(() -> workspaceService.submit(request, accountId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须是当前账号绑定的联系人身份");
    }

    @Test
    void requestCandidate_organizationWithoutIdentity_sameRuleAsSubmit() {
        // requestCandidate 入口与 submit 同一套校验规则
        UUID orgId = UUID.randomUUID();
        AdministeredItem ai = newDraftItem();
        ai.setStewardshipRecord(new StewardshipRecord());
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(newOrganization(orgId)));
        when(contactAccountService.boundContactIdsOfAccount(accountId)).thenReturn(List.of());

        assertThatThrownBy(() -> workspaceService.requestCandidate(
                itemId, new SubmissionInfo(null, orgId), accountId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先创建为该机构联系人");
        verify(itemAdministrationService, never()).recordSubmission(any(), any());
    }
}
