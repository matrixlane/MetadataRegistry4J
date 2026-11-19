package io.github.matrixlane.metadataregistry.web.mcp;

import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * MCP 面身份支持（add-mcp-server-support）：从 SecurityContext 解析调用身份
 * 与角色。身份语义与业务面完全一致——个人版隐式 SUPER_ADMIN 由
 * {@code PersonalEditionAuthenticationFilter} 装配，团队版 Bearer JWT 由
 * {@code JwtAuthenticationFilter} 装配；本类只读上下文，不改变认证语义。
 */
public final class McpIdentitySupport {

    private McpIdentitySupport() {
    }

    /** 身份标签（审计用）：已认证为 account:<uuid>，否则 anonymous@<remoteAddr>。 */
    public static String identityLabel(jakarta.servlet.http.HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return "account:" + user.id();
        }
        return "anonymous@" + (request == null ? "unknown" : request.getRemoteAddr());
    }

    /** 当前是否持有任一指定角色（ROLE_ 前缀自动补全）。 */
    public static boolean hasAnyRole(String... roles) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (String role : roles) {
            String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            boolean granted = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(authority::equals);
            if (granted) {
                return true;
            }
        }
        return false;
    }

    /** 当前是否有已认证主体（个人版隐式主体亦算）。 */
    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AuthenticatedUser;
    }
}
