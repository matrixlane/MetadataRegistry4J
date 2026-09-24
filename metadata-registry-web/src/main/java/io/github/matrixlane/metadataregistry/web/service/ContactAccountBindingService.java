package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.ext.basicandcore.ContactAccountBinding;
import io.github.matrixlane.metadataregistry.web.repository.ContactAccountBindingRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link ContactAccountBinding} 卫星实体读写服务。
 *
 * <p>原 {@code Contact.account} 关联(标准外扩展)的全部读写经由此处:
 * 账号 → Contact/Registrar 反查经绑定表中转;Contact → 账号为按
 * contactId 的点查。绑定缺失语义等同字段时代的 {@code account == null}。
 */
@Service
public class ContactAccountBindingService {

    private final ContactAccountBindingRepository bindingRepository;
    private final ContactRepository contactRepository;
    private final RegistrarRepository registrarRepository;

    public ContactAccountBindingService(ContactAccountBindingRepository bindingRepository,
                                        ContactRepository contactRepository,
                                        RegistrarRepository registrarRepository) {
        this.bindingRepository = bindingRepository;
        this.contactRepository = contactRepository;
        this.registrarRepository = registrarRepository;
    }

    /** Contact 绑定的账号 ID;未绑定时为空(语义同字段时代 account == null)。 */
    public Optional<UUID> accountIdOfContact(UUID contactId) {
        if (contactId == null) {
            return Optional.empty();
        }
        return bindingRepository.findByContactId(contactId)
                .map(ContactAccountBinding::getAccountId);
    }

    /** 账号绑定的全部 Contact(原 contactRepository.findByAccount 的迁移路径)。 */
    public List<Contact> contactsOfAccount(Account account) {
        if (account == null || account.getId() == null) {
            return List.of();
        }
        List<UUID> contactIds = bindingRepository.findByAccountId(account.getId()).stream()
                .map(ContactAccountBinding::getContactId)
                .toList();
        if (contactIds.isEmpty()) {
            return List.of();
        }
        return contactRepository.findAllById(contactIds);
    }

    /** 账号绑定的全部 Registrar(原 registrarRepository.findByAccount_Id 的迁移路径)。 */
    public List<Registrar> registrarsOfAccount(UUID accountId) {
        if (accountId == null) {
            return List.of();
        }
        List<UUID> contactIds = bindingRepository.findByAccountId(accountId).stream()
                .map(ContactAccountBinding::getContactId)
                .toList();
        if (contactIds.isEmpty()) {
            return List.of();
        }
        return registrarRepository.findAllById(contactIds);
    }

    /** 账号是否绑定了任何 Contact（含 Registrar 行）；是 Submitter 能力的判定。 */
    public boolean hasAnyContact(UUID accountId) {
        if (accountId == null) {
            return false;
        }
        return !bindingRepository.findByAccountId(accountId).isEmpty();
    }

    /** 账号的默认联系人绑定；未设置时为空。 */
    public Optional<ContactAccountBinding> defaultBindingOf(UUID accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        return bindingRepository.findByAccountIdAndIsDefaultTrue(accountId);
    }

    /** 账号的全部绑定（按 contactId 升序，"最早绑定"即首条）。 */
    public List<ContactAccountBinding> bindingsOfAccount(UUID accountId) {
        if (accountId == null) {
            return List.of();
        }
        return bindingRepository.findByAccountIdOrderByContactIdAsc(accountId);
    }

    /** 建立/更新绑定(upsert,按 contactId 唯一)。新建绑定时若为该账号的首个
     *  身份,自动标记为默认(add-submitter-contact-identity：唯一身份即默认)。 */
    public void bind(UUID contactId, UUID accountId) {
        bindingRepository.findByContactId(contactId).ifPresentOrElse(existing -> {
            existing.setAccountId(accountId);
            bindingRepository.save(existing);
        }, () -> {
            boolean first = bindingRepository.findByAccountId(accountId).isEmpty();
            bindingRepository.save(new ContactAccountBinding(contactId, accountId, first));
        });
    }

    /** 解除绑定;未绑定时为 no-op。 */
    public void unbind(UUID contactId) {
        if (contactId != null) {
            bindingRepository.deleteByContactId(contactId);
        }
    }

    /** 建立绑定并落库,返回绑定实体。 */
    public ContactAccountBinding bindAndSave(UUID contactId, UUID accountId, boolean isDefault) {
        ContactAccountBinding binding = bindingRepository.findByContactId(contactId)
                .orElseGet(ContactAccountBinding::new);
        binding.setContactId(contactId);
        binding.setAccountId(accountId);
        binding.setDefault(isDefault);
        return bindingRepository.save(binding);
    }

    /**
     * 将账号下指定绑定设为唯一默认：同一事务内清除该账号其他绑定的默认标志。
     * 绑定必须属于该账号。
     */
    public void markDefault(UUID accountId, UUID contactId) {
        ContactAccountBinding target = bindingRepository.findByContactId(contactId)
                .filter(b -> accountId.equals(b.getAccountId()))
                .orElseThrow(() -> new io.github.matrixlane.metadataregistry.web.common.BusinessException(
                        io.github.matrixlane.metadataregistry.web.common.ApiCodes.VALIDATION_FAILED,
                        "该联系人身份不属于当前账号"));
        for (ContactAccountBinding b : bindingRepository.findByAccountId(accountId)) {
            if (!b.getId().equals(target.getId()) && b.isDefault()) {
                b.setDefault(false);
                bindingRepository.save(b);
            }
        }
        if (!target.isDefault()) {
            target.setDefault(true);
            bindingRepository.save(target);
        }
    }

    /** 账号绑定中最早（contactId 最小）的一条；无绑定时为空。 */
    public Optional<ContactAccountBinding> firstBindingOf(UUID accountId) {
        return bindingsOfAccount(accountId).stream().findFirst();
    }
}
