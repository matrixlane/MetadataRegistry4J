package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierVersionHistory;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.SemVerValidator;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.VersionStatus;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.IdentifierVersionHistoryResponse;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierCreateRequest;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierResponse;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.VersionTransitionRequest;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierVersionHistoryRepository;
import io.github.matrixlane.metadataregistry.web.repository.IdentifiedItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import io.github.matrixlane.metadataregistry.web.service.namespace.NamespaceConstraintValidator;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ScopedIdentifierService {

    private final ScopedIdentifierRepository scopedIdentifierRepository;
    private final IdentifierVersionHistoryRepository historyRepository;
    private final NamespaceRepository namespaceRepository;
    private final IdentifiedItemRepository identifiedItemRepository;
    private final IdentifierGenerationService generationService;
    private final NamespaceConstraintValidator namespaceConstraintValidator;

    @Transactional(readOnly = true)
    public Page<ScopedIdentifierResponse> list(Pageable pageable) {
        return scopedIdentifierRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ScopedIdentifierResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ScopedIdentifierResponse create(ScopedIdentifierCreateRequest request) {
        Namespace namespace = namespaceRepository.findById(request.namespaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Namespace", request.namespaceId()));

        IdentifiedItem identifiedItem = identifiedItemRepository.findById(request.identifiedItemId())
                .orElseThrow(() -> new ResourceNotFoundException("IdentifiedItem", request.identifiedItemId()));

        String identifier = request.identifier();
        if (identifier == null || identifier.isBlank()) {
            identifier = generationService.generate(namespace);
        }

        String version = request.version();
        if (version == null || version.isBlank()) {
            version = "1.0.0";
        }
        SemVerValidator.validate(version);

        if (scopedIdentifierRepository.existsByNamespaceIdAndIdentifier(namespace.getId(), identifier)) {
            throw new BusinessException("Identifier already exists in namespace: " + identifier);
        }

        ScopedIdentifier entity = new ScopedIdentifier();
        entity.setIdentifier(identifier);
        entity.setVersion(version);
        entity.setVersionStatus(VersionStatus.DRAFT);
        entity.setNamespace(namespace);
        entity.setIdentifiedItem(identifiedItem);
        entity.setCreatedAt(LocalDateTime.now());

        namespaceConstraintValidator.validateScopedIdentifier(namespace.getId(), entity, null);

        try {
            ScopedIdentifier saved = scopedIdentifierRepository.save(entity);
            recordHistory(saved, version, VersionStatus.DRAFT, "Created: " + (request.changeSummary() != null ? request.changeSummary() : "Initial version"));
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("Identifier already exists in namespace (concurrent conflict): " + identifier);
        }
    }

    public ScopedIdentifierResponse update(UUID id, ScopedIdentifierCreateRequest request) {
        ScopedIdentifier entity = loadOrThrow(id);

        // identifier is immutable
        if (request.version() != null && !request.version().isBlank()
                && !request.version().equals(entity.getVersion())) {
            SemVerValidator.validate(request.version());
            entity.setVersion(request.version());
        }

        if (request.namespaceId() != null
                && !request.namespaceId().equals(entity.getNamespace().getId())) {
            Namespace ns = namespaceRepository.findById(request.namespaceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Namespace", request.namespaceId()));
            entity.setNamespace(ns);
        }

        if (request.identifiedItemId() != null
                && !request.identifiedItemId().equals(entity.getIdentifiedItem().getId())) {
            IdentifiedItem ii = identifiedItemRepository.findById(request.identifiedItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("IdentifiedItem", request.identifiedItemId()));
            entity.setIdentifiedItem(ii);
        }

        namespaceConstraintValidator.validateScopedIdentifier(
                entity.getNamespace().getId(), entity, entity.getIdentifiedItem().getId());

        ScopedIdentifier saved = scopedIdentifierRepository.save(entity);
        return toResponse(saved);
    }

    public ScopedIdentifierResponse transition(UUID id, VersionTransitionRequest request) {
        ScopedIdentifier entity = loadOrThrow(id);
        VersionStatus current = entity.getVersionStatus();
        VersionStatus target = request.toStatus();

        validateTransition(current, target);

        entity.setVersionStatus(target);
        LocalDateTime now = LocalDateTime.now();
        switch (target) {
            case PUBLISHED -> entity.setPublishedAt(now);
            case DEPRECATED -> entity.setDeprecatedAt(now);
            case OBSOLETE -> entity.setObsoleteAt(now);
            default -> { /* no-op */ }
        }

        ScopedIdentifier saved = scopedIdentifierRepository.save(entity);
        recordHistory(saved, saved.getVersion(), target, request.changeSummary());
        return toResponse(saved);
    }

    public void delete(UUID id) {
        if (!scopedIdentifierRepository.existsById(id)) {
            throw new ResourceNotFoundException("ScopedIdentifier", id);
        }
        scopedIdentifierRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<IdentifierVersionHistoryResponse> listHistory(UUID scopedIdentifierId) {
        return historyRepository.findByScopedIdentifierIdOrderByTimestampDesc(scopedIdentifierId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    private ScopedIdentifier loadOrThrow(UUID id) {
        return scopedIdentifierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ScopedIdentifier", id));
    }

    private void validateTransition(VersionStatus current, VersionStatus target) {
        boolean valid = switch (current) {
            case DRAFT -> target == VersionStatus.PUBLISHED || target == VersionStatus.OBSOLETE;
            case PUBLISHED -> target == VersionStatus.DEPRECATED || target == VersionStatus.OBSOLETE;
            case DEPRECATED -> target == VersionStatus.OBSOLETE;
            case OBSOLETE -> false;
        };
        if (!valid) {
            throw new BusinessException(
                    "Invalid version transition from " + current + " to " + target);
        }
    }

    private void recordHistory(ScopedIdentifier si, String version,
                               VersionStatus status, String summary) {
        IdentifierVersionHistory history = new IdentifierVersionHistory(
                si, version, status, "system", summary);
        historyRepository.save(history);
    }

    private ScopedIdentifierResponse toResponse(ScopedIdentifier si) {
        Namespace ns = si.getNamespace();
        IdentifiedItem ii = si.getIdentifiedItem();

        UUID identifiedItemId = null;
        String identifiedItemType = null;
        String identifiedItemLabel = null;
        if (ii != null) {
            identifiedItemId = ii.getId();
            Object unproxied = Hibernate.unproxy(ii);
            identifiedItemType = unproxied.getClass().getSimpleName();
            if (ii.getItem() != null) {
                identifiedItemLabel = ItemNamingHelper.preferredDesignation(ii.getItem());
            }
        }

        return new ScopedIdentifierResponse(
                si.getId(),
                ns == null ? null : ns.getId(),
                ns == null ? null : ItemNamingHelper.preferredDesignation(ns),
                si.getIdentifier(),
                si.getVersion(),
                si.getVersionStatus(),
                identifiedItemId,
                identifiedItemType,
                identifiedItemLabel,
                si.getCreatedAt(),
                si.getPublishedAt(),
                si.getDeprecatedAt(),
                si.getObsoleteAt()
        );
    }

    private IdentifierVersionHistoryResponse toHistoryResponse(IdentifierVersionHistory h) {
        return new IdentifierVersionHistoryResponse(
                h.getId(),
                h.getScopedIdentifier().getId(),
                h.getVersion(),
                h.getVersionStatus(),
                h.getOperator(),
                h.getTimestamp(),
                h.getChangeSummary()
        );
    }
}
