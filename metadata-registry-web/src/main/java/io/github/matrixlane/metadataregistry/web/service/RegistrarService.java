package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
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
    private final RoleRepository roleRepository;
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

        // RA：null 时保留现有（编辑表单通常不动 RA）
        if (request.registrationAuthorityId() != null) {
            RegistrationAuthority ra = registrationAuthorityRepository.findById(request.registrationAuthorityId())
                    .orElseThrow(() -> new ResourceNotFoundException("RegistrationAuthority", request.registrationAuthorityId()));
            entity.setRegistrationAuthority(ra);
        }

        // 组织：必填（RegistrarRequest 校验），按 id 解析
        Organization org = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", request.organizationId()));
        entity.setOrganization(org);

        // Individual 三种情形：
        //   1) individualId 非空 → 改绑到指定 Individual（按 individualName 可选地更新其 name）
        //   2) individualId 为空 + individualName 非空 → 创建新 Individual 并绑定
        //   3) individualId 为空 + individualName 为空 → 保留现有 Individual（不清空）
        String requestedName = request.individualName() == null ? null : request.individualName().trim();
        if (request.individualId() != null) {
            Individual ind = individualRepository.findById(request.individualId())
                    .orElseThrow(() -> new ResourceNotFoundException("Individual", request.individualId()));
            if (!requestedName.isEmpty()) {
                ind.getName().clear();
                ind.getName().add(Sign.of(requestedName));
                individualRepository.save(ind);
            }
            entity.setIndividual(ind);
        } else if (requestedName != null && !requestedName.isEmpty()) {
            Individual created = new Individual();
            created.getName().add(Sign.of(requestedName));
            entity.setIndividual(individualRepository.save(created));
        }
        // else：保留 entity.getIndividual()

        // 角色：可空（角色可清空）；按 id 解析或 setRole(null)
        if (request.roleId() != null) {
            Role role = roleRepository.findById(request.roleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Role", request.roleId()));
            entity.setRole(role);
        } else {
            entity.setRole(null);
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
                entity.getIndividual() != null ? entity.getIndividual().getName().getValue() : null,
                entity.getRole() != null ? entity.getRole().getId() : null,
                entity.getRole() != null ? entity.getRole().getRoleName() : null);
    }

    public Registrar createRegistrarForUser(UUID accountId, String username, String individualName, String roleName, UUID organizationId, RegistrationAuthority ra) {
        // 1. Get current Account
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        // 2. Optional Individual — created only when an explicit name is provided
        //    (init wizard passes the user-provided name; empty = no Individual).
        Individual individual = null;
        if (individualName != null && !individualName.isBlank()) {
            individual = new Individual();
            individual.setName(Sign.of(individualName.trim()));
            individual.setIdentifier(account.getUsername());
            individual = individualRepository.save(individual);
        }

        // 3. Role — required, by roleName uniqueness. If a Role with the same
        //    roleName already exists (case-insensitive), reuse it; otherwise create.
        if (roleName == null || roleName.isBlank()) {
            throw new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                    io.github.matrixlane.metadataregistry.web.common.ApiCodes.VALIDATION_FAILED,
                    "注册管理员角色不能为空");
        }
        Role role = roleRepository.findByRoleNameIgnoreCase(roleName.trim())
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setRoleName(roleName.trim());
                    return roleRepository.save(r);
                });

        // 4. Organization — explicit id wins; otherwise fall back to the
        //    registration authority (which extends Organization).
        Organization organization = organizationId != null
                ? organizationRepository.findById(organizationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId))
                : ra;

        // 5. Build the Registrar with all three elements wired up.
        Registrar registrar = new Registrar();
        registrar.setIdentifier(username + "-registrar");
        registrar.setIndividual(individual);
        registrar.setRole(role);
        registrar.setRegistrationAuthority(ra);
        registrar.setOrganization(organization);
        Registrar saved = registrarRepository.save(registrar);
        // 账号绑定写入卫星实体(扩展关联已剥离出标准实体 Contact)
        contactAccountBindingService.bind(saved.getId(), account.getId());
        return saved;
    }
}
