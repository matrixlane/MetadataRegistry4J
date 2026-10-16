package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.contact.ContactIdentityKind;
import io.github.matrixlane.metadataregistry.web.dto.contact.ContactRequest;
import io.github.matrixlane.metadataregistry.web.dto.contact.ContactResponse;
import io.github.matrixlane.metadataregistry.web.dto.contact.ContactBatchRequest;
import io.github.matrixlane.metadataregistry.web.dto.contact.MyContactRequest;
import io.github.matrixlane.metadataregistry.web.dto.contact.MyContactResponse;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ContactAccountService contactAccountService;
    private final SystemSettingService systemSettingService;
    private final io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository registrationAuthorityRepository;
    private final io.github.matrixlane.metadataregistry.web.repository.SubmissionRecordRepository submissionRecordRepository;
    private final io.github.matrixlane.metadataregistry.web.repository.StewardshipRecordRepository stewardshipRecordRepository;
    private final io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionApprovalRepository registrationTransitionApprovalRepository;

    /**
     * 联系人身份列表。关键词按所属组织名检索。
     *
     * <p>维持仅组织名匹配：Contact 是独立根实体（{@code Contact extends Entity}），
     * 不在受管链上，不存在 Scoped_Identifier 行，故无标识符可匹配。</p>
     */
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
        applyOrganizationAndRole(entity, request.organizationId(), request.roleId());
        if (request.individualId() != null) {
            Individual ind = individualRepository.findById(request.individualId())
                    .orElseThrow(() -> new ResourceNotFoundException("Individual", request.individualId()));
            entity.setIndividual(ind);
        } else {
            entity.setIndividual(null);
        }
    }

    private void applyRequest(Contact entity, MyContactRequest request) {
        applyOrganizationAndRole(entity, request.organizationId(), request.roleId());
        if (request.individualId() != null) {
            Individual ind = individualRepository.findById(request.individualId())
                    .orElseThrow(() -> new ResourceNotFoundException("Individual", request.individualId()));
            entity.setIndividual(ind);
        }
        // individualId 缺省保留现有 contact.individual 不变；自建路径由 createMyContact 回填账号自身 Individual
        // Role 仅为描述性元数据：Registrar 身份由 Contact 子类 Registrar 实例确定
        // （类型即身份，ISO/IEC 11179-3），自选角色不产生任何权限效应。
    }

    private void applyOrganizationAndRole(Contact entity, UUID organizationId, UUID roleId) {
        if (organizationId != null) {
            Organization org = organizationRepository.findById(organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
            entity.setOrganization(org);
        } else {
            entity.setOrganization(null);
        }

        if (roleId != null) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
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

    /**
     * 提交信息默认上下文（personal-no-login-registrar-default）：优先取账号默认
     * 注册人员的联系人身份及其注册机构；无默认注册人员时回退到绑定列表中最早绑定
     * （contactId 最小）的联系人及其所属组织；无任何绑定时返回空。
     */
    @Transactional(readOnly = true)
    public Optional<SubmissionContext> resolveDefaultSubmissionContext(UUID accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        Optional<Registrar> defaultRegistrar = contactAccountService.defaultRegistrarOf(accountId)
                .filter(r -> r.getRegistrationAuthority() != null);
        if (defaultRegistrar.isPresent()) {
            Registrar registrar = defaultRegistrar.get();
            return Optional.of(new SubmissionContext(registrar, registrar.getRegistrationAuthority()));
        }
        return contactAccountService.boundContactIdsOfAccount(accountId).stream()
                .findFirst()
                .flatMap(contactRepository::findById)
                .map(c -> new SubmissionContext(c, c.getOrganization()));
    }

    /** 提交信息默认上下文：默认联系人 + 其所属组织（组织可为空）。 */
    public record SubmissionContext(Contact contact, Organization organization) {
    }

    // ===== "我的"联系人身份（add-submitter-contact-identity） =====

    /** 当前账号绑定的全部联系人身份（含 Registrar 行）；按绑定顺序（contact_id 升序）排列。 */
    @Transactional(readOnly = true)
    public List<MyContactResponse> listMyContacts(UUID accountId) {
        List<UUID> contactIds = contactAccountService.boundContactIdsOfAccount(accountId);
        Map<UUID, Contact> byId = new HashMap<>();
        contactRepository.findAllById(contactIds).forEach(c -> byId.put(c.getId(), c));
        // 一次性取默认注册人员 id，避免逐行 isDefaultRegistrar 查询（N+1）
        Optional<UUID> defaultRegistrarId = contactAccountService.defaultRegistrarIdOf(accountId);
        List<MyContactResponse> result = new ArrayList<>();
        for (UUID contactId : contactIds) {
            Contact c = byId.get(contactId);
            if (c != null) {
                result.add(toMyResponse(c,
                        defaultRegistrarId.map(id -> id.equals(contactId)).orElse(false)));
            }
        }
        return result;
    }

    /**
     * 为当前账号自建联系人身份；账号的首个 Registrar 身份自动设为默认注册人员。
     *
     * <p>kind=REGISTRAR 时创建 Registrar 实体（类型即身份，ISO/IEC 11179-3），两版本一致：
     * 仅超级管理员可创建注册人员身份，其他账号一律拒绝；kind 缺省按 CONTACT 处理。
     *
     * <p>REGISTRAR 归属（M2）：所选 organization 即注册机构时回填
     * {@code Registrar.registrationAuthority}，避免产生 registration_authority 为 null 的
     * 僵尸身份；非注册机构的普通组织留空（合法状态，由 Registrar 管理流程补挂）。</p>
     */
    public MyContactResponse createMyContact(UUID accountId, MyContactRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (request.kind() == ContactIdentityKind.REGISTRAR
                && account.getSysRole() != SystemRole.SUPER_ADMIN) {
            throw new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                    io.github.matrixlane.metadataregistry.web.common.ApiCodes.VALIDATION_FAILED,
                    "仅超级管理员可创建注册人员身份");
        }
        Contact entity = request.kind() == ContactIdentityKind.REGISTRAR ? new Registrar() : new Contact();
        applyRequest(entity, request);
        if (entity instanceof Registrar registrar && request.organizationId() != null) {
            registrationAuthorityRepository.findById(request.organizationId())
                    .ifPresent(registrar::setRegistrationAuthority);
        }
        // 未指定 Individual 时回填账号自身的 Individual，便于身份列表展示姓名
        if (entity.getIndividual() == null && account.getIndividual() != null) {
            entity.setIndividual(account.getIndividual());
        }
        Contact saved = contactRepository.save(entity);
        contactAccountService.bind(saved.getId(), accountId);
        if (saved instanceof Registrar) {
            // 首个 Registrar 自动默认（已有默认时 ensure 内部 no-op）
            contactAccountService.ensureDefaultRegistrarWhenFirst(accountId, saved.getId());
        }
        return toMyResponse(saved, contactAccountService.isDefaultRegistrar(saved.getId()));
    }

    /**
     * 更新自建联系人身份。
     *
     * <p>普通（非 Registrar）行：organizationId 必填，individualId 允许为空，roleId 允许为空。
     * <p>Registrar 行：同时允许改 Role / Individual / Organization 三要素的关联；其中
     * Organization 在 Registrar 上下文中 MUST NOT 被清空（add-registrar-three-elements）。
     */
    public MyContactResponse updateMyContact(UUID accountId, UUID contactId, MyContactRequest request) {
        Contact entity = loadOwnContact(accountId, contactId);
        if (entity instanceof Registrar registrar) {
            applyRegistrarThreeElements(registrar, request);
            return toMyResponse(contactRepository.save(registrar),
                    contactAccountService.isDefaultRegistrar(contactId));
        }
        applyRequest(entity, request);
        return toMyResponse(contactRepository.save(entity),
                contactAccountService.isDefaultRegistrar(contactId));
    }

    /**
     * Registrar 行的三要素编辑（add-registrar-three-elements）：
     * <ul>
     *   <li>roleId 允许 null（角色可清空）</li>
     *   <li>individualId 允许 null（解绑）或非空（重新绑定）</li>
     *   <li>organizationId 必填 — Registrar 必须挂在某 Organization 下</li>
     * </ul>
     */
    private void applyRegistrarThreeElements(Registrar registrar, MyContactRequest request) {
        if (request.organizationId() == null) {
            throw new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                    io.github.matrixlane.metadataregistry.web.common.ApiCodes.VALIDATION_FAILED,
                    "Registrar 行的所属组织不能为空");
        }
        Organization org = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", request.organizationId()));
        registrar.setOrganization(org);

        if (request.individualId() != null) {
            Individual ind = individualRepository.findById(request.individualId())
                    .orElseThrow(() -> new ResourceNotFoundException("Individual", request.individualId()));
            registrar.setIndividual(ind);
        } else {
            registrar.setIndividual(null);
        }

        if (request.roleId() != null) {
            Role role = roleRepository.findById(request.roleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Role", request.roleId()));
            registrar.setRole(role);
        } else {
            registrar.setRole(null);
        }
    }

    /**
     * 删除联系人身份（含仅剩的一个）。版本分支（SystemSettingService.getEdition）：
     *
     * <ul>
     *   <li>个人版（PERSONAL，含未初始化 null，按个人版保守处理）：Registrar 行
     *       已挂注册机构（registration_authority 非空）时一律拒绝删除，须先删除其注册机构；
     *       null-RA 的 Registrar 行（历史/边缘数据）允许删除，走正常解绑流程。
     *       CONTACT 行保留提交记录引用守卫。</li>
     *   <li>团队版（TEAM）：移除 SubmissionRecord / Stewardship / 审批三处引用守卫，任何身份
     *       可直接删除。</li>
     * </ul>
     *
     * <p>删除 Registrar 行相当于该账号在系统初始化时未作为 Registrar 创建：解绑账号绑定、
     * 清理 registrar 卫星行后删除实体。被删者为默认注册人员时，自动把剩余 Registrar 中的
     * 首个设为默认；删空后账号失去 Submitter 能力。
     */
    public void deleteMyContact(UUID accountId, UUID contactId) {
        Contact entity = loadOwnContact(accountId, contactId);
        boolean isRegistrar = entity instanceof Registrar;
        DeploymentEdition edition = systemSettingService.getEdition();
        if (edition != DeploymentEdition.TEAM) {
            // 个人版（或未初始化，按个人版保守处理）：已挂注册机构的注册人员身份随
            // 注册机构存在，不可单独删除。该守卫位于引用守卫之前：注册机构未删时无需再查引用。
            // null-RA 的 Registrar 行（createMyContact 选普通组织产生）允许删除（M2）。
            if (isRegistrar && ((Registrar) entity).getRegistrationAuthority() != null) {
                throw new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                        io.github.matrixlane.metadataregistry.web.common.ApiCodes.CONFLICT,
                        "该注册人员身份所属注册机构仍存在，不可删除；如确需删除请先删除该注册机构");
            }
            // 个人版 CONTACT 行：已被 SubmissionRecord 引用时拒绝删除（409 提示更友好；
            // 三方言基线无此外键约束，直接删除会静默悬挂，故须服务层守卫）
            if (submissionRecordRepository.existsByContactId(contactId)) {
                throw new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                        io.github.matrixlane.metadataregistry.web.common.ApiCodes.CONFLICT,
                        "该联系人身份已作为提交联系人被提交记录引用，不可删除");
            }
        }
        // 团队版：不做任何引用守卫，任何身份可直接删除（三方言基线无引用外键约束，
        // 引用列随之悬挂，由对应业务视图按活数据口径过滤）
        boolean wasDefaultRegistrar = isRegistrar && contactAccountService.isDefaultRegistrar(contactId);
        contactAccountService.unbind(contactId);
        if (isRegistrar) {
            // 卫星行随实体删除，默认标志一并清理（被删者即默认时本行 is_default=true）
            contactAccountService.deleteRegistrarExtension(contactId);
        }
        contactRepository.delete(entity);
        if (wasDefaultRegistrar) {
            contactAccountService.promoteFirstRegistrarToDefault(accountId);
        }
    }

    /**
     * 将指定 Registrar 身份设为当前账号的默认注册人员；普通联系人身份 MUST NOT
     * 被设为默认（默认身份概念已收窄至注册人员）。
     */
    public void setDefaultMyContact(UUID accountId, UUID contactId) {
        Contact entity = loadOwnContact(accountId, contactId);
        if (!(entity instanceof Registrar)) {
            throw new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                    io.github.matrixlane.metadataregistry.web.common.ApiCodes.VALIDATION_FAILED,
                    "默认身份仅可设为注册人员身份");
        }
        contactAccountService.markDefaultRegistrar(accountId, contactId);
    }

    /** 加载必须属于当前账号的联系人身份；不存在或属于他人时按 404 处理。 */
    private Contact loadOwnContact(UUID accountId, UUID contactId) {
        Contact entity = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId));
        java.util.Optional<UUID> boundAccount = contactAccountService.accountIdOfContact(contactId);
        if (boundAccount.isEmpty() || !boundAccount.get().equals(accountId)) {
            throw new ResourceNotFoundException("Contact", contactId);
        }
        return entity;
    }

    private MyContactResponse toMyResponse(Contact entity, boolean isDefault) {
        boolean isRegistrar = entity instanceof Registrar;
        UUID raId = null;
        String raName = null;
        if (isRegistrar) {
            io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority ra =
                    ((Registrar) entity).getRegistrationAuthority();
            if (ra != null) {
                raId = ra.getId();
                raName = ra.getName() == null ? null
                        : ra.getName().stream().findFirst().map(n -> n.getValue()).orElse(null);
            }
        }
        return new MyContactResponse(
                entity.getId(),
                entity.getOrganization() != null ? entity.getOrganization().getId() : null,
                entity.getOrganization() != null && entity.getOrganization().getName() != null
                        ? entity.getOrganization().getName().stream().findFirst().map(n -> n.getValue()).orElse(null)
                        : null,
                entity.getIndividual() != null ? entity.getIndividual().getId() : null,
                entity.getIndividual() != null && entity.getIndividual().getName() != null
                        ? entity.getIndividual().getName().getValue() : null,
                entity.getRole() != null ? entity.getRole().getId() : null,
                entity.getRole() != null ? entity.getRole().getRoleName() : null,
                isRegistrar,
                raId,
                raName,
                isDefault);
    }

    public void batchConfigure(ContactBatchRequest request) {        Organization org = organizationRepository.findById(request.organizationId())
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
            contactAccountService.bind(saved.getId(), account.getId());
        }
    }
}
