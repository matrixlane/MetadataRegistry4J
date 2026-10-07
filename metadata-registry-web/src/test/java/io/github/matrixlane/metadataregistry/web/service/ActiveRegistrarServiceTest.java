package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * personal-no-login-registrar-default：激活状态浏览器化后的解析链单测。
 * 解析链：X-Active-Registrar-Id 请求头（合法）→ ext.registrar.is_default → 首个有效身份；
 * 全程纯读，无任何持久化写回。
 */
@ExtendWith(MockitoExtension.class)
class ActiveRegistrarServiceTest {

    @Mock private RegistrarRepository registrarRepository;
    @Mock private ContactAccountService contactAccountService;
    @Mock private AdministeredItemRepository administeredItemRepository;
    @Mock private io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository registrationAuthorityRepository;

    @InjectMocks
    private ActiveRegistrarService service;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

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

    private void withHeader(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (value != null) {
            request.addHeader(ActiveRegistrarService.ACTIVE_REGISTRAR_HEADER, value);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    // ---- 解析链 ----

    @Test
    void resolve_singleIdentity_activatesAutomatically() {
        UUID accountId = UUID.randomUUID();
        Registrar registrar = newRegistrar(newRa());
        when(contactAccountService.registrarsOfAccount(accountId)).thenReturn(List.of(registrar));

        Optional<Registrar> active = service.resolveActiveRegistrar(accountId);

        assertThat(active).contains(registrar);
    }

    @Test
    void resolve_headerSelectionWinsOverDefault() {
        UUID accountId = UUID.randomUUID();
        Registrar first = newRegistrar(newRa());
        Registrar second = newRegistrar(newRa());
        when(contactAccountService.registrarsOfAccount(accountId)).thenReturn(List.of(first, second));
        // 注意：命中请求头时不再查询默认注册人员（解析链短路），此处不 stub defaultRegistrarOf
        withHeader(second.getId().toString());

        Optional<Registrar> active = service.resolveActiveRegistrar(accountId);

        assertThat(active).contains(second);
    }

    @Test
    void resolve_headerNotInIdentities_ignoredFallsToDefault() {
        UUID accountId = UUID.randomUUID();
        Registrar first = newRegistrar(newRa());
        Registrar second = newRegistrar(newRa());
        when(contactAccountService.registrarsOfAccount(accountId)).thenReturn(List.of(first, second));
        when(contactAccountService.defaultRegistrarIdOf(accountId)).thenReturn(Optional.of(second.getId()));
        withHeader(UUID.randomUUID().toString()); // 不属于该账号的身份

        Optional<Registrar> active = service.resolveActiveRegistrar(accountId);

        assertThat(active).contains(second);
    }

    @Test
    void resolve_headerMalformed_ignoredFallsToDefault() {
        UUID accountId = UUID.randomUUID();
        Registrar first = newRegistrar(newRa());
        when(contactAccountService.registrarsOfAccount(accountId)).thenReturn(List.of(first));
        withHeader("not-a-uuid");

        Optional<Registrar> active = service.resolveActiveRegistrar(accountId);

        assertThat(active).contains(first);
    }

    @Test
    void resolve_noRequestContext_skipsHeaderAndFallsToDefault() {
        UUID accountId = UUID.randomUUID();
        Registrar first = newRegistrar(newRa());
        Registrar second = newRegistrar(newRa());
        when(contactAccountService.registrarsOfAccount(accountId)).thenReturn(List.of(first, second));
        when(contactAccountService.defaultRegistrarIdOf(accountId)).thenReturn(Optional.of(second.getId()));

        Optional<Registrar> active = service.resolveActiveRegistrar(accountId);

        assertThat(active).contains(second);
    }

    @Test
    void resolve_defaultRegistrarWinsOverFirst() {
        UUID accountId = UUID.randomUUID();
        Registrar first = newRegistrar(newRa());
        Registrar second = newRegistrar(newRa());
        when(contactAccountService.registrarsOfAccount(accountId)).thenReturn(List.of(first, second));
        when(contactAccountService.defaultRegistrarIdOf(accountId)).thenReturn(Optional.of(second.getId()));

        Optional<Registrar> active = service.resolveActiveRegistrar(accountId);

        assertThat(active).contains(second);
    }

    @Test
    void resolve_defaultRegistrarNotInIdentities_fallsBackToFirst() {
        // 默认标志指向已撤销/失效的身份 → 回退首个有效身份
        UUID accountId = UUID.randomUUID();
        Registrar first = newRegistrar(newRa());
        Registrar revoked = newRegistrar(newRa());
        when(contactAccountService.registrarsOfAccount(accountId)).thenReturn(List.of(first));
        when(contactAccountService.defaultRegistrarIdOf(accountId)).thenReturn(Optional.of(revoked.getId()));

        Optional<Registrar> active = service.resolveActiveRegistrar(accountId);

        assertThat(active).contains(first);
    }

    @Test
    void resolve_emptyWhenNoIdentity() {
        UUID accountId = UUID.randomUUID();
        when(contactAccountService.registrarsOfAccount(accountId)).thenReturn(List.of());

        assertThat(service.resolveActiveRegistrar(accountId)).isEmpty();
    }

    @Test
    void resolve_isPureRead_noRepositoryWrites() {
        UUID accountId = UUID.randomUUID();
        Registrar registrar = newRegistrar(newRa());
        when(contactAccountService.registrarsOfAccount(accountId)).thenReturn(List.of(registrar));

        service.resolveActiveRegistrar(accountId);

        // 纯读解析：RegistrarRepository 零交互（写回缓存机制已移除）
        verifyNoInteractions(registrarRepository);
    }

    @Test
    void resolveActiveIdentity_returnsMappedIdentityView() {
        UUID accountId = UUID.randomUUID();
        Registrar registrar = newRegistrar(newRa());
        when(contactAccountService.registrarsOfAccount(accountId)).thenReturn(List.of(registrar));

        Optional<ActiveRegistrarService.RegistrarIdentity> identity = service.resolveActiveIdentity(accountId);

        assertThat(identity).isPresent();
        assertThat(identity.get().registrarId()).isEqualTo(registrar.getId());
        assertThat(identity.get().registrationAuthorityId())
                .isEqualTo(registrar.getRegistrationAuthority().getId());
    }

    @Test
    void resolveActiveIdentity_emptyWhenNoIdentity() {
        UUID accountId = UUID.randomUUID();
        when(contactAccountService.registrarsOfAccount(accountId)).thenReturn(List.of());

        assertThat(service.resolveActiveIdentity(accountId)).isEmpty();
    }

    // ---- 变更权限约束 ----

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
        when(contactAccountService.hasAnyContact(accountId)).thenReturn(true);
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
        when(contactAccountService.hasAnyContact(accountId)).thenReturn(true);
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
        when(contactAccountService.hasAnyContact(accountId)).thenReturn(false);

        assertThatThrownBy(() -> spy.assertCanModify(ai))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void isSubmitter_delegatesToContactAccountService() {
        UUID accountId = UUID.randomUUID();
        when(contactAccountService.hasAnyContact(accountId)).thenReturn(true);
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
