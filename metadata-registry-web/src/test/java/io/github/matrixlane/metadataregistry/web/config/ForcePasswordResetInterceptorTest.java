package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ForcePasswordResetException;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForcePasswordResetInterceptorTest {

    @Mock private SystemSettingService systemSettingService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private ForcePasswordResetInterceptor interceptor() {
        return new ForcePasswordResetInterceptor(systemSettingService);
    }

    private void authenticate(boolean forceChangePassword) {
        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(), "admin", SystemRole.SUPER_ADMIN, forceChangePassword, true, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(uri);
        return req;
    }

    @Test
    void forceChangeSession_datasourceAllowedWhenUninitialized() throws Exception {
        authenticate(true);
        when(systemSettingService.isInitialized()).thenReturn(false);

        assertThat(interceptor().preHandle(
                request("/api/v1/datasource/test-connection"), new MockHttpServletResponse(), null)).isTrue();
        assertThat(interceptor().preHandle(
                request("/api/v1/datasource/config"), new MockHttpServletResponse(), null)).isTrue();
    }

    @Test
    void forceChangeSession_datasourceBlockedWhenInitialized() {
        authenticate(true);
        when(systemSettingService.isInitialized()).thenReturn(true);

        assertThatThrownBy(() -> interceptor().preHandle(
                request("/api/v1/datasource/test-connection"), new MockHttpServletResponse(), null))
                .isInstanceOf(ForcePasswordResetException.class);
    }

    @Test
    void forceChangeSession_passwordEndpointsAlwaysAllowed() throws Exception {
        authenticate(true);
        // 白名单内的端点不查询初始化状态
        lenient().when(systemSettingService.isInitialized()).thenReturn(true);

        assertThat(interceptor().preHandle(
                request("/api/v1/auth/change-password"), new MockHttpServletResponse(), null)).isTrue();
    }

    @Test
    void forceChangeSession_otherEndpointsBlocked() {
        authenticate(true);
        when(systemSettingService.isInitialized()).thenReturn(true);

        assertThatThrownBy(() -> interceptor().preHandle(
                request("/api/v1/data-elements"), new MockHttpServletResponse(), null))
                .isInstanceOf(ForcePasswordResetException.class);
    }

    @Test
    void normalSession_anyEndpointAllowed() throws Exception {
        authenticate(false);

        assertThat(interceptor().preHandle(
                request("/api/v1/data-elements"), new MockHttpServletResponse(), null)).isTrue();
    }
}
