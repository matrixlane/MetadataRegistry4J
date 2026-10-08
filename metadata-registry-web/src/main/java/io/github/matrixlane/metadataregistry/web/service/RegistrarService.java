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
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
    private final ContactAccountService contactAccountService;

    @Transactional(readOnly = true)
    public Page<RegistrarResponse> list(String keyword, Pageable pageable) {
        Specification<Registrar> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            spec = spec.and(matchesKeyword(keyword));
        }
        return registrarRepository.findAll(spec, pageable).map(this::toResponse);
    }

    /**
     * 关键词按 Contact §6.3.5 的三要素检索：个人姓名、角色名、组织名。
     * 三要素均可空，故个人/角色用 LEFT JOIN（默认内连接会把缺要素的 Registrar 整行滤掉）。
     * 组织名是 {@code @ElementCollection}，join 会让同一 Registrar 按名称条数重复出现，
     * 故走 exists 子查询，不改变结果基数。
     */
    private Specification<Registrar> matchesKeyword(String keyword) {
        String like = "%" + keyword.toLowerCase() + "%";
        return (root, q, cb) -> {
            Join<?, Individual> individual = root.join("individual", JoinType.LEFT);
            Join<?, Role> role = root.join("role", JoinType.LEFT);
            Subquery<UUID> orgNameMatch = q.subquery(UUID.class);
            Root<Organization> organization = orgNameMatch.from(Organization.class);
            orgNameMatch.select(organization.get("id"))
                    .where(cb.equal(organization.get("id"), root.get("organization").get("id")),
                            cb.like(cb.lower(organization.join("name").get("value")), like));
            return cb.or(
                    cb.like(cb.lower(individual.get("name").get("value")), like),
                    cb.like(cb.lower(role.get("roleName")), like),
                    cb.exists(orgNameMatch));
        };
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
        // 注意：Individual.name 是单个 Sign（@Embedded），不是 List<Sign>。
        String requestedName = request.individualName() == null ? null : request.individualName().trim();
        if (request.individualId() != null) {
            Individual ind = individualRepository.findById(request.individualId())
                    .orElseThrow(() -> new ResourceNotFoundException("Individual", request.individualId()));
            if (requestedName != null && !requestedName.isEmpty()) {
                ind.setName(Sign.of(requestedName));
                individualRepository.save(ind);
            }
            entity.setIndividual(ind);
        } else if (requestedName != null && !requestedName.isEmpty()) {
            Individual created = new Individual();
            created.setName(Sign.of(requestedName));
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
            contactAccountService.bind(registrarId, account.getId());
        } else {
            contactAccountService.unbind(registrarId);
        }
    }

    private RegistrarResponse toResponse(Registrar entity) {
        return new RegistrarResponse(
                entity.getId(),
                entity.getRegistrationAuthority() != null ? entity.getRegistrationAuthority().getId() : null,
                entity.getRegistrationAuthority() != null
                        ? entity.getRegistrationAuthority().getName().stream().findFirst().map(n -> n.getValue()).orElse(null)
                        : null,
                contactAccountService.accountIdOfContact(entity.getId()).orElse(null),
                entity.getOrganization() != null ? entity.getOrganization().getId() : null,
                entity.getOrganization() != null
                        ? entity.getOrganization().getName().stream().findFirst().map(n -> n.getValue()).orElse(null)
                        : null,
                entity.getIndividual() != null ? entity.getIndividual().getId() : null,
                entity.getIndividual() != null ? entity.getIndividual().getName().getValue() : null,
                entity.getRole() != null ? entity.getRole().getId() : null,
                entity.getRole() != null ? entity.getRole().getRoleName() : null);
    }

    /**
     * 初始化向导为管理员账号建立 Registrar 身份。
     *
     * <p>{@code username} 此前只用于生成非标准的 {@code {username}-registrar} 标识符，
     * 该列已按 ISO/IEC 11179-3:2023 §6.3.5 移除；参数保留是为避免改动既有调用点。
     *
     * <p>Individual 解析两级：显式 {@code individualName}（初始化向导传入用户填写姓名）
     * 非空时新建并挂到 Account；为空时回退复用 {@code account.getIndividual()}——注册机构
     * 创建页自动建立 Registrar（PERSONAL 自动 / TEAM selfAsRegistrar）走此级，使该机构
     * 默认注册人员直接关联当前用户自己，与首个机构向导创建的 Registrar 同人。</p>
     */
    public Registrar createRegistrarForUser(UUID accountId, String username, String individualName, String roleName, UUID organizationId, RegistrationAuthority ra) {
        // 1. Get current Account
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        // 2. Optional Individual — explicit name wins (init wizard passes the
        //    user-provided name); blank falls back to the account's own Individual
        //    so an auto-created Registrar is linked to the current user.
        Individual individual = null;
        if (individualName != null && !individualName.isBlank()) {
            individual = new Individual();
            individual.setName(Sign.of(individualName.trim()));
            individual = individualRepository.save(individual);
            // 该 Individual 即 admin 本人的个人信息：同时挂到 Account，"我的"首次
            // 打开即显示并可就地更新，避免后续保存时再产生第二个 Individual。
            account.setIndividual(individual);
            accountRepository.save(account);
        } else if (account.getIndividual() != null) {
            individual = account.getIndividual();
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
        registrar.setIndividual(individual);
        registrar.setRole(role);
        registrar.setRegistrationAuthority(ra);
        registrar.setOrganization(organization);
        Registrar saved = registrarRepository.save(registrar);
        // 账号绑定写入卫星实体(扩展关联已剥离出标准实体 Contact)
        contactAccountService.bind(saved.getId(), account.getId());
        return saved;
    }
}
