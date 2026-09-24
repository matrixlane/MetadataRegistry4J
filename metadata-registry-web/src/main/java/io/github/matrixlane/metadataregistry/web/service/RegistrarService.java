package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registrar.RegistrarRequest;
import io.github.matrixlane.metadataregistry.web.dto.registrar.RegistrarResponse;
import io.github.matrixlane.metadataregistry.web.repository.IndividualRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.repository.RoleRepository;
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
    private final AccountRepository accountRepository;
    private final OrganizationRepository organizationRepository;
    private final IndividualRepository individualRepository;
    private final ContactAccountBindingService contactAccountBindingService;

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
        Registrar saved = registrarRepository.save(entity);
        applyAccountBinding(saved.getId(), request.userId());
        return toResponse(saved);
    }

    public RegistrarResponse update(UUID id, RegistrarRequest request) {
        Registrar entity = loadOrThrow(id);
        applyRequest(entity, request);
        Registrar saved = registrarRepository.save(entity);
        applyAccountBinding(saved.getId(), request.userId());
        return toResponse(saved);
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

    /**
     * 账号绑定写入卫星实体(扩展关联已剥离出标准实体 Contact)。
     * userId 非空时 upsert 绑定,为空时解绑(语义同字段时代 setAccount(null))。
     */
    private void applyAccountBinding(UUID registrarId, java.util.UUID userId) {
        if (userId != null) {
            Account account = accountRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
            contactAccountBindingService.bind(registrarId, account.getId());
        } else {
            contactAccountBindingService.unbind(registrarId);
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
                contactAccountBindingService.accountIdOfContact(entity.getId()).orElse(null),
                entity.getOrganization() != null ? entity.getOrganization().getId() : null,
                entity.getOrganization() != null
                        ? entity.getOrganization().getName().stream().findFirst().map(n -> n.getValue()).orElse(null)
                        : null,
                entity.getIndividual() != null ? entity.getIndividual().getId() : null,
                entity.getIndividual() != null ? entity.getIndividual().getName().getValue() : null);
    }

    public Registrar createRegistrarForUser(UUID accountId, String username, String displayName, String email, RegistrationAuthority ra) {
        // 1. Get current Account
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        // 2. Create Registrar (no Individual generated yet!)
        // 有意不盖任何 Role：Registrar 身份由 Contact 子类（Registrar 实例）确定，
        // Role 仅为描述性元数据，由用户后续自行创建并关联（见 add-submitter-contact-identity）。
        Registrar registrar = new Registrar();
        registrar.setIdentifier(username + "-registrar");
        registrar.setIndividual(null); // Keep individual empty initially!
        registrar.setRole(null);
        registrar.setRegistrationAuthority(ra);
        registrar.setOrganization(ra); // RegistrationAuthority extends Organization
        Registrar saved = registrarRepository.save(registrar);
        // 账号绑定写入卫星实体(扩展关联已剥离出标准实体 Contact)
        contactAccountBindingService.bind(saved.getId(), account.getId());
        return saved;
    }
}
