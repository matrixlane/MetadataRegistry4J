
package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.namingconvention.NamingConventionRequest;
import io.github.matrixlane.metadataregistry.web.dto.namingconvention.NamingConventionResponse;
import io.github.matrixlane.metadataregistry.web.repository.NamingConventionRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NamingConventionService {

    private final NamingConventionRepository repository;
    private final ItemAdministrationService itemAdministrationService;

    @Transactional(readOnly = true)
    public Page<NamingConventionResponse> list(String keyword, Pageable pageable) {
        Specification<NamingConvention> spec = byKeyword(keyword);
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public NamingConventionResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public NamingConventionResponse create(NamingConventionRequest request) {
        NamingConvention entity = new NamingConvention();
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public NamingConventionResponse update(UUID id, NamingConventionRequest request) {
        NamingConvention entity = loadOrThrow(id);
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("NamingConvention", id);
        }
        repository.deleteById(id);
    }

    private void applyRequest(NamingConvention entity, NamingConventionRequest request) {
        entity.setScopeRule(Text.of(request.scopeRule()));
        if (StringUtils.hasText(request.authorityRule())) {
            entity.setAuthorityRule(Text.of(request.authorityRule()));
        } else {
            entity.setAuthorityRule(null);
        }
        itemAdministrationService.applyDesignations(entity, request.designations());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(entity, request.registration());
        }
    }

    private NamingConvention loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NamingConvention", id));
    }

    private static Specification<NamingConvention> byKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Specification.where(null);
        }
        String like = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> {
            if (query != null) query.distinct(true);
            var sub = query.subquery(Long.class);
            var designation = sub.from(
                    io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation.class);
            sub.select(cb.literal(1L));
            sub.where(cb.and(
                    cb.equal(designation.get("item").get("id"), root.get("id")),
                    cb.like(cb.lower(designation.get("sign").get("value")), like)));
            return cb.exists(sub);
        };
    }

    private NamingConventionResponse toResponse(NamingConvention entity) {
        return new NamingConventionResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                entity.getScopeRule() == null ? null : entity.getScopeRule().getValue(),
                entity.getAuthorityRule() == null ? null : entity.getAuthorityRule().getValue(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }
}
