package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.service.ContactAccountService;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 个人版免登录的隐式管理员自动认证（personal-no-login-registrar-default / 个人版 Authorization 免疫）。
 * 覆盖：PERSONAL 无 Bearer 头自动认证为唯一 SUPER_ADMIN、角色装配、
 * 团队版/已认证/无管理员时不装载主体；PERSONAL 下对后续链隐藏 Authorization 头
 * （旧令牌不得覆盖隐式主体造成 498 死锁）。
 */
@ExtendWith(MockitoExtension.class)
class PersonalEditionAuthenticationFilterTest {

    @Mock private SystemSettingService systemSettingService;
    @Mock private AccountRepository accountRepository;
    @Mock private ContactAccountService contactAccountService;
    @org.mockito.Mock
    private org.springframework.beans.factory.ObjectProvider<SystemSettingService> systemSettingServiceProvider;
    @org.mockito.Mock
    private org.springframework.beans.factory.ObjectProvider<AccountRepository> accountRepositoryProvider;
    @org.mockito.Mock
    private org.springframework.beans.factory.ObjectProvider<ContactAccountService> contactAccountServiceProvider;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private PersonalEditionAuthenticationFilter filter() {
        org.mockito.Mockito.lenient()
                .when(systemSettingServiceProvider.getIfAvailable()).thenReturn(systemSettingService);
        org.mockito.Mockito.lenient()
                .when(accountRepositoryProvider.getObject()).thenReturn(accountRepository);
        org.mockito.Mockito.lenient()
                .when(contactAccountServiceProvider.getIfAvailable()).thenReturn(contactAccountService);
        return new PersonalEditionAuthenticationFilter(
                systemSettingServiceProvider, accountRepositoryProvider, contactAccountServiceProvider);
    }

    private Account admin() {
        Account admin = new Account();
        admin.setId(UUID.randomUUID());
        admin.setUsername("admin");
        admin.setSysRole(SystemRole.SUPER_ADMIN);
        return admin;
    }

    /** 取出传给后续链的请求：个人版必须是隐藏 Authorization 的包装；其余路径为原请求。 */
    private HttpServletRequest capturedRequest() throws Exception {
        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(filterChain).doFilter(captor.capture(), eq(response));
        return captor.getValue();
    }

    @Test
    void personalEditionWithoutBearer_authenticatesAsImplicitAdmin() throws Exception {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        Account admin = admin();
        when(accountRepository.findFirstBySysRole(SystemRole.SUPER_ADMIN)).thenReturn(java.util.Optional.of(admin));

        filter().doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        AuthenticatedUser principal = (AuthenticatedUser) auth.getPrincipal();
        assertThat(principal.id()).isEqualTo(admin.getId());
        assertThat(principal.role()).isEqualTo(SystemRole.SUPER_ADMIN);
        // 个人版无密码概念：强制改密标志恒 false
        assertThat(principal.forceChangePassword()).isFalse();
        // SUPER_ADMIN 恒装配 ROLE_SUPER_ADMIN + ROLE_REGISTRAR
        assertThat(auth.getAuthorities())
                .extracting(SimpleGrantedAuthority.class::cast)
                .extracting(SimpleGrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_SUPER_ADMIN", "ROLE_REGISTRAR");
        // 个人版：对后续链隐藏 Authorization 头的包装请求（防旧令牌触发 498）
        HttpServletRequest passed = capturedRequest();
        assertThat(passed).isNotSameAs(request);
        assertThat(passed.getHeader("Authorization")).isNull();
    }

    @Test
    void personalEditionBoundContact_addsSubmitterRole() throws Exception {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        Account admin = admin();
        when(accountRepository.findFirstBySysRole(SystemRole.SUPER_ADMIN)).thenReturn(java.util.Optional.of(admin));
        when(contactAccountService.hasAnyContact(admin.getId())).thenReturn(true);

        filter().doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getAuthorities())
                .extracting(a -> ((SimpleGrantedAuthority) a).getAuthority())
                .contains("ROLE_SUBMITTER");
    }

    @Test
    void personalEditionWithStaleBearer_stillAuthenticatesAsImplicitAdminAndHidesToken() throws Exception {
        // 个人版对 Authorization 头一律免疫：即便请求携带旧令牌（如 forceChangePassword=true 的向导会话
        // 令牌），隐式主体仍生效，且包装请求使下游 JwtAuthenticationFilter 看不到该令牌。
        when(request.getHeader("Authorization")).thenReturn("Bearer stale-token");
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        Account admin = admin();
        when(accountRepository.findFirstBySysRole(SystemRole.SUPER_ADMIN)).thenReturn(java.util.Optional.of(admin));

        // 底层请求确实携带旧令牌（stub 被读取，避免 strict-stub 误判；同时证明包装层确实"隐藏"）
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer stale-token");

        filter().doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(((AuthenticatedUser) auth.getPrincipal()).id()).isEqualTo(admin.getId());
        HttpServletRequest passed = capturedRequest();
        assertThat(passed).isNotSameAs(request);
        assertThat(passed.getHeader("Authorization")).isNull();
    }

    @Test
    void teamEdition_skipsAutoAuthentication() throws Exception {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.TEAM);

        filter().doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(accountRepository);
        // 团队版不包装：原样透传
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void uninitializedPersonalEditionWithoutAdmin_remainsAnonymous() throws Exception {
        // 未初始化（无 SUPER_ADMIN 业务数据）：保持匿名，由未初始化访问控制返回 499；
        // 个人版下仍走隐藏 Authorization 的包装请求
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        when(accountRepository.findFirstBySysRole(SystemRole.SUPER_ADMIN)).thenReturn(java.util.Optional.empty());

        filter().doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        HttpServletRequest passed = capturedRequest();
        assertThat(passed).isNotSameAs(request);
        assertThat(passed.getHeader("Authorization")).isNull();
    }

    @Test
    void alreadyAuthenticatedUser_skips() throws Exception {
        Account admin = admin();
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(admin.getId(), admin.getUsername(),
                                SystemRole.SUPER_ADMIN, false, false, false),
                        null, List.of()));

        filter().doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(((AuthenticatedUser) auth.getPrincipal()).id()).isEqualTo(admin.getId());
        // 不重复装载账号；已认证路径不包装，原样透传
        verifyNoInteractions(accountRepository);
        verify(filterChain).doFilter(request, response);
    }
}