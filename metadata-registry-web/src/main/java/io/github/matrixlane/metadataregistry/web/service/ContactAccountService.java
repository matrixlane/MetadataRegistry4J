package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.ext.basicandcore.ContactExtension;
import io.github.matrixlane.metadataregistry.ext.registration.RegistrarExtension;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.repository.ContactExtensionRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarExtensionRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Contact/Registrar 与登录账号（Account）绑定及默认注册人员标志的卫星读写服务
 * （personal-no-login-registrar-default 由 ContactAccountBindingService 重构而来）。
 *
 * <p>存储两层卫星（均有意不建 JPA 关联，读写经值键）：
 * <ul>
 *   <li>{@code ext.contact}（contact_id 即 PK）：contact → account 多对一绑定，
 *       承接原 contact_account_binding 表；</li>
 *   <li>{@code ext.registrar}（registrar_id 即 PK）：{@code is_default} 标志该账号
 *       一系列 Registrar 身份中的默认者（原 account.active_registrar_id 缓存与
 *       contact_account_binding.is_default 两概念的唯一收敛点）。</li>
 * </ul>
 *
 * <p>不变式：同一账号绑定的 Registrar 中至多一个默认；无默认时激活解析回退首个
 * 有效身份（见 {@code ActiveRegistrarService}）。普通 Contact 不存在默认概念。
 */
@Service
@Transactional
public class ContactAccountService {

    private final ContactExtensionRepository contactExtensionRepository;
    private final RegistrarExtensionRepository registrarExtensionRepository;
    private final ContactRepository contactRepository;
    private final RegistrarRepository registrarRepository;

    public ContactAccountService(ContactExtensionRepository contactExtensionRepository,
                                 RegistrarExtensionRepository registrarExtensionRepository,
                                 ContactRepository contactRepository,
                                 RegistrarRepository registrarRepository) {
        this.contactExtensionRepository = contactExtensionRepository;
        this.registrarExtensionRepository = registrarExtensionRepository;
        this.contactRepository = contactRepository;
        this.registrarRepository = registrarRepository;
    }

    // ===== ext.contact：账号绑定 =====

    /** Contact 绑定的账号 ID；未绑定时为空（语义同字段时代 account == null）。 */
    public Optional<UUID> accountIdOfContact(UUID contactId) {
        if (contactId == null) {
            return Optional.empty();
        }
        return contactExtensionRepository.findById(contactId)
                .map(ContactExtension::getAccountId);
    }

