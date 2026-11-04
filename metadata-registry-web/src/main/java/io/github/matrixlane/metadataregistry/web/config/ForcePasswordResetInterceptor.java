package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStatusProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ForcePasswordResetInterceptor implements HandlerInterceptor {

    private final SystemSettingService systemSettingService;

    // @Lazy:@WebMvcTest 切片会实例化 HandlerInterceptor,但不包含 SystemSettingService;
    // 延迟代理使切片上下文无需该服务即可加载(切片请求不会走到调用它的分支)。
    public ForcePasswordResetInterceptor(@Lazy SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser) {
            AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
            if (user.forceChangePassword()) {
                // 个人版无密码概念（personal-no-login-registrar-default）：强制改密拦截
                // MUST NOT 在个人版生效。会话标志只可能来自陈旧令牌或历史脏数据，
                // 触发拦截会把整个应用锁死在 498，而用户没有任何合法操作能清掉该标志。
                if (systemSettingService.getEdition() == DeploymentEdition.PERSONAL) {
                    return true;
                }
                String path = request.getRequestURI();
                // Whitelist endpoints for password reset process
                if (path.endsWith("/api/v1/auth/login") ||
                        path.endsWith("/api/v1/auth/change-password") ||
                        path.endsWith("/api/v1/auth/logout") ||
                        path.endsWith("/api/v1/auth/me") ||
                        // Escape hatch: factory reset re-verifies the admin
                        // password itself, so it is safe (and useful) to
                        // allow while the session is in force-change state.
                        path.endsWith("/api/v1/system/factory-reset") ||
                        path.endsWith("/api/v1/init-wizard/status") ||
                        path.endsWith("/api/v1/init-wizard/setup") ||
                        path.endsWith("/api/v1/init-wizard/commit") ||
                        path.endsWith("/api/v1/init-wizard/abort") ||
                        path.endsWith("/api/v1/init-wizard/wizard-state")) {
                    return true;
                }

                // 系统未初始化时，初始化向导的数据库步骤需要数据源端点；
                // 数据库连接参数与管理员账号密码无关，且控制层在未初始化时本就
                // 允许匿名访问（见 DatasourceConfigController.guardWhenInitialized）。
                if (!systemSettingService.isInitialized() && path.startsWith("/api/v1/datasource/")) {
                    return true;
                }

                // 语言字典是只读公共数据，与账号密码无关；向导第 1 步挂载时
                // 会加载语言列表，放行以避免强改密会话下出现误导性提示。
                if (path.endsWith("/api/v1/languages")) {
                    return true;
                }

                // Throw exception to be handled by GlobalExceptionHandler and mapped to 498 status
                throw new io.github.matrixlane.metadataregistry.web.common.ForcePasswordResetException("安全提示：请先修改默认密码或临时密码");
            }
        }
        return true;
    }
}
