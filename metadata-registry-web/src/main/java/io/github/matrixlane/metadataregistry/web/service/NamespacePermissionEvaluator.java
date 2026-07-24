package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.entity.NamespacePermission;
import io.github.matrixlane.metadataregistry.web.entity.NamespacePermissionType;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespacePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NamespacePermissionEvaluator {

    private final NamespacePermissionRepository namespacePermissionRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;

    /**
     * Checks if a user has read access to a namespace.
     */
    public boolean hasReadPermission(UUID namespaceId, UUID accountId) {
        if (accountId == null) {
            return false;
        }

        // 1. SUPER_ADMIN has full access
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account != null && account.getSysRole() == SystemRole.SUPER_ADMIN) {
            return true;
        }

        // 2. Check user-level permission
        Optional<NamespacePermission> userPerm = namespacePermissionRepository
                .findByNamespaceIdAndAccountId(namespaceId, accountId);
        if (userPerm.isPresent()) {
            return userPerm.get().getPermissionType() == NamespacePermissionType.READ_ONLY ||
                   userPerm.get().getPermissionType() == NamespacePermissionType.READ_WRITE;
        }

        // 3. Resolve standard Contact and Organizations
        if (account != null) {
            List<Contact> contacts = contactRepository.findByAccount(account);
            if (!contacts.isEmpty()) {
                boolean hasExplicitDeny = false;
                boolean hasExplicitAllow = false;

                for (Contact contact : contacts) {
                    if (contact.getOrganization() != null) {
                        Optional<NamespacePermission> orgPerm = namespacePermissionRepository
                                .findByNamespaceIdAndOrganizationId(namespaceId, contact.getOrganization().getId());
                        if (orgPerm.isPresent()) {
                            if (orgPerm.get().getPermissionType() == NamespacePermissionType.NONE) {
                                hasExplicitDeny = true;
                            } else {
                                hasExplicitAllow = true;
                            }
                        }
                    }
                }

                // If any organization explicitly allows it, or if no organization explicitly denies it,
                // then organization is allowed (since default organization permission is read-only).
                if (hasExplicitAllow) {
                    return true;
                }
                if (hasExplicitDeny) {
                    return false; // NONE overrides default read-only
                }
                return true; // Default organization permission is read-only
            }
        }

        // 4. Default for non-contact users is read-only
        return true;
    }

    /**
     * Checks if a user has write (create/update/delete) access to a namespace.
     */
    public boolean hasWritePermission(UUID namespaceId, UUID accountId) {
        if (accountId == null) {
            return false;
        }

        // 1. SUPER_ADMIN has full access
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account != null && account.getSysRole() == SystemRole.SUPER_ADMIN) {
            return true;
        }

        // 2. Check user-level permission
        Optional<NamespacePermission> userPerm = namespacePermissionRepository
                .findByNamespaceIdAndAccountId(namespaceId, accountId);
        if (userPerm.isPresent()) {
            return userPerm.get().getPermissionType() == NamespacePermissionType.READ_WRITE;
        }

        // 3. Resolve standard Contact and Organizations
        if (account != null) {
            List<Contact> contacts = contactRepository.findByAccount(account);
            if (!contacts.isEmpty()) {
                for (Contact contact : contacts) {
                    if (contact.getOrganization() != null) {
                        Optional<NamespacePermission> orgPerm = namespacePermissionRepository
                                .findByNamespaceIdAndOrganizationId(namespaceId, contact.getOrganization().getId());
                        if (orgPerm.isPresent() && orgPerm.get().getPermissionType() == NamespacePermissionType.READ_WRITE) {
                            return true;
                        }
                    }
                }
                // If no organization has explicit READ_WRITE permission, return false
                // (Default organization permission is READ_ONLY).
                return false;
            }
        }

        // 4. Default for non-contact users is read-only, so write permission is false
        return false;
    }

    public boolean isRegistrar(UUID accountId, UUID registryId) {
        if (accountId == null || registryId == null) return false;
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) return false;
        if (account.getSysRole() == SystemRole.SUPER_ADMIN) return true;
        
        // Find if this account is associated with a registrar contact in the target registry
        List<Contact> contacts = contactRepository.findByAccount(account);
        return contacts.stream()
                .filter(c -> c.getRole() != null && "注册管理员".equals(c.getRole().getRoleName()))
                .anyMatch(c -> c.getOrganization() != null && registryId.equals(c.getOrganization().getId()));
    }

    public boolean isAnyRegistrarOrAdmin(UUID accountId) {
        if (accountId == null) return false;
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) return false;
        if (account.getSysRole() == SystemRole.SUPER_ADMIN) return true;
        
        // Find if this account is associated with any registrar contact
        List<Contact> contacts = contactRepository.findByAccount(account);
        return contacts.stream()
                .anyMatch(c -> c.getRole() != null && "注册管理员".equals(c.getRole().getRoleName()));
    }
}
