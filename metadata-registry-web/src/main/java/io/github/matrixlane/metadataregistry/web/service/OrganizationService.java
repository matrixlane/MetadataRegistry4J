package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.EmailAddress;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PostalAddress;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.organization.OrganizationRequest;
import io.github.matrixlane.metadataregistry.web.dto.organization.OrganizationResponse;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationService {

    private final OrganizationRepository repository;

    @Transactional(readOnly = true)
    public Page<OrganizationResponse> list(String keyword, Pageable pageable) {
        Specification<Organization> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> {
                var nameJoin = root.join("name", jakarta.persistence.criteria.JoinType.LEFT);
                if (q != null) {
                    q.distinct(true);
                }
                return cb.like(cb.lower(nameJoin.get("value")), like);
            });
        }
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public OrganizationResponse create(OrganizationRequest request) {
        Organization entity = new Organization();
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public OrganizationResponse update(UUID id, OrganizationRequest request) {
        Organization entity = loadOrThrow(id);
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Organization", id);
        }
        repository.deleteById(id);
    }

    private Organization loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
    }

    private void applyRequest(Organization entity, OrganizationRequest request) {
        entity.getName().clear();
        entity.getName().add(Sign.of(request.name()));

        entity.setMailAddress(request.mailAddress() == null
                ? new PostalAddress()
                : PostalAddress.of(request.mailAddress()));

        entity.getEmailAddress().clear();
        if (request.emailAddress() != null) {
            for (String e : request.emailAddress()) {
                entity.getEmailAddress().add(EmailAddress.of(e));
            }
        }

        entity.getPhoneNumber().clear();
        if (request.phoneNumber() != null) {
            for (String p : request.phoneNumber()) {
                entity.getPhoneNumber().add(PhoneNumber.of(p));
            }
        }

        entity.setWebAddress(request.webAddress());
    }

    private OrganizationResponse toResponse(Organization entity) {
        List<String> names = entity.getName().stream().map(Sign::getValue).toList();
        String first = names.isEmpty() ? null : names.get(0);
        List<String> additional = names.size() <= 1 ? List.of() : new ArrayList<>(names.subList(1, names.size()));
        return new OrganizationResponse(
                entity.getId(),
                first,
                additional,
                entity.getMailAddress() == null ? null : entity.getMailAddress().getValue(),
                entity.getEmailAddress().stream().map(EmailAddress::getValue).toList(),
                entity.getPhoneNumber().stream().map(PhoneNumber::getValue).toList(),
                entity.getWebAddress());
    }
}
