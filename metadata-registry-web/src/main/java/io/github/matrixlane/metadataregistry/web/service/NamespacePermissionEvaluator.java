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
    private final ContactAccountBindingService contactAccountBindingService;
    private final io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository namespaceRepository;
    private final io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository registrationAuthorityRepository;

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
            List<Contact> contacts = contactAccountBindingService.contactsOfAccount(account);
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

        // 3. Submitter 通道（add-submitter-contact-identity）：系统中恰好只有一个
        // 注册机构时，已登记联系人的用户可在注册机构自有命名空间提交元数据（条目归属
        // 该系统唯一注册机构，与 resolveOwnershipForCreate 的 Submitter 归属推导一致）。
        if (account != null && contactAccountBindingService.hasAnyContact(accountId)) {
            io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace ns =
                    namespaceRepository.findById(namespaceId).orElse(null);
            if (ns != null && ns.getRegistrationAuthority() != null) {
                List<io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority> all =
                        registrationAuthorityRepository.findAll();
                if (all.size() == 1 && all.get(0).getId().equals(ns.getRegistrationAuthority().getId())) {
                    return true;
                }
            }
        }

        // 4. Resolve standard Contact and Organizations
        if (account != null) {
            List<Contact> contacts = contactAccountBindingService.contactsOfAccount(account);
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

        // 5. Default for non-contact users is read-only, so write permission is false
        return false;
    }

    /**
     * 账号是否持有指定注册机构（organization id）下的 Registrar 身份。
     * Registrar 身份由 Contact 子类 Registrar 实例确定（类型即身份，与 Role 无关）。
     */
    public boolean isRegistrar(UUID accountId, UUID registryId) {
        if (accountId == null || registryId == null) return false;
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) return false;
        if (account.getSysRole() == SystemRole.SUPER_ADMIN) return true;

        return contactAccountBindingService.registrarsOfAccount(accountId).stream()
                .anyMatch(r -> r.getOrganization() != null && registryId.equals(r.getOrganization().getId()));
    }

    /**
     * 账号是否持有任一 Registrar 身份（Registrar 子类实例，类型即身份，与 Role 无关）。
     */
    public boolean isAnyRegistrarOrAdmin(UUID accountId) {
        if (accountId == null) return false;
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account == null) return false;
        if (account.getSysRole() == SystemRole.SUPER_ADMIN) return true;

        return !contactAccountBindingService.registrarsOfAccount(accountId).isEmpty();
    }
}
