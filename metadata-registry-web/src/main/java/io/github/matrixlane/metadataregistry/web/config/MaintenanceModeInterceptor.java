package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.service.migration.MaintenanceModeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 迁移维护状态拦截器：维护模式开启期间，拒绝一切非 GET 的 API 写操作
 * （迁移管理端点与认证端点除外）。
 */
@Component
@RequiredArgsConstructor
public class MaintenanceModeInterceptor implements HandlerInterceptor {

    private final ObjectProvider<MaintenanceModeService> maintenanceModeService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // @WebMvcTest 切片不加载服务层，缺省视为非维护模式
        MaintenanceModeService service = maintenanceModeService.getIfAvailable();
        if (service == null || !service.isActive()) {
            return true;
        }
        String path = request.getRequestURI();
        if ("GET".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/api/v1/migration/")
                || path.startsWith("/api/v1/auth/")) {
            return true;
        }
        response.setStatus(503);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":503,\"message\":\"系统处于数据迁移维护状态，写操作暂不可用\"}");
        return false;
    }
}
