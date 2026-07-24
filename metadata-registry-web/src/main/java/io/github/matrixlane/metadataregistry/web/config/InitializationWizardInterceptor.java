package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class InitializationWizardInterceptor implements HandlerInterceptor {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SystemSettingService systemSettingService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // Whitelist paths
        if (path.startsWith("/api/v1/init-wizard/") ||
            path.startsWith("/api/v1/datasource/") ||
            path.startsWith("/api/v1/migration/") ||
            path.startsWith("/api/v1/auth/login") ||
            path.startsWith("/api/v1/auth/me") ||
            path.startsWith("/api/v1/auth/allow-registration") ||
            path.contains("/swagger-ui") ||
            path.contains("/v3/api-docs") ||
            path.endsWith("/swagger-ui.html")) {
            return true;
        }

        // If the system is not initialized, block and return uninitialized status code (499)
        if (systemSettingService != null && !systemSettingService.isInitialized()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":499,\"message\":\"System uninitialized. Redirecting to wizard.\"}");
            return false;
        }

        return true;
    }
}
