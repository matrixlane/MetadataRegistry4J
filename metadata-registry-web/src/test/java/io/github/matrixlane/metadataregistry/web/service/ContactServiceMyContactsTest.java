package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.ext.basicandcore.ContactAccountBinding;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.contact.MyContactRequest;
import io.github.matrixlane.metadataregistry.web.dto.contact.MyContactResponse;
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
    @Mock private io.github.matrixlane.metadataregistry.web.repository.SubmissionRecordRepository submissionRecordRepository;

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
        assertThat(result.get(0).isRegistrar()).isFalse();
        assertThat(result.get(0).isDefault()).isFalse();
        assertThat(result.get(1).isRegistrar()).isTrue();
        assertThat(result.get(1).isDefault()).isTrue();
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
                accountId, new MyContactRequest(org.getId(), null));

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
                accountId, new MyContactRequest(org.getId(), null));

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
                accountId, new MyContactRequest(org.getId(), registrarRole.getId()));

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

        contactService.createMyContact(accountId, new MyContactRequest(organization.getId(), null));

        org.mockito.ArgumentCaptor<Contact> captor = org.mockito.ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(captor.capture());
        assertThat(captor.getValue().getIndividual()).isSameAs(individual);
    }

    @Test
    void updateMyContact_registrarRow_roleOnlyUpdate() {
        // Registrar 行仅允许修改 Role 关联：身份由类型确定，组织等其他字段不受请求影响
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Registrar registrar = new Registrar(ra);
        registrar.setId(UUID.randomUUID());
        Organization originalOrg = new Organization();
        originalOrg.setId(UUID.randomUUID());
        registrar.setOrganization(originalOrg);
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setRoleName("数据管理员");
        when(contactRepository.findById(registrar.getId())).thenReturn(Optional.of(registrar));
        when(bindingService.accountIdOfContact(registrar.getId())).thenReturn(Optional.of(accountId));
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bindingService.defaultBindingOf(accountId)).thenReturn(Optional.empty());

        MyContactResponse response = contactService.updateMyContact(
                accountId, registrar.getId(), new MyContactRequest(UUID.randomUUID(), role.getId()));

        assertThat(response.roleId()).isEqualTo(role.getId());
        assertThat(registrar.getOrganization()).isSameAs(originalOrg);
    }

    @Test
    void updateMyContact_registrarRow_clearRole() {
        // Role 允许置空：roleId 传 null 时清除关联
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Registrar registrar = new Registrar(ra);
        registrar.setId(UUID.randomUUID());
        Role existingRole = new Role();
        existingRole.setId(UUID.randomUUID());
        registrar.setRole(existingRole);
        when(contactRepository.findById(registrar.getId())).thenReturn(Optional.of(registrar));
        when(bindingService.accountIdOfContact(registrar.getId())).thenReturn(Optional.of(accountId));
        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bindingService.defaultBindingOf(accountId)).thenReturn(Optional.empty());

        MyContactResponse response = contactService.updateMyContact(
                accountId, registrar.getId(), new MyContactRequest(UUID.randomUUID(), null));

        assertThat(registrar.getRole()).isNull();
        assertThat(response.roleId()).isNull();
    }

    @Test
    void updateMyContact_rejectsForeignContact() {
        UUID foreign = UUID.randomUUID();
        when(contactRepository.findById(foreign)).thenReturn(Optional.of(new Contact()));
        when(bindingService.accountIdOfContact(foreign)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> contactService.updateMyContact(
                accountId, foreign, new MyContactRequest(UUID.randomUUID(), null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteMyContact_rejectsRegistrar() {
        Registrar registrar = new Registrar(new RegistrationAuthority());
        registrar.setId(UUID.randomUUID());
        when(contactRepository.findById(registrar.getId())).thenReturn(Optional.of(registrar));
        when(bindingService.accountIdOfContact(registrar.getId())).thenReturn(Optional.of(accountId));

        assertThatThrownBy(() -> contactService.deleteMyContact(accountId, registrar.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Registrar");
        verify(contactRepository, never()).delete(any(Contact.class));
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
        UUID contactId = UUID.randomUUID();
        Contact contact = new Contact();
        contact.setId(contactId);
        when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));
        when(bindingService.accountIdOfContact(contactId)).thenReturn(Optional.of(accountId));
        when(bindingService.defaultBindingOf(accountId)).thenReturn(Optional.empty());
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
                accountId, contact.getId(), new MyContactRequest(org.getId(), role.getId()));

        assertThat(response.organizationId()).isEqualTo(org.getId());
        assertThat(response.roleId()).isEqualTo(role.getId());
    }
}
