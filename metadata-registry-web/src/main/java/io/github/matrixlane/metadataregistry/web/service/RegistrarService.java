package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.User;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registrar.RegistrarRequest;
import io.github.matrixlane.metadataregistry.web.dto.registrar.RegistrarResponse;
import io.github.matrixlane.metadataregistry.web.repository.IndividualRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.UserRepository;
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
public class RegistrarService {

    private final RegistrarRepository registrarRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final IndividualRepository individualRepository;

    @Transactional(readOnly = true)
    public Page<RegistrarResponse> list(String keyword, Pageable pageable) {
        Specification<Registrar> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("identifier")), like));
        }
        return registrarRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RegistrarResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public RegistrarResponse create(RegistrarRequest request) {
        Registrar entity = new Registrar();
        applyRequest(entity, request);
        return toResponse(registrarRepository.save(entity));
    }

    public RegistrarResponse update(UUID id, RegistrarRequest request) {
        Registrar entity = loadOrThrow(id);
        applyRequest(entity, request);
        return toResponse(registrarRepository.save(entity));
    }

    public void delete(UUID id) {
        if (!registrarRepository.existsById(id)) {
            throw new ResourceNotFoundException("Registrar", id);
        }
        registrarRepository.deleteById(id);
    }

    private Registrar loadOrThrow(UUID id) {
        return registrarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Registrar", id));
    }

    private void applyRequest(Registrar entity, RegistrarRequest request) {
        entity.setIdentifier(request.identifier());

        if (request.registrationAuthorityId() != null) {
            RegistrationAuthority ra = registrationAuthorityRepository.findById(request.registrationAuthorityId())
                    .orElseThrow(() -> new ResourceNotFoundException("RegistrationAuthority", request.registrationAuthorityId()));
            entity.setRegistrationAuthority(ra);
        } else {
            entity.setRegistrationAuthority(null);
        }

        if (request.userId() != null) {
            User user = userRepository.findById(request.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", request.userId()));
            entity.setUser(user);
        } else {
            entity.setUser(null);
        }

        if (request.organizationId() != null) {
            Organization org = organizationRepository.findById(request.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", request.organizationId()));
            entity.setOrganization(org);
        } else {
            entity.setOrganization(null);
        }

        if (request.individualId() != null) {
            Individual ind = individualRepository.findById(request.individualId())
                    .orElseThrow(() -> new ResourceNotFoundException("Individual", request.individualId()));
            entity.setIndividual(ind);
        } else {
            entity.setIndividual(null);
        }
    }

    private RegistrarResponse toResponse(Registrar entity) {
        return new RegistrarResponse(
                entity.getId(),
                entity.getIdentifier(),
                entity.getRegistrationAuthority() != null ? entity.getRegistrationAuthority().getId() : null,
                entity.getRegistrationAuthority() != null
                        ? entity.getRegistrationAuthority().getName().stream().findFirst().map(n -> n.getValue()).orElse(null)
                        : null,
                entity.getUser() != null ? entity.getUser().getId() : null,
                entity.getOrganization() != null ? entity.getOrganization().getId() : null,
                entity.getOrganization() != null
                        ? entity.getOrganization().getName().stream().findFirst().map(n -> n.getValue()).orElse(null)
                        : null,
                entity.getIndividual() != null ? entity.getIndividual().getId() : null,
                entity.getIndividual() != null ? entity.getIndividual().getName().getValue() : null);
    }
}
