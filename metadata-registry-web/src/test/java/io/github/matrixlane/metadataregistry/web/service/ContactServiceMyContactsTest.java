package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.ext.basicandcore.ContactAccountBinding;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.contact.ContactIdentityKind;
import io.github.matrixlane.metadataregistry.web.dto.contact.MyContactRequest;
import io.github.matrixlane.metadataregistry.web.dto.contact.MyContactResponse;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.IndividualRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RoleRepository;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** "我的"联系人身份（add-submitter-contact-identity）：多身份、默认、Registrar 只读约束。 */
@ExtendWith(MockitoExtension.class)
class ContactServiceMyContactsTest {

    @Mock private ContactRepository contactRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private IndividualRepository individualRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ContactAccountBindingService bindingService;
    @Mock private SystemSettingService systemSettingService;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository registrationAuthorityRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.SubmissionRecordRepository submissionRecordRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.StewardshipRecordRepository stewardshipRecordRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionApprovalRepository registrationTransitionApprovalRepository;

    @InjectMocks
    private ContactService contactService;

    private UUID accountId = UUID.randomUUID();

    private ContactAccountBinding binding(UUID contactId, boolean isDefault) {
        ContactAccountBinding b = new ContactAccountBinding(contactId, accountId, isDefault);
        b.setId(UUID.randomUUID());
        return b;
    }

    @Test
    void listMyContacts_marksRegistrarAndDefault() {
        Contact plain = new Contact();
        plain.setId(UUID.randomUUID());
        Registrar registrar = new Registrar(new RegistrationAuthority());
        registrar.setId(UUID.randomUUID());
        when(bindingService.bindingsOfAccount(accountId))
                .thenReturn(List.of(binding(plain.getId(), false), binding(registrar.getId(), true)));
        when(contactRepository.findAllById(any())).thenReturn(List.of(plain, registrar));

        List<MyContactResponse> result = contactService.listMyContacts(accountId);

        assertThat(result).hasSize(2);
        // 按绑定顺序展示（默认身份不置顶）
        assertThat(result.get(0).isRegistrar()).isFalse();
        assertThat(result.get(0).isDefault()).isFalse();
        assertThat(result.get(1).isRegistrar()).isTrue();
        assertThat(result.get(1).isDefault()).isTrue();
    }

    @Test
    void listMyContacts_keepsBindingOrder_defaultNotSortedFirst() {
        // 默认身份不是首绑定时也按绑定顺序展示，不置顶
        Contact c1 = new Contact();
        c1.setId(UUID.randomUUID());
        Contact c2 = new Contact();
        c2.setId(UUID.randomUUID());
        Contact c3 = new Contact();
        c3.setId(UUID.randomUUID());
        when(bindingService.bindingsOfAccount(accountId))
                .thenReturn(List.of(binding(c1.getId(), false), binding(c2.getId(), true),
                        binding(c3.getId(), false)));
        when(contactRepository.findAllById(any())).thenReturn(List.of(c1, c2, c3));

        List<MyContactResponse> result = contactService.listMyContacts(accountId);

        assertThat(result).extracting(MyContactResponse::id).containsExactly(c1.getId(), c2.getId(), c3.getId());
    }

