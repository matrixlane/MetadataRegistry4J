package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.ext.basicandcore.ContactAccountBinding;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.contact.ContactRequest;
import io.github.matrixlane.metadataregistry.web.dto.contact.ContactResponse;
import io.github.matrixlane.metadataregistry.web.dto.contact.ContactBatchRequest;
import io.github.matrixlane.metadataregistry.web.dto.contact.MyContactRequest;
import io.github.matrixlane.metadataregistry.web.dto.contact.MyContactResponse;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final io.github.matrixlane.metadataregistry.web.repository.SubmissionRecordRepository submissionRecordRepository;

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
     * 提交信息默认上下文（add-submitter-contact-identity）：账号默认联系人及其
     * 所属组织；未设默认时回退到绑定列表中最早绑定（contactId 最小）的一条；
     * 无任何绑定时返回空。
     */
    @Transactional(readOnly = true)
    public Optional<SubmissionContext> resolveDefaultSubmissionContext(UUID accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        Optional<Contact> contact = contactAccountBindingService.defaultBindingOf(accountId)
                .map(b -> contactRepository.findById(b.getContactId()))
                .orElseGet(() -> contactAccountBindingService.bindingsOfAccount(accountId).stream()
                        .findFirst()
                        .map(b -> contactRepository.findById(b.getContactId()))
                        .orElse(Optional.empty()));
        return contact.map(c -> new SubmissionContext(c, c.getOrganization()));
    }

    /** 提交信息默认上下文：默认联系人 + 其所属组织（组织可为空）。 */
    public record SubmissionContext(Contact contact, Organization organization) {
    }

    // ===== "我的"联系人身份（add-submitter-contact-identity） =====

    /** 当前账号绑定的全部联系人身份（含 Registrar 行），绑定顺序排列。 */
    @Transactional(readOnly = true)
    public List<MyContactResponse> listMyContacts(UUID accountId) {
        List<ContactAccountBinding> bindings = contactAccountBindingService.bindingsOfAccount(accountId);
        Set<UUID> defaultIds = bindings.stream()
                .filter(ContactAccountBinding::isDefault)
                .map(ContactAccountBinding::getContactId)
                .collect(Collectors.toSet());
        List<UUID> contactIds = bindings.stream().map(ContactAccountBinding::getContactId).toList();
        Map<UUID, Contact> byId = new HashMap<>();
        contactRepository.findAllById(contactIds).forEach(c -> byId.put(c.getId(), c));
        List<MyContactResponse> result = new ArrayList<>();
        for (UUID contactId : contactIds) {
            Contact c = byId.get(contactId);
            if (c != null) {
                result.add(toMyResponse(c, defaultIds.contains(contactId)));
            }
        }
        return result;
    }

    /** 为当前账号自建联系人身份；首个身份自动设为默认。 */
    public MyContactResponse createMyContact(UUID accountId, MyContactRequest request) {
        Contact entity = new Contact();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        applyRequest(entity, request);
        // 未指定 Individual 时回填账号自身的 Individual，便于身份列表展示姓名
        if (entity.getIndividual() == null && account.getIndividual() != null) {
            entity.setIndividual(account.getIndividual());
        }
        Contact saved = contactRepository.save(entity);
        boolean first = !contactAccountBindingService.hasAnyContact(accountId);
        contactAccountBindingService.bindAndSave(saved.getId(), accountId, first);
        return toMyResponse(saved, first);
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
                    contactAccountBindingService.defaultBindingOf(accountId)
                            .map(b -> contactId.equals(b.getContactId()))
                            .orElse(false));
        }
        applyRequest(entity, request);
        return toMyResponse(contactRepository.save(entity),
                contactAccountBindingService.defaultBindingOf(accountId)
                        .map(b -> contactId.equals(b.getContactId()))
                        .orElse(false));
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
     * 删除自建联系人身份；Registrar 行拒绝（409）。删除默认身份后自动把剩余最早
     * 绑定的一条设为默认；删空后账号失去 Submitter 能力。
     */
    public void deleteMyContact(UUID accountId, UUID contactId) {
        Contact entity = loadOwnContact(accountId, contactId);
        if (entity instanceof Registrar) {
            throw new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                    io.github.matrixlane.metadataregistry.web.common.ApiCodes.CONFLICT,
                    "Registrar 身份不可在此删除，请通过 Registrar 管理流程撤销");
        }
        boolean wasDefault = contactAccountBindingService.defaultBindingOf(accountId)
                .map(b -> contactId.equals(b.getContactId()))
                .orElse(false);
        // 已被 SubmissionRecord 引用时拒绝删除（外键非空，删除必失败；409 提示更友好）
        if (submissionRecordRepository.existsByContactId(contactId)) {
            throw new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                    io.github.matrixlane.metadataregistry.web.common.ApiCodes.CONFLICT,
                    "该联系人身份已作为提交联系人被提交记录引用，不可删除");
        }
        contactAccountBindingService.unbind(contactId);
        contactRepository.delete(entity);
        if (wasDefault) {
            contactAccountBindingService.firstBindingOf(accountId)
                    .ifPresent(b -> contactAccountBindingService.markDefault(accountId, b.getContactId()));
        }
    }

    /** 将指定身份设为当前账号的默认联系人。 */
    public void setDefaultMyContact(UUID accountId, UUID contactId) {
        loadOwnContact(accountId, contactId);
        contactAccountBindingService.markDefault(accountId, contactId);
    }

    /** 加载必须属于当前账号的联系人身份；不存在或属于他人时按 404 处理。 */
    private Contact loadOwnContact(UUID accountId, UUID contactId) {
        Contact entity = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId));
        java.util.Optional<UUID> boundAccount = contactAccountBindingService.accountIdOfContact(contactId);
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
            contactAccountBindingService.bind(saved.getId(), account.getId());
        }
    }
}
