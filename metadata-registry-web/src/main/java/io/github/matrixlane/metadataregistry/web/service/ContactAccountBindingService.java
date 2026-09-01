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

    /** 建立/更新绑定(upsert,按 contactId 唯一)。 */
    public void bind(UUID contactId, UUID accountId) {
        ContactAccountBinding binding = bindingRepository.findByContactId(contactId)
                .orElseGet(ContactAccountBinding::new);
        binding.setContactId(contactId);
        binding.setAccountId(accountId);
        bindingRepository.save(binding);
    }

    /** 解除绑定;未绑定时为 no-op。 */
    public void unbind(UUID contactId) {
        if (contactId != null) {
            bindingRepository.deleteByContactId(contactId);
        }
    }
}
