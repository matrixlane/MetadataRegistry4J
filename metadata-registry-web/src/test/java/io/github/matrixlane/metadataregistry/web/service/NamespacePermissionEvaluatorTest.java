package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.entity.NamespacePermission;
import io.github.matrixlane.metadataregistry.web.entity.NamespacePermissionType;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespacePermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NamespacePermissionEvaluatorTest {

    @Mock
    private NamespacePermissionRepository namespacePermissionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private ContactRepository contactRepository;

    @InjectMocks
    private NamespacePermissionEvaluator evaluator;

    private UUID namespaceId;
    private UUID accountId;
    private Account account;

    @BeforeEach
    void setUp() {
        namespaceId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        account = new Account();
        account.setId(accountId);
    }

    @Test
    void hasReadPermission_adminShouldHaveFullAccess() {
        account.setSysRole(SystemRole.SUPER_ADMIN);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        boolean hasRead = evaluator.hasReadPermission(namespaceId, accountId);
        boolean hasWrite = evaluator.hasWritePermission(namespaceId, accountId);

        assertThat(hasRead).isTrue();
        assertThat(hasWrite).isTrue();
    }

    @Test
    void hasReadPermission_explicitUserPermissionNone() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        NamespacePermission perm = new NamespacePermission(namespaceId, accountId, null, NamespacePermissionType.NONE);
        when(namespacePermissionRepository.findByNamespaceIdAndAccountId(namespaceId, accountId))
                .thenReturn(Optional.of(perm));

        boolean hasRead = evaluator.hasReadPermission(namespaceId, accountId);
        boolean hasWrite = evaluator.hasWritePermission(namespaceId, accountId);

        assertThat(hasRead).isFalse();
        assertThat(hasWrite).isFalse();
    }

    @Test
    void hasWritePermission_explicitUserPermissionReadWrite() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        NamespacePermission perm = new NamespacePermission(namespaceId, accountId, null, NamespacePermissionType.READ_WRITE);
        when(namespacePermissionRepository.findByNamespaceIdAndAccountId(namespaceId, accountId))
                .thenReturn(Optional.of(perm));

        boolean hasRead = evaluator.hasReadPermission(namespaceId, accountId);
        boolean hasWrite = evaluator.hasWritePermission(namespaceId, accountId);

        assertThat(hasRead).isTrue();
        assertThat(hasWrite).isTrue();
    }

    @Test
    void hasReadPermission_explicitUserPermissionReadOnly() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        NamespacePermission perm = new NamespacePermission(namespaceId, accountId, null, NamespacePermissionType.READ_ONLY);
        when(namespacePermissionRepository.findByNamespaceIdAndAccountId(namespaceId, accountId))
                .thenReturn(Optional.of(perm));

        boolean hasRead = evaluator.hasReadPermission(namespaceId, accountId);
        boolean hasWrite = evaluator.hasWritePermission(namespaceId, accountId);

        assertThat(hasRead).isTrue();
        assertThat(hasWrite).isFalse();
    }

    @Test
    void hasWritePermission_inheritedOrganizationPermissionReadWrite() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(namespacePermissionRepository.findByNamespaceIdAndAccountId(namespaceId, accountId))
                .thenReturn(Optional.empty());

        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        Contact contact = new Contact();
        contact.setAccount(account);
        contact.setOrganization(org);

        when(contactRepository.findByAccount(account)).thenReturn(List.of(contact));
        NamespacePermission orgPerm = new NamespacePermission(namespaceId, null, org.getId(), NamespacePermissionType.READ_WRITE);
        when(namespacePermissionRepository.findByNamespaceIdAndOrganizationId(namespaceId, org.getId()))
                .thenReturn(Optional.of(orgPerm));

        boolean hasRead = evaluator.hasReadPermission(namespaceId, accountId);
        boolean hasWrite = evaluator.hasWritePermission(namespaceId, accountId);

        assertThat(hasRead).isTrue();
        assertThat(hasWrite).isTrue();
    }

    @Test
    void hasReadPermission_inheritedOrganizationPermissionNone() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(namespacePermissionRepository.findByNamespaceIdAndAccountId(namespaceId, accountId))
                .thenReturn(Optional.empty());

        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        Contact contact = new Contact();
        contact.setAccount(account);
        contact.setOrganization(org);

        when(contactRepository.findByAccount(account)).thenReturn(List.of(contact));
        NamespacePermission orgPerm = new NamespacePermission(namespaceId, null, org.getId(), NamespacePermissionType.NONE);
        when(namespacePermissionRepository.findByNamespaceIdAndOrganizationId(namespaceId, org.getId()))
                .thenReturn(Optional.of(orgPerm));

        boolean hasRead = evaluator.hasReadPermission(namespaceId, accountId);
        boolean hasWrite = evaluator.hasWritePermission(namespaceId, accountId);

        assertThat(hasRead).isFalse();
        assertThat(hasWrite).isFalse();
    }

    @Test
    void hasReadPermission_defaultReadOnlyForNonContact() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(namespacePermissionRepository.findByNamespaceIdAndAccountId(namespaceId, accountId))
                .thenReturn(Optional.empty());
        when(contactRepository.findByAccount(account)).thenReturn(Collections.emptyList());

        boolean hasRead = evaluator.hasReadPermission(namespaceId, accountId);
        boolean hasWrite = evaluator.hasWritePermission(namespaceId, accountId);

        assertThat(hasRead).isTrue();
        assertThat(hasWrite).isFalse();
    }
}
