package io.github.matrixlane.metadataregistry.web.service.namespace;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Determines whether a namespace is "own" (自有) or "external" (外部).
 *
 * <p>Temporary determination per design D8 of namespace-identifier-strategy:
 * a namespace is own when its associated registration authority is this
 * system's registration authority — equivalent to the session-relative
 * determination in single-RA deployments. When the current account has
 * Registrar identities, the namespace's RA must match one of theirs;
 * otherwise (no auth context / no Registrar) a single-RA system treats its
 * only RA as the own one. Once active-registrar-ownership lands, this
 * switches to the active-Registrar session determination.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NamespaceOwnershipService {

    private final RegistrarRepository registrarRepository;
    private final io.github.matrixlane.metadataregistry.web.service.ContactAccountBindingService contactAccountBindingService;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;

    /**
     * @return true when the namespace is own relative to the current caller
     */
    public boolean isOwn(Namespace namespace) {
        if (namespace == null) {
            return false;
        }
        RegistrationAuthority namespaceRa = namespace.getRegistrationAuthority();
        if (namespaceRa == null || namespaceRa.getId() == null) {
            return false;
        }

        List<Registrar> identities = currentRegistrars();
        if (!identities.isEmpty()) {
            return identities.stream()
                    .map(Registrar::getRegistrationAuthority)
                    .filter(Objects::nonNull)
                    .map(RegistrationAuthority::getId)
                    .filter(Objects::nonNull)
                    .anyMatch(namespaceRa.getId()::equals);
        }

        // No Registrar context: single-RA deployments treat their only RA as own.
        List<RegistrationAuthority> all = registrationAuthorityRepository.findAll();
        return all.size() == 1 && namespaceRa.getId().equals(all.get(0).getId());
    }

    public boolean isExternal(Namespace namespace) {
        return !isOwn(namespace);
    }

    private List<Registrar> currentRegistrars() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return List.of();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            return contactAccountBindingService.registrarsOfAccount(user.id());
        }
        return List.of();
    }
}
