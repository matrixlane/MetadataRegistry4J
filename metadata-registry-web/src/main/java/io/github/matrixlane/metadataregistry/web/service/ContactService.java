package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.contact.ContactRequest;
import io.github.matrixlane.metadataregistry.web.dto.contact.ContactResponse;
import io.github.matrixlane.metadataregistry.web.dto.contact.ContactBatchRequest;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.IndividualRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RoleRepository;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
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
public class ContactService {

    private final ContactRepository contactRepository;
    private final OrganizationRepository organizationRepository;
    private final IndividualRepository individualRepository;
    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final ContactAccountBindingService contactAccountBindingService;

    @Transactional(readOnly = true)
    public Page<ContactResponse> list(String keyword, Pageable pageable) {
        Specification<Contact> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> {
                if (q != null) q.distinct(true);
                var orgJoin = root.join("organization", jakarta.persistence.criteria.JoinType.LEFT);
                var nameJoin = orgJoin.join("name", jakarta.persistence.criteria.JoinType.LEFT);
                return cb.like(cb.lower(nameJoin.get("value")), like);
            });
        }
        return contactRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ContactResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public ContactResponse create(ContactRequest request) {
        Contact entity = new Contact();
        applyRequest(entity, request);
        return toResponse(contactRepository.save(entity));
    }

    public ContactResponse update(UUID id, ContactRequest request) {
        Contact entity = loadOrThrow(id);
        applyRequest(entity, request);
        return toResponse(contactRepository.save(entity));
    }

    public void delete(UUID id) {
        if (!contactRepository.existsById(id)) {
            throw new ResourceNotFoundException("Contact", id);
        }
        contactRepository.deleteById(id);
    }

    private Contact loadOrThrow(UUID id) {
        return contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", id));
    }

    private void applyRequest(Contact entity, ContactRequest request) {
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

        if (request.roleId() != null) {
            Role role = roleRepository.findById(request.roleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Role", request.roleId()));
            entity.setRole(role);
        } else {
            entity.setRole(null);
        }
    }

    private ContactResponse toResponse(Contact entity) {
        return new ContactResponse(
                entity.getId(),
                entity.getOrganization() != null ? entity.getOrganization().getId() : null,
                entity.getOrganization() != null
                        ? entity.getOrganization().getName().stream().findFirst().map(n -> n.getValue()).orElse(null)
                        : null,
                entity.getIndividual() != null ? entity.getIndividual().getId() : null,
                entity.getIndividual() != null ? entity.getIndividual().getName().getValue() : null,
                entity.getRole() != null ? entity.getRole().getId() : null,
                entity.getRole() != null ? entity.getRole().getRoleName() : null);
    }

    public void batchConfigure(ContactBatchRequest request) {
        Organization org = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", request.organizationId()));

        Role role = roleRepository.findByRoleName(request.roleName())
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setRoleName(request.roleName());
                    return roleRepository.save(r);
                });

        for (UUID accountId : request.appUserIds()) {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

            // Create and save Contact linking to the Account's Individual (if exists);
            // 账号绑定写入卫星实体(扩展关联已剥离出标准实体 Contact)
            Contact contact = new Contact();
            contact.setIndividual(account.getIndividual());
            contact.setOrganization(org);
            contact.setRole(role);
            Contact saved = contactRepository.save(contact);
            contactAccountBindingService.bind(saved.getId(), account.getId());
        }
    }
}
