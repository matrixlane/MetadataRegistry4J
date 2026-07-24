package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespacePermissionRequest;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespacePermissionResponse;
import io.github.matrixlane.metadataregistry.web.entity.NamespacePermission;
import io.github.matrixlane.metadataregistry.web.repository.NamespacePermissionRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class NamespacePermissionService {

    private final NamespacePermissionRepository repository;
    private final AccountRepository accountRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public List<NamespacePermissionResponse> getPermissions(UUID namespaceId) {
        return repository.findByNamespaceId(namespaceId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public NamespacePermissionResponse savePermission(UUID namespaceId, NamespacePermissionRequest request) {
        Optional<NamespacePermission> existing;
        if (request.appUserId() != null) {
            existing = repository.findByNamespaceIdAndAccountId(namespaceId, request.appUserId());
        } else if (request.organizationId() != null) {
            existing = repository.findByNamespaceIdAndOrganizationId(namespaceId, request.organizationId());
        } else {
            throw new IllegalArgumentException("AccountId or OrganizationId must be provided");
        }

        NamespacePermission entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setPermissionType(request.permissionType());
        } else {
            entity = new NamespacePermission(
                    namespaceId,
                    request.appUserId(),
                    request.organizationId(),
                    request.permissionType()
            );
        }

        return toResponse(repository.save(entity));
    }

    public void deletePermission(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("NamespacePermission", id);
        }
        repository.deleteById(id);
    }

    private NamespacePermissionResponse toResponse(NamespacePermission entity) {
        String username = null;
        String displayName = null;
        if (entity.getAccountId() != null) {
            Account user = accountRepository.findById(entity.getAccountId()).orElse(null);
            if (user != null) {
                username = user.getUsername();
                if (user.getIndividual() != null && user.getIndividual().getName() != null) {
                    displayName = user.getIndividual().getName().getValue();
                }
            }
        }

        String orgName = null;
        if (entity.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(entity.getOrganizationId()).orElse(null);
            if (org != null && !org.getName().isEmpty()) {
                orgName = org.getName().get(0).getValue();
            }
        }

        return new NamespacePermissionResponse(
                entity.getId(),
                entity.getNamespaceId(),
                entity.getAccountId(),
                username,
                displayName,
                entity.getOrganizationId(),
                orgName,
                entity.getPermissionType()
        );
    }
}
