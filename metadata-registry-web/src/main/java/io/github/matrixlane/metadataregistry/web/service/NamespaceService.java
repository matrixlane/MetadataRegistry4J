package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceRequest;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceResponse;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NamespaceService {

    private final NamespaceRepository repository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final ItemAdministrationService itemAdministrationService;

    @Transactional(readOnly = true)
    public Page<NamespaceResponse> list(String keyword, Pageable pageable) {
        Specification<Namespace> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> {
                if (q != null) q.distinct(true);
                var d = root.join("designations", jakarta.persistence.criteria.JoinType.LEFT);
                return cb.like(cb.lower(d.get("sign").get("value")), like);
            });
        }
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public NamespaceResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public NamespaceResponse create(NamespaceRequest request) {
        Namespace entity = new Namespace();
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public NamespaceResponse update(UUID id, NamespaceRequest request) {
        Namespace entity = loadOrThrow(id);
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Namespace", id);
        }
        repository.deleteById(id);
    }

    private Namespace loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Namespace", id));
    }

    private void applyRequest(Namespace entity, NamespaceRequest request) {
        if (request.namingAuthorityId() != null) {
            RegistrationAuthority ra = registrationAuthorityRepository.findById(request.namingAuthorityId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "RegistrationAuthority", request.namingAuthorityId()));
            entity.setRegistrationAuthority(ra);
        } else {
            entity.setRegistrationAuthority(null);
        }
        itemAdministrationService.applyDesignations(entity, request.designations());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(entity, request.registration());
        }
    }

    private NamespaceResponse toResponse(Namespace entity) {
        RegistrationAuthority ra = entity.getRegistrationAuthority();
        String raName = null;
        if (ra != null && !ra.getName().isEmpty()) {
            raName = ra.getName().get(0).getValue();
        }
        return new NamespaceResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                ra == null ? null : ra.getId(),
                raName,
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }

    @SuppressWarnings("unused")
    private static String firstName(Sign sign) {
        return sign == null ? null : sign.getValue();
    }
}
