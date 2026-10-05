package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveRegistrarServiceTest {

    @Mock private RegistrarRepository registrarRepository;
    @Mock private ContactAccountBindingService contactAccountBindingService;
    @Mock private AccountRepository accountRepository;
    @Mock private AdministeredItemRepository administeredItemRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository registrationAuthorityRepository;

    @InjectMocks
    private ActiveRegistrarService service;

    private RegistrationAuthority newRa() {
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(UUID.randomUUID());
        return ra;
    }

    private Registrar newRegistrar(RegistrationAuthority ra) {
        Registrar registrar = new Registrar(ra);
        registrar.setId(UUID.randomUUID());
        return registrar;
    }

    @Test
    void resolve_singleIdentity_activatesAutomatically() {
        UUID accountId = UUID.randomUUID();
        Registrar registrar = newRegistrar(newRa());
        when(contactAccountBindingService.registrarsOfAccount(accountId)).thenReturn(List.of(registrar));
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        Optional<Registrar> active = service.resolveActiveRegistrar(accountId);

        assertThat(active).contains(registrar);
    }

    @Test
    void resolve_honorsPersistedSelection() {
        UUID accountId = UUID.randomUUID();
        Registrar first = newRegistrar(newRa());
        Registrar second = newRegistrar(newRa());
        when(contactAccountBindingService.registrarsOfAccount(accountId)).thenReturn(List.of(first, second));
        Account account = new Account();
        account.setActiveRegistrarId(second.getId());
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        Optional<Registrar> active = service.resolveActiveRegistrar(accountId);

        assertThat(active).contains(second);
    }

    @Test
    void resolve_fallsBackWhenSelectionRevoked() {
        UUID accountId = UUID.randomUUID();
        Registrar first = newRegistrar(newRa());
        when(contactAccountBindingService.registrarsOfAccount(accountId)).thenReturn(List.of(first));
        Account account = new Account();
        account.setActiveRegistrarId(UUID.randomUUID()); // 已被撤销的身份
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        Optional<Registrar> active = service.resolveActiveRegistrar(accountId);

        assertThat(active).contains(first);
    }

    @Test
    void resolve_emptyWhenNoIdentity() {
        UUID accountId = UUID.randomUUID();
        when(contactAccountBindingService.registrarsOfAccount(accountId)).thenReturn(List.of());

        assertThat(service.resolveActiveRegistrar(accountId)).isEmpty();
    }

    @Test
    void resolveActiveIdentity_returnsMappedIdentityView() {
        UUID accountId = UUID.randomUUID();
        Registrar registrar = newRegistrar(newRa());
        when(contactAccountBindingService.registrarsOfAccount(accountId)).thenReturn(List.of(registrar));
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        Optional<ActiveRegistrarService.RegistrarIdentity> identity = service.resolveActiveIdentity(accountId);

        assertThat(identity).isPresent();
        assertThat(identity.get().registrarId()).isEqualTo(registrar.getId());
        assertThat(identity.get().registrationAuthorityId())
                .isEqualTo(registrar.getRegistrationAuthority().getId());
    }

    @Test
    void resolveActiveIdentity_emptyWhenNoIdentity() {
        UUID accountId = UUID.randomUUID();
        when(contactAccountBindingService.registrarsOfAccount(accountId)).thenReturn(List.of());

        assertThat(service.resolveActiveIdentity(accountId)).isEmpty();
    }

    @Test
    void select_persistsSelection() {
        UUID accountId = UUID.randomUUID();
        Registrar registrar = newRegistrar(newRa());
        Account owner = new Account();
        owner.setId(accountId);
        when(contactAccountBindingService.accountIdOfContact(registrar.getId()))
                .thenReturn(Optional.of(accountId));
        when(registrarRepository.findById(registrar.getId())).thenReturn(Optional.of(registrar));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(owner));

        var identity = service.selectActiveRegistrar(accountId, registrar.getId());

        assertThat(owner.getActiveRegistrarId()).isEqualTo(registrar.getId());
        verify(accountRepository).save(owner);
        assertThat(identity.registrationAuthorityId())
                .isEqualTo(registrar.getRegistrationAuthority().getId());
    }

    @Test
    void select_rejectsIdentityOfAnotherAccount() {
        UUID accountId = UUID.randomUUID();
        Registrar registrar = newRegistrar(newRa());
        when(contactAccountBindingService.accountIdOfContact(registrar.getId()))
                .thenReturn(Optional.of(UUID.randomUUID()));
        when(registrarRepository.findById(registrar.getId())).thenReturn(Optional.of(registrar));

        assertThatThrownBy(() -> service.selectActiveRegistrar(accountId, registrar.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void assertCanModify_allowsActiveIdentityOfOwner() {
        UUID accountId = UUID.randomUUID();
        RegistrationAuthority ra = newRa();
        Registrar registrar = newRegistrar(ra);
        AdministeredItem ai = mock(AdministeredItem.class);
        when(ai.getRegistrationAuthority()).thenReturn(ra);

        ActiveRegistrarService spy = spy(service);
        doReturn(accountId).when(spy).currentAccountIdOrNull();
        doReturn(Optional.of(registrar)).when(spy).resolveActiveRegistrar(accountId);

        spy.assertCanModify(ai);
    }

    @Test
    void assertCanModify_deniesWhenActiveIdentityBelongsToAnotherRa() {
        UUID accountId = UUID.randomUUID();
        RegistrationAuthority owner = newRa();
        Registrar otherIdentity = newRegistrar(newRa());
        AdministeredItem ai = mock(AdministeredItem.class);
        when(ai.getRegistrationAuthority()).thenReturn(owner);

        ActiveRegistrarService spy = spy(service);
        doReturn(accountId).when(spy).currentAccountIdOrNull();
        doReturn(Optional.of(otherIdentity)).when(spy).resolveActiveRegistrar(accountId);

        assertThatThrownBy(() -> spy.assertCanModify(ai))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertCanModify_skipsForLegacyRaLessItem() {
        AdministeredItem ai = mock(AdministeredItem.class);
        when(ai.getRegistrationAuthority()).thenReturn(null);

        ActiveRegistrarService spy = spy(service);

        spy.assertCanModify(ai); // 无归属历史遗留条目：维持既有行为
    }

    // ---- Submitter 通道（add-submitter-contact-identity） ----

    @Test
    void assertCanModify_allowsSubmitterWhenOwnerIsSystemUniqueRa() {
        UUID accountId = UUID.randomUUID();
        RegistrationAuthority owner = newRa();
        AdministeredItem ai = mock(AdministeredItem.class);
        when(ai.getRegistrationAuthority()).thenReturn(owner);

        ActiveRegistrarService spy = spy(service);
        doReturn(accountId).when(spy).currentAccountIdOrNull();
        doReturn(Optional.empty()).when(spy).resolveActiveRegistrar(accountId);
        when(contactAccountBindingService.hasAnyContact(accountId)).thenReturn(true);
        when(registrationAuthorityRepository.findAll()).thenReturn(List.of(owner));

        spy.assertCanModify(ai);
    }

    @Test
    void assertCanModify_deniesSubmitterWhenNotUniqueRa() {
        UUID accountId = UUID.randomUUID();
        RegistrationAuthority owner = newRa();
        AdministeredItem ai = mock(AdministeredItem.class);
        when(ai.getRegistrationAuthority()).thenReturn(owner);

        ActiveRegistrarService spy = spy(service);
        doReturn(accountId).when(spy).currentAccountIdOrNull();
        doReturn(Optional.empty()).when(spy).resolveActiveRegistrar(accountId);
        when(contactAccountBindingService.hasAnyContact(accountId)).thenReturn(true);
        // 系统中存在多个注册机构时：Submitter 通道关闭
        when(registrationAuthorityRepository.findAll()).thenReturn(List.of(owner, newRa()));

        assertThatThrownBy(() -> spy.assertCanModify(ai))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertCanModify_deniesAccountWithoutRegistrarNorContact() {
        UUID accountId = UUID.randomUUID();
        RegistrationAuthority owner = newRa();
        AdministeredItem ai = mock(AdministeredItem.class);
        when(ai.getRegistrationAuthority()).thenReturn(owner);

        ActiveRegistrarService spy = spy(service);
        doReturn(accountId).when(spy).currentAccountIdOrNull();
        doReturn(Optional.empty()).when(spy).resolveActiveRegistrar(accountId);
        when(contactAccountBindingService.hasAnyContact(accountId)).thenReturn(false);

        assertThatThrownBy(() -> spy.assertCanModify(ai))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void isSubmitter_delegatesToBindingService() {
        UUID accountId = UUID.randomUUID();
        when(contactAccountBindingService.hasAnyContact(accountId)).thenReturn(true);
        assertThat(service.isSubmitter(accountId)).isTrue();
        assertThat(service.isSubmitter(null)).isFalse();
    }

    @Test
    void systemUniqueRegistrationAuthority_onlyWhenExactlyOne() {
        RegistrationAuthority ra = newRa();
        when(registrationAuthorityRepository.findAll()).thenReturn(List.of(ra));
        assertThat(service.systemUniqueRegistrationAuthority()).contains(ra);

        when(registrationAuthorityRepository.findAll()).thenReturn(List.of(ra, newRa()));
        assertThat(service.systemUniqueRegistrationAuthority()).isEmpty();

        when(registrationAuthorityRepository.findAll()).thenReturn(List.of());
        assertThat(service.systemUniqueRegistrationAuthority()).isEmpty();
    }
}
