package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.activeregistrar.ActiveRegistrarResponse;
import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 活动 Registrar GET 契约（personal-no-login-registrar-default，纯单元级）：
 * 响应为 {defaultRegistrarId, identities}（不再含 active 字段）；PUT 端点已删除；
 * 未认证时拒绝。安全链路透传在切片中无既有范式（@AuthenticationPrincipal 无测试先例），
 * 故以直接调用锁定契约，端到端行为由冒烟覆盖。
 */
@ExtendWith(MockitoExtension.class)
class ActiveRegistrarControllerTest {

    @Mock
    private ActiveRegistrarService activeRegistrarService;

    private ActiveRegistrarController controller;

    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new ActiveRegistrarController(activeRegistrarService);
        AuthenticatedUser principal = new AuthenticatedUser(accountId, "admin", SystemRole.SUPER_ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        org.mockito.Mockito.lenient()
                .when(activeRegistrarService.currentAccountIdOrNull())
                .thenReturn(accountId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void current_returnsIdentitiesAndDefaultRegistrarId() {
        UUID defaultId = UUID.randomUUID();
        var identity = new ActiveRegistrarService.RegistrarIdentity(defaultId, UUID.randomUUID(), "测试机构");
        when(activeRegistrarService.defaultRegistrarIdOf(accountId)).thenReturn(Optional.of(defaultId));
        when(activeRegistrarService.listIdentities(accountId)).thenReturn(List.of(identity));

        ApiResponse<ActiveRegistrarResponse> res = controller.current();

        assertThat(res.getData().defaultRegistrarId()).isEqualTo(defaultId);
        assertThat(res.getData().identities()).hasSize(1);
        assertThat(res.getData().identities().get(0).registrationAuthorityName()).isEqualTo("测试机构");
    }

    @Test
    void current_withoutDefault_returnsNullDefaultField() {
        when(activeRegistrarService.defaultRegistrarIdOf(accountId)).thenReturn(Optional.empty());
        when(activeRegistrarService.listIdentities(accountId)).thenReturn(List.of());

        ApiResponse<ActiveRegistrarResponse> res = controller.current();

        assertThat(res.getData().defaultRegistrarId()).isNull();
        assertThat(res.getData().identities()).isEmpty();
    }

    @Test
    void current_unauthenticated_throwsAccessDenied() {
        SecurityContextHolder.clearContext();
        org.mockito.Mockito.when(activeRegistrarService.currentAccountIdOrNull()).thenReturn(null);

        assertThatThrownBy(() -> controller.current())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void putEndpoint_removed() {
        boolean anyPutHandler = Arrays.stream(ActiveRegistrarController.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("select"));
        assertThat(anyPutHandler).as("PUT /active-registrar 端点应已删除").isFalse();
    }
}
