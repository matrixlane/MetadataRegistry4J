package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.VersionStatus;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.scopedidentifier.ScopedIdentifierResponse;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves business identifiers to registry entries and provides reverse lookup.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdentifierResolutionService {

    private final ScopedIdentifierRepository scopedIdentifierRepository;

    /**
     * Resolves an identifier within a specific namespace and optional version.
     *
     * @return the resolved identifier, or empty if not found
     */
    public Optional<ScopedIdentifierResponse> resolve(UUID namespaceId,
                                                      String identifier,
                                                      String version) {
        Optional<ScopedIdentifier> result;
        if (version != null && !version.isBlank()) {
            result = scopedIdentifierRepository.findByNamespaceIdAndIdentifierAndVersion(
                    namespaceId, identifier, version);
        } else {
            result = scopedIdentifierRepository.findByNamespaceIdAndIdentifier(namespaceId, identifier);
        }
        return result.map(this::toResponse);
    }

    /**
     * Resolves an identifier across all namespaces (returns latest match).
     */
    public Optional<ScopedIdentifierResponse> resolveGlobally(String identifier, String version) {
        List<ScopedIdentifier> matches = scopedIdentifierRepository.findByIdentifier(identifier);
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        ScopedIdentifier selected;
        if (version != null && !version.isBlank()) {
            selected = matches.stream()
                    .filter(si -> version.equals(si.getVersion()))
                    .findFirst()
                    .orElse(null);
        } else {
            selected = matches.stream()
                    .filter(si -> si.getVersionStatus() != VersionStatus.OBSOLETE)
                    .max(Comparator.comparing(ScopedIdentifier::getCreatedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(matches.get(0));
        }

        if (selected == null) {
            return Optional.empty();
        }
        return Optional.of(toResponse(selected));
    }

    /**
     * Reverse lookup: find all identifiers associated with an IdentifiedItem.
     */
    public List<ScopedIdentifierResponse> findByIdentifiedItem(UUID identifiedItemId) {
        return scopedIdentifierRepository.findByIdentifiedItemId(identifiedItemId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ScopedIdentifierResponse toResponse(ScopedIdentifier si) {
        var ns = si.getNamespace();
        var ii = si.getIdentifiedItem();

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
                si.getObsoleteAt(),
                si.getIdentifierCreationMode(),
                si.getVersionCreationMode()
        );
    }
}