    @Test
    void createMyContact_firstIdentityBecomesDefault() {
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        when(organizationRepository.findById(org.getId())).thenReturn(Optional.of(org));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bindingService.hasAnyContact(accountId)).thenReturn(false);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(new Account()));

        MyContactResponse response = contactService.createMyContact(
                accountId, new MyContactRequest(org.getId(), null, null, null));

        verify(bindingService).bindAndSave(any(), eq(accountId), eq(true));
        assertThat(response.isDefault()).isTrue();
        assertThat(response.organizationId()).isEqualTo(org.getId());
    }

    @Test
    void createMyContact_secondIdentityNotDefault() {
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        when(organizationRepository.findById(org.getId())).thenReturn(Optional.of(org));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bindingService.hasAnyContact(accountId)).thenReturn(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(new Account()));

        MyContactResponse response = contactService.createMyContact(
                accountId, new MyContactRequest(org.getId(), null, null, null));

        verify(bindingService).bindAndSave(any(), eq(accountId), eq(false));
        assertThat(response.isDefault()).isFalse();
    }

    @Test
    void createMyContact_registrarRoleIsPureMetadata() {
        // 类型即身份：自选「注册管理员」角色不产生任何权限效应，仅作描述性元数据保存
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        Role registrarRole = new Role();
        registrarRole.setId(UUID.randomUUID());
        registrarRole.setRoleName("注册管理员");
        when(organizationRepository.findById(org.getId())).thenReturn(Optional.of(org));
        when(roleRepository.findById(registrarRole.getId())).thenReturn(Optional.of(registrarRole));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bindingService.hasAnyContact(accountId)).thenReturn(false);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(new Account()));

        MyContactResponse response = contactService.createMyContact(
                accountId, new MyContactRequest(org.getId(), registrarRole.getId(), null, null));

        assertThat(response.roleName()).isEqualTo("注册管理员");
        assertThat(response.isRegistrar()).isFalse();
    }

    @Test
    void createMyContact_backfillsAccountIndividual() {
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual individual =
                new io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual();
        Account account = new Account();
        account.setIndividual(individual);
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bindingService.hasAnyContact(accountId)).thenReturn(false);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        contactService.createMyContact(accountId, new MyContactRequest(organization.getId(), null, null, null));

        org.mockito.ArgumentCaptor<Contact> captor = org.mockito.ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(captor.capture());
        assertThat(captor.getValue().getIndividual()).isSameAs(individual);
    }

    @Test
    void createMyContact_registrarKind_rejectedForNonAdmin() {
        // 4.1 门控：非超级管理员创建 REGISTRAR 身份被拒（两版本一致，不分版本）
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(new Account()));

        assertThatThrownBy(() -> contactService.createMyContact(
                accountId, new MyContactRequest(UUID.randomUUID(), null, null, ContactIdentityKind.REGISTRAR)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅超级管理员可创建注册人员身份");
        verify(contactRepository, never()).save(any(Contact.class));
        verifyNoInteractions(organizationRepository, roleRepository);
    }

    @Test
    void createMyContact_registrarKind_allowedForSuperAdmin() {
        // 4.1 门控：超级管理员创建 REGISTRAR 身份成功，落库为 Registrar 实体（类型即身份）
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        Account account = new Account();
        account.setSysRole(SystemRole.SUPER_ADMIN);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bindingService.hasAnyContact(accountId)).thenReturn(false);

        MyContactResponse response = contactService.createMyContact(
                accountId, new MyContactRequest(organization.getId(), null, null, ContactIdentityKind.REGISTRAR));

        org.mockito.ArgumentCaptor<Contact> captor = org.mockito.ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(Registrar.class);
        assertThat(response.isRegistrar()).isTrue();
    }

    @Test
    void createMyContact_registrarKind_organizationIsRa_bindsRegistrationAuthority() {
        // M2：所选 organization 即注册机构时回填 Registrar.registrationAuthority，
        // 不产生 null-RA 的僵尸身份
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Account account = new Account();
        account.setSysRole(SystemRole.SUPER_ADMIN);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(organizationRepository.findById(ra.getId())).thenReturn(Optional.of(ra));
        when(registrationAuthorityRepository.findById(ra.getId())).thenReturn(Optional.of(ra));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bindingService.hasAnyContact(accountId)).thenReturn(false);

        MyContactResponse response = contactService.createMyContact(
                accountId, new MyContactRequest(ra.getId(), null, null, ContactIdentityKind.REGISTRAR));

        org.mockito.ArgumentCaptor<Contact> captor = org.mockito.ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(Registrar.class);
        assertThat(((Registrar) captor.getValue()).getRegistrationAuthority()).isSameAs(ra);
        assertThat(response.registrationAuthorityId()).isEqualTo(ra.getId());
    }

    @Test
    void createMyContact_registrarKind_plainOrganization_leavesRaNull() {
        // M2：所选 organization 非注册机构（普通组织）时 registrationAuthority 留空——
        // 合法状态，由 Registrar 管理流程补挂
        Organization plainOrg = new Organization();
        plainOrg.setId(UUID.randomUUID());
        Account account = new Account();
        account.setSysRole(SystemRole.SUPER_ADMIN);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(organizationRepository.findById(plainOrg.getId())).thenReturn(Optional.of(plainOrg));
        when(registrationAuthorityRepository.findById(plainOrg.getId())).thenReturn(Optional.empty());
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bindingService.hasAnyContact(accountId)).thenReturn(false);

        MyContactResponse response = contactService.createMyContact(
                accountId, new MyContactRequest(plainOrg.getId(), null, null, ContactIdentityKind.REGISTRAR));

        org.mockito.ArgumentCaptor<Contact> captor = org.mockito.ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(Registrar.class);
        assertThat(((Registrar) captor.getValue()).getRegistrationAuthority()).isNull();
        assertThat(response.registrationAuthorityId()).isNull();
    }

    @Test
    void createMyContact_contactKind_unaffectedForNonAdmin() {
        // 4.1 门控：非管理员创建 CONTACT 身份不受影响（kind 显式 CONTACT 与缺省同路径）
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(new Account()));
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bindingService.hasAnyContact(accountId)).thenReturn(false);

        MyContactResponse response = contactService.createMyContact(
                accountId, new MyContactRequest(organization.getId(), null, null, ContactIdentityKind.CONTACT));

        org.mockito.ArgumentCaptor<Contact> captor = org.mockito.ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(captor.capture());
        assertThat(captor.getValue()).isExactlyInstanceOf(Contact.class);
        assertThat(response.isRegistrar()).isFalse();
    }

    @Test
    void updateMyContact_registrarRow_roleOnlyUpdate() {
        // add-registrar-three-elements: Registrar 行允许同时改 Role/Individual/Organization。
        // 这里传入新的 organizationId + roleId，验证两要素都按请求应用。
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Registrar registrar = new Registrar(ra);
        registrar.setId(UUID.randomUUID());
        Organization originalOrg = new Organization();
        originalOrg.setId(UUID.randomUUID());
        registrar.setOrganization(originalOrg);
        Organization newOrg = new Organization();
        newOrg.setId(UUID.randomUUID());
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setRoleName("数据管理员");
        when(contactRepository.findById(registrar.getId())).thenReturn(Optional.of(registrar));
        when(bindingService.accountIdOfContact(registrar.getId())).thenReturn(Optional.of(accountId));
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(organizationRepository.findById(newOrg.getId())).thenReturn(Optional.of(newOrg));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bindingService.defaultBindingOf(accountId)).thenReturn(Optional.empty());

        MyContactResponse response = contactService.updateMyContact(
                accountId, registrar.getId(), new MyContactRequest(newOrg.getId(), role.getId(), null, null));

        assertThat(response.roleId()).isEqualTo(role.getId());
        assertThat(registrar.getOrganization()).isSameAs(newOrg);
    }

    @Test
    void updateMyContact_registrarRow_clearRole() {
        // Role 允许置空：roleId 传 null 时清除关联；但 Organization 在 Registrar 中必填，传入既有 organizationId 保留。
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Registrar registrar = new Registrar(ra);
        registrar.setId(UUID.randomUUID());
        Organization existingOrg = new Organization();
        existingOrg.setId(UUID.randomUUID());
        registrar.setOrganization(existingOrg);
        Role existingRole = new Role();
        existingRole.setId(UUID.randomUUID());
        registrar.setRole(existingRole);
        when(contactRepository.findById(registrar.getId())).thenReturn(Optional.of(registrar));
        when(bindingService.accountIdOfContact(registrar.getId())).thenReturn(Optional.of(accountId));
        when(organizationRepository.findById(existingOrg.getId())).thenReturn(Optional.of(existingOrg));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bindingService.defaultBindingOf(accountId)).thenReturn(Optional.empty());

        MyContactResponse response = contactService.updateMyContact(
                accountId, registrar.getId(), new MyContactRequest(existingOrg.getId(), null, null, null));

        assertThat(registrar.getRole()).isNull();
        assertThat(response.roleId()).isNull();
        assertThat(registrar.getOrganization()).isSameAs(existingOrg);
    }

    @Test
    void updateMyContact_registrarRow_rejectsMissingOrganization() {
        // Registrar 上下文中 organizationId 为 null MUST 返回校验错误（spec registrar-identity-elements）。
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Registrar registrar = new Registrar(ra);
        registrar.setId(UUID.randomUUID());
        Organization originalOrg = new Organization();
        originalOrg.setId(UUID.randomUUID());
        registrar.setOrganization(originalOrg);
        when(contactRepository.findById(registrar.getId())).thenReturn(Optional.of(registrar));
        when(bindingService.accountIdOfContact(registrar.getId())).thenReturn(Optional.of(accountId));

        assertThatThrownBy(() -> contactService.updateMyContact(
                accountId, registrar.getId(), new MyContactRequest(null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Registrar");
    }

    @Test
    void updateMyContact_rejectsForeignContact() {
        UUID foreign = UUID.randomUUID();
        when(contactRepository.findById(foreign)).thenReturn(Optional.of(new Contact()));
        when(bindingService.accountIdOfContact(foreign)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> contactService.updateMyContact(
                accountId, foreign, new MyContactRequest(UUID.randomUUID(), null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteMyContact_personalEdition_registrarRejectedEvenWithoutReferences() {
        // 个人版：Registrar 行随注册机构存在，一律拒绝删除（位于引用守卫之前，不看引用）
        Registrar registrar = new Registrar(new RegistrationAuthority());
        registrar.setId(UUID.randomUUID());
        when(contactRepository.findById(registrar.getId())).thenReturn(Optional.of(registrar));
        when(bindingService.accountIdOfContact(registrar.getId())).thenReturn(Optional.of(accountId));
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);

        assertThatThrownBy(() -> contactService.deleteMyContact(accountId, registrar.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("所属注册机构仍存在");
        verify(contactRepository, never()).delete(any(Contact.class));
    }

    @Test
    void deleteMyContact_nullEdition_registrarRejectedConservatively() {
        // 未初始化（getEdition 返回 null）按个人版保守处理
        Registrar registrar = new Registrar(new RegistrationAuthority());
        registrar.setId(UUID.randomUUID());
        when(contactRepository.findById(registrar.getId())).thenReturn(Optional.of(registrar));
        when(bindingService.accountIdOfContact(registrar.getId())).thenReturn(Optional.of(accountId));

        assertThatThrownBy(() -> contactService.deleteMyContact(accountId, registrar.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("所属注册机构仍存在");
        verify(contactRepository, never()).delete(any(Contact.class));
    }

    @Test
    void deleteMyContact_personalEdition_nullRaRegistrar_deletes() {
        // M2：null-RA 的 Registrar 行（createMyContact 选普通组织产生）允许删除，
        // 走正常解绑流程；守卫消息不再出现
        Registrar registrar = new Registrar();
        registrar.setId(UUID.randomUUID());
        when(contactRepository.findById(registrar.getId())).thenReturn(Optional.of(registrar));
        when(bindingService.accountIdOfContact(registrar.getId())).thenReturn(Optional.of(accountId));
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        when(bindingService.defaultBindingOf(accountId))
                .thenReturn(Optional.of(binding(UUID.randomUUID(), true)));

        contactService.deleteMyContact(accountId, registrar.getId());

        verify(bindingService).unbind(registrar.getId());
        verify(contactRepository).delete(registrar);
    }

    @Test
    void deleteMyContact_teamEdition_registrarDeletedEvenWithReferences() {
        // 团队版：移除 SubmissionRecord/Stewardship/审批三处引用守卫，任何身份可直接删除；
        // 解绑、清 activeRegistrarId、默认递补逻辑保留
        Registrar registrar = new Registrar(new RegistrationAuthority());
        registrar.setId(UUID.randomUUID());
        when(contactRepository.findById(registrar.getId())).thenReturn(Optional.of(registrar));
        when(bindingService.accountIdOfContact(registrar.getId())).thenReturn(Optional.of(accountId));
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.TEAM);
        when(bindingService.defaultBindingOf(accountId)).thenReturn(Optional.of(binding(registrar.getId(), true)));
        Account admin = new Account();
        admin.setId(accountId);
        admin.setActiveRegistrarId(registrar.getId());
        when(accountRepository.findByActiveRegistrarId(registrar.getId())).thenReturn(List.of(admin));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        contactService.deleteMyContact(accountId, registrar.getId());

        assertThat(admin.getActiveRegistrarId()).isNull();
        verify(bindingService).unbind(registrar.getId());
        verify(contactRepository).delete(registrar);
        // 三处引用守卫已移除：团队版不再查询任何引用
        verifyNoInteractions(submissionRecordRepository, stewardshipRecordRepository,
                registrationTransitionApprovalRepository);
    }

    @Test
    void deleteMyContact_personalEdition_contactWithoutReferences_deletes() {
        // 个人版 CONTACT 行：无引用时仍可删除（默认递补逻辑不变）
        UUID removed = UUID.randomUUID();
        UUID remaining = UUID.randomUUID();
        Contact contact = new Contact();
        contact.setId(removed);
        when(contactRepository.findById(removed)).thenReturn(Optional.of(contact));
        when(bindingService.accountIdOfContact(removed)).thenReturn(Optional.of(accountId));
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        when(bindingService.defaultBindingOf(accountId))
                .thenReturn(Optional.of(binding(removed, true)));
        when(bindingService.firstBindingOf(accountId))
                .thenReturn(Optional.of(binding(remaining, false)));

        contactService.deleteMyContact(accountId, removed);

        verify(bindingService).unbind(removed);
        verify(contactRepository).delete(contact);
        verify(bindingService).markDefault(accountId, remaining);
    }

    @Test
    void deleteMyContact_defaultPromotesRemaining() {
        UUID removed = UUID.randomUUID();
        UUID remaining = UUID.randomUUID();
        Contact contact = new Contact();
        contact.setId(removed);
        when(contactRepository.findById(removed)).thenReturn(Optional.of(contact));
        when(bindingService.accountIdOfContact(removed)).thenReturn(Optional.of(accountId));
        when(bindingService.defaultBindingOf(accountId))
                .thenReturn(Optional.of(binding(removed, true)));
        when(bindingService.firstBindingOf(accountId))
                .thenReturn(Optional.of(binding(remaining, false)));

        contactService.deleteMyContact(accountId, removed);

        verify(bindingService).unbind(removed);
        verify(contactRepository).delete(contact);
        verify(bindingService).markDefault(accountId, remaining);
    }

    @Test
    void deleteMyContact_nonDefaultLeavesDefaultAlone() {
        UUID removed = UUID.randomUUID();
        Contact contact = new Contact();
        contact.setId(removed);
        when(contactRepository.findById(removed)).thenReturn(Optional.of(contact));
        when(bindingService.accountIdOfContact(removed)).thenReturn(Optional.of(accountId));
        when(bindingService.defaultBindingOf(accountId))
                .thenReturn(Optional.of(binding(UUID.randomUUID(), true)));

        contactService.deleteMyContact(accountId, removed);

        verify(bindingService, never()).markDefault(any(), any());
    }

    @Test
    void deleteMyContact_referencedBySubmissionRecord_conflict() {
        // 个人版 CONTACT 行：提交记录引用守卫保留
        UUID contactId = UUID.randomUUID();
        Contact contact = new Contact();
        contact.setId(contactId);
        when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));
        when(bindingService.accountIdOfContact(contactId)).thenReturn(Optional.of(accountId));
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        when(submissionRecordRepository.existsByContactId(contactId)).thenReturn(true);

        assertThatThrownBy(() -> contactService.deleteMyContact(accountId, contactId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("提交记录引用");
        verify(contactRepository, never()).delete(any(Contact.class));
    }

    @Test
    void setDefaultMyContact_delegatesToBindingService() {
        UUID contactId = UUID.randomUUID();
        Contact contact = new Contact();
        contact.setId(contactId);
        when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));
        when(bindingService.accountIdOfContact(contactId)).thenReturn(Optional.of(accountId));

        contactService.setDefaultMyContact(accountId, contactId);

        verify(bindingService).markDefault(accountId, contactId);
    }

    @Test
    void updateMyContact_appliesOrganizationAndRole() {
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID());
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setRoleName("数据管理员");
        when(contactRepository.findById(contact.getId())).thenReturn(Optional.of(contact));
        when(bindingService.accountIdOfContact(contact.getId())).thenReturn(Optional.of(accountId));
        when(organizationRepository.findById(org.getId())).thenReturn(Optional.of(org));
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));

        MyContactResponse response = contactService.updateMyContact(
                accountId, contact.getId(), new MyContactRequest(org.getId(), role.getId(), null, null));

        assertThat(response.organizationId()).isEqualTo(org.getId());
        assertThat(response.roleId()).isEqualTo(role.getId());
    }
}
