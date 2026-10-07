package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.ext.basicandcore.ContactExtension;
import io.github.matrixlane.metadataregistry.ext.registration.RegistrarExtension;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.repository.ContactExtensionRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarExtensionRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** ContactAccountService：ext.contact 绑定 + ext.registrar 默认注册人员标志（personal-no-login-registrar-default）。 */
@ExtendWith(MockitoExtension.class)
class ContactAccountServiceTest {

    @Mock private ContactExtensionRepository contactExtensionRepository;
    @Mock private RegistrarExtensionRepository registrarExtensionRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private RegistrarRepository registrarRepository;

    @InjectMocks
    private ContactAccountService service;

    private final UUID accountId = UUID.randomUUID();
    private final UUID contactId = UUID.randomUUID();

    // ---- ext.contact 绑定 ----

    @Test
    void bind_newBindingSaved() {
        when(contactExtensionRepository.findById(contactId)).thenReturn(Optional.empty());

        service.bind(contactId, accountId);

        ArgumentCaptor<ContactExtension> captor = ArgumentCaptor.forClass(ContactExtension.class);
        verify(contactExtensionRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(contactId);
        assertThat(captor.getValue().getAccountId()).isEqualTo(accountId);
    }

    @Test
    void bind_existingBindingUpdatesAccount() {
        ContactExtension existing = new ContactExtension(contactId, UUID.randomUUID());
        when(contactExtensionRepository.findById(contactId)).thenReturn(Optional.of(existing));

        service.bind(contactId, accountId);

        verify(contactExtensionRepository).save(existing);
        assertThat(existing.getAccountId()).isEqualTo(accountId);
    }

    @Test
    void unbind_deletesByContactId() {
        service.unbind(contactId);
        verify(contactExtensionRepository).deleteById(contactId);
    }

    @Test
    void accountIdOfContact_readsSatellite() {
        when(contactExtensionRepository.findById(contactId))
                .thenReturn(Optional.of(new ContactExtension(contactId, accountId)));
        assertThat(service.accountIdOfContact(contactId)).contains(accountId);
        assertThat(service.accountIdOfContact(null)).isEmpty();
    }

    // ---- ext.registrar 默认注册人员标志 ----

    @Test
    void markDefaultRegistrar_clearsOthersAndSetsTarget() {
        UUID otherId = UUID.randomUUID();
        when(contactExtensionRepository.findById(contactId))
                .thenReturn(Optional.of(new ContactExtension(contactId, accountId)));
        when(contactExtensionRepository.findByAccountIdOrderByIdAsc(accountId))
                .thenReturn(List.of(new ContactExtension(contactId, accountId), new ContactExtension(otherId, accountId)));
        RegistrarExtension otherExt = new RegistrarExtension(otherId, true);
        RegistrarExtension targetExt = new RegistrarExtension(contactId, false);
        when(registrarExtensionRepository.findAllById(any())).thenReturn(List.of(otherExt, targetExt));
        when(registrarExtensionRepository.findById(contactId)).thenReturn(Optional.of(targetExt));

        service.markDefaultRegistrar(accountId, contactId);

        assertThat(otherExt.isDefault()).isFalse();
        assertThat(targetExt.isDefault()).isTrue();
        verify(registrarExtensionRepository, atLeastOnce()).save(otherExt);
        verify(registrarExtensionRepository).save(targetExt);
    }

    @Test
    void markDefaultRegistrar_createsRowWhenMissing() {
        UUID registrarId = UUID.randomUUID();
        when(contactExtensionRepository.findById(registrarId))
                .thenReturn(Optional.of(new ContactExtension(registrarId, accountId)));
        when(contactExtensionRepository.findByAccountIdOrderByIdAsc(accountId))
                .thenReturn(List.of(new ContactExtension(registrarId, accountId)));
        when(registrarExtensionRepository.findAllById(any())).thenReturn(List.of());
        when(registrarExtensionRepository.findById(registrarId)).thenReturn(Optional.empty());

        service.markDefaultRegistrar(accountId, registrarId);

        ArgumentCaptor<RegistrarExtension> captor = ArgumentCaptor.forClass(RegistrarExtension.class);
        verify(registrarExtensionRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(registrarId);
        assertThat(captor.getValue().isDefault()).isTrue();
    }

    @Test
    void markDefaultRegistrar_rejectsForeignRegistrar() {
        when(contactExtensionRepository.findById(contactId))
                .thenReturn(Optional.of(new ContactExtension(contactId, UUID.randomUUID())));

        assertThatThrownBy(() -> service.markDefaultRegistrar(accountId, contactId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ensureDefault_whenAbsent_setsDefault() {
        UUID registrarId = UUID.randomUUID();
        when(contactExtensionRepository.findByAccountIdOrderByIdAsc(accountId))
                .thenReturn(List.of(new ContactExtension(registrarId, accountId)));
        when(registrarExtensionRepository.findAllById(any())).thenReturn(List.of());
        when(registrarExtensionRepository.findById(registrarId)).thenReturn(Optional.empty());

        service.ensureDefaultRegistrarWhenFirst(accountId, registrarId);

        ArgumentCaptor<RegistrarExtension> captor = ArgumentCaptor.forClass(RegistrarExtension.class);
        verify(registrarExtensionRepository).save(captor.capture());
        assertThat(captor.getValue().isDefault()).isTrue();
    }

    @Test
    void ensureDefault_whenAlreadyDefault_noOp() {
        UUID registrarId = UUID.randomUUID();
        when(contactExtensionRepository.findByAccountIdOrderByIdAsc(accountId))
                .thenReturn(List.of(new ContactExtension(registrarId, accountId)));
        when(registrarExtensionRepository.findAllById(any()))
                .thenReturn(List.of(new RegistrarExtension(registrarId, true)));

        service.ensureDefaultRegistrarWhenFirst(accountId, registrarId);

        verify(registrarExtensionRepository, never()).save(any());
    }

    @Test
    void defaultRegistrarOf_returnsBoundDefaultRegistrar() {
        UUID registrarId = UUID.randomUUID();
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Registrar registrar = new Registrar(ra);
        registrar.setId(registrarId);
        when(contactExtensionRepository.findByAccountIdOrderByIdAsc(accountId))
                .thenReturn(List.of(new ContactExtension(registrarId, accountId)));
        when(registrarExtensionRepository.findAllById(any()))
                .thenReturn(List.of(new RegistrarExtension(registrarId, true)));
        when(registrarRepository.findById(registrarId)).thenReturn(Optional.of(registrar));

        assertThat(service.defaultRegistrarOf(accountId)).contains(registrar);
    }

    @Test
    void promoteFirstRegistrarToDefault_usesFirstByContactIdOrder() {
        // 确定性 UUID：first < second（按自然序），与实现 sorted() 语义一致
        UUID first = new UUID(0, 1);
        UUID second = new UUID(0, 2);
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Registrar r1 = new Registrar(ra);
        r1.setId(first);
        Registrar r2 = new Registrar(ra);
        r2.setId(second);
        // 绑定顺序已按 contact_id 升序返回（仓储职责）
        when(contactExtensionRepository.findByAccountIdOrderByIdAsc(accountId))
                .thenReturn(List.of(new ContactExtension(first, accountId), new ContactExtension(second, accountId)));
        when(registrarRepository.findAllById(any())).thenReturn(List.of(r1, r2));
        when(registrarExtensionRepository.findAllById(any())).thenReturn(List.of());
        when(registrarExtensionRepository.findById(first)).thenReturn(Optional.empty());

        service.promoteFirstRegistrarToDefault(accountId);

        ArgumentCaptor<RegistrarExtension> captor = ArgumentCaptor.forClass(RegistrarExtension.class);
        verify(registrarExtensionRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(first);
        assertThat(captor.getValue().isDefault()).isTrue();
    }

    @Test
    void deleteRegistrarExtension_deletesById() {
        service.deleteRegistrarExtension(contactId);
        verify(registrarExtensionRepository).deleteById(contactId);
    }

    @Test
    void hasAnyContact_delegatesToSatelliteCount() {
        when(contactExtensionRepository.findByAccountId(accountId))
                .thenReturn(List.of(new ContactExtension(contactId, accountId)));
        assertThat(service.hasAnyContact(accountId)).isTrue();
        assertThat(service.hasAnyContact(null)).isFalse();
    }

    // ---- 单默认不变式：bind/unbind ----

    @Test
    void unbind_alsoDeletesRegistrarExtension() {
        service.unbind(contactId);
        verify(contactExtensionRepository).deleteById(contactId);
        verify(registrarExtensionRepository).deleteById(contactId);
    }

    @Test
    void bind_movedDefaultRegistrarToAccountWithOtherDefault_clearsMovedFlag() {
        UUID registrarId = UUID.randomUUID();
        UUID otherDefault = UUID.randomUUID();
        when(contactExtensionRepository.findById(registrarId)).thenReturn(Optional.empty());
        // 迁入 registrar 自带默认标志
        when(registrarExtensionRepository.findById(registrarId))
                .thenReturn(Optional.of(new RegistrarExtension(registrarId, true)));
        // 目标账号已有另一默认
        when(contactExtensionRepository.findByAccountIdOrderByIdAsc(accountId))
                .thenReturn(List.of(new ContactExtension(registrarId, accountId),
                        new ContactExtension(otherDefault, accountId)));
        // defaultRegistrarIdOf 取 findAllById 结果中首个 is_default——
        // 既有默认排前，bind 才能识别"目标账号已有其他默认"
        when(registrarExtensionRepository.findAllById(any()))
                .thenReturn(List.of(new RegistrarExtension(otherDefault, true),
                        new RegistrarExtension(registrarId, true)));

        service.bind(registrarId, accountId);

        // 迁入者默认标志被清除（目标账号保留原有默认）
        verify(registrarExtensionRepository).save(argThat(ext ->
                ext.getId().equals(registrarId) && !ext.isDefault()));
    }

    @Test
    void bind_movedDefaultRegistrarToAccountWithoutDefault_keepsFlag() {
        UUID registrarId = UUID.randomUUID();
        when(contactExtensionRepository.findById(registrarId)).thenReturn(Optional.empty());
        when(registrarExtensionRepository.findById(registrarId))
                .thenReturn(Optional.of(new RegistrarExtension(registrarId, true)));
        when(contactExtensionRepository.findByAccountIdOrderByIdAsc(accountId))
                .thenReturn(List.of(new ContactExtension(registrarId, accountId)));
        when(registrarExtensionRepository.findAllById(any()))
                .thenReturn(List.of(new RegistrarExtension(registrarId, true)));

        service.bind(registrarId, accountId);

        // 目标账号无其他默认：迁入者保留默认标志（成为该账号默认）
        verify(registrarExtensionRepository, never()).save(any());
    }

    @Test
    void registrarsOfAccount_returnsInBindingOrder() {
        UUID first = new UUID(0, 1);
        UUID second = new UUID(0, 2);
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        Registrar r1 = new Registrar(ra);
        r1.setId(first);
        Registrar r2 = new Registrar(ra);
        r2.setId(second);
        when(contactExtensionRepository.findByAccountIdOrderByIdAsc(accountId))
                .thenReturn(List.of(new ContactExtension(first, accountId),
                        new ContactExtension(second, accountId)));
        // 仓储乱序返回，服务应按绑定顺序归位
        when(registrarRepository.findAllById(any())).thenReturn(List.of(r2, r1));

        assertThat(service.registrarsOfAccount(accountId)).extracting(Registrar::getId)
                .containsExactly(first, second);
    }

    @Test
    void clearDefaultIfAny_clearsOnlyWhenDefault() {
        RegistrarExtension ext = new RegistrarExtension(contactId, true);
        when(registrarExtensionRepository.findById(contactId)).thenReturn(Optional.of(ext));
        service.clearDefaultIfAny(contactId);
        assertThat(ext.isDefault()).isFalse();
        verify(registrarExtensionRepository).save(ext);
    }
}