    /** 账号绑定的全部 Contact（原 contactsOfAccount 语义，经 ext.contact 反查）；按绑定顺序（contact_id 升序）确定性返回。 */
    public List<Contact> contactsOfAccount(Account account) {
        if (account == null || account.getId() == null) {
            return List.of();
        }
        List<UUID> contactIds = boundContactIdsOfAccount(account.getId());
        if (contactIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Contact> byId = new java.util.HashMap<>();
        contactRepository.findAllById(contactIds).forEach(c -> byId.put(c.getId(), c));
        return contactIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    /** 账号绑定的全部 Registrar；按绑定顺序（contact_id 升序）确定性返回。 */
    public List<Registrar> registrarsOfAccount(UUID accountId) {
        if (accountId == null) {
            return List.of();
        }
        List<UUID> contactIds = boundContactIdsOfAccount(accountId);
        if (contactIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Registrar> byId = new java.util.HashMap<>();
        registrarRepository.findAllById(contactIds).forEach(r -> byId.put(r.getId(), r));
        return contactIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    /** 账号是否绑定了任何 Contact（含 Registrar 行）；是 Submitter 能力的判定。 */
    public boolean hasAnyContact(UUID accountId) {
        return accountId != null && !contactExtensionRepository.findByAccountId(accountId).isEmpty();
    }

    /** 账号绑定的 contact id 列表（contact_id 升序，"最早绑定"即首条）。 */
    public List<UUID> boundContactIdsOfAccount(UUID accountId) {
        if (accountId == null) {
            return List.of();
        }
        return contactExtensionRepository.findByAccountIdOrderByIdAsc(accountId).stream()
                .map(ContactExtension::getId)
                .toList();
    }

    /** 建立/迁移绑定（upsert，contact_id 即主键）。
     * 携带默认标志的 Registrar 改绑到"已有其他默认注册人员"的账号时，清除迁入标志，
     * 维持同账号至多一默认（registrar 管理端改绑账号路径）。 */
    public void bind(UUID contactId, UUID accountId) {
        contactExtensionRepository.findById(contactId)
                .ifPresentOrElse(existing -> {
                    existing.setAccountId(accountId);
                    contactExtensionRepository.save(existing);
                }, () -> contactExtensionRepository.save(new ContactExtension(contactId, accountId)));
        if (isDefaultRegistrar(contactId)) {
            boolean otherDefaultExists = defaultRegistrarIdOf(accountId)
                    .map(existing -> !existing.equals(contactId))
                    .orElse(false);
            if (otherDefaultExists) {
                clearDefaultIfAny(contactId);
            }
        }
    }

    /** 解除绑定；未绑定时为 no-op。Registrar 的默认标志卫星行一并清理。 */
    public void unbind(UUID contactId) {
        if (contactId != null) {
            contactExtensionRepository.deleteById(contactId);
            deleteRegistrarExtension(contactId);
        }
    }

    // ===== ext.registrar：默认注册人员标志 =====

    /** 账号的默认 Registrar；未设置时为空。 */
    public Optional<Registrar> defaultRegistrarOf(UUID accountId) {
        return defaultRegistrarIdOf(accountId).flatMap(registrarRepository::findById);
    }

    /** 账号默认 Registrar 的 id；未设置时为空。 */
    public Optional<UUID> defaultRegistrarIdOf(UUID accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        List<UUID> contactIds = boundContactIdsOfAccount(accountId);
        if (contactIds.isEmpty()) {
            return Optional.empty();
        }
        return registrarExtensionRepository.findAllById(contactIds).stream()
                .filter(RegistrarExtension::isDefault)
                .map(RegistrarExtension::getId)
                .findFirst();
    }

    /** 该 Registrar 是否为某账号的默认注册人员。 */
    public boolean isDefaultRegistrar(UUID registrarId) {
        return registrarId != null
                && registrarExtensionRepository.findById(registrarId)
                        .map(RegistrarExtension::isDefault)
                        .orElse(false);
    }

    /**
     * 将账号下指定 Registrar 设为唯一默认：同一事务内清除该账号其他 Registrar 的
     * 默认标志。目标必须绑定到该账号，否则返回业务校验错误。
     */
    public void markDefaultRegistrar(UUID accountId, UUID registrarId) {
        requireBoundToAccount(accountId, registrarId);
        clearDefaultAmong(accountId);
        registrarExtensionRepository.findById(registrarId)
                .ifPresentOrElse(existing -> {
                    existing.setDefault(true);
                    registrarExtensionRepository.save(existing);
                }, () -> registrarExtensionRepository.save(new RegistrarExtension(registrarId, true)));
    }

    /**
     * 账号尚无默认注册人员时，把指定 Registrar 标为默认（首个 Registrar 身份自动
     * 默认语义）；已有默认时 no-op。调用前须保证该 Registrar 已绑定到该账号。
     */
    public void ensureDefaultRegistrarWhenFirst(UUID accountId, UUID registrarId) {
        if (defaultRegistrarIdOf(accountId).isPresent()) {
            return;
        }
        registrarExtensionRepository.findById(registrarId)
                .ifPresentOrElse(existing -> {
                    existing.setDefault(true);
                    registrarExtensionRepository.save(existing);
                }, () -> registrarExtensionRepository.save(new RegistrarExtension(registrarId, true)));
    }

    /** 删除 Registrar 的卫星行（身份删除时随实体清理，含默认标志）。 */
    public void deleteRegistrarExtension(UUID registrarId) {
        if (registrarId != null) {
            registrarExtensionRepository.deleteById(registrarId);
        }
    }

    /** 清除指定 Registrar 的默认标志（行保留）；非默认时 no-op。 */
    public void clearDefaultIfAny(UUID registrarId) {
        if (registrarId == null) {
            return;
        }
        registrarExtensionRepository.findById(registrarId).ifPresent(existing -> {
            if (existing.isDefault()) {
                existing.setDefault(false);
                registrarExtensionRepository.save(existing);
            }
        });
    }

    /**
     * 删除默认 Registrar 后的递补：把该账号剩余 Registrar 中的首个（contact_id 升序）
     * 标为默认；无剩余时 no-op。
     */
    public void promoteFirstRegistrarToDefault(UUID accountId) {
        List<Registrar> remaining = registrarsOfAccount(accountId);
        if (remaining.isEmpty()) {
            return;
        }
        UUID first = remaining.stream()
                .map(Registrar::getId)
                .sorted()
                .findFirst()
                .orElseThrow();
        ensureDefaultRegistrarWhenFirst(accountId, first);
    }

    private void requireBoundToAccount(UUID accountId, UUID registrarId) {
        Optional<UUID> boundAccountId = accountIdOfContact(registrarId);
        if (boundAccountId.isEmpty() || !accountId.equals(boundAccountId.get())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "该注册人员身份不属于当前账号");
        }
    }

    private void clearDefaultAmong(UUID accountId) {
        List<UUID> contactIds = boundContactIdsOfAccount(accountId);
        if (contactIds.isEmpty()) {
            return;
        }
        for (RegistrarExtension ext : registrarExtensionRepository.findAllById(contactIds)) {
            if (ext.isDefault()) {
                ext.setDefault(false);
                registrarExtensionRepository.save(ext);
            }
        }
    }
}
