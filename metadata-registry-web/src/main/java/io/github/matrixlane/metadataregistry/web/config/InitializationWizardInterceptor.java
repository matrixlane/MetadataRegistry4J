package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStatusProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class InitializationWizardInterceptor implements HandlerInterceptor {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private InitializationStatusProvider statusProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        // Whitelist paths
        if (path.startsWith("/api/v1/init-wizard/") ||
            path.startsWith("/api/v1/datasource/") ||
            path.startsWith("/api/v1/migration/") ||
            // 初始化引导交互：失败态下的重试与"确认退出"——与数据源端点同等的
            // 匿名可达性（仅本机回环，由控制器守卫）
            path.startsWith("/api/v1/bootstrap/") ||
            path.startsWith("/api/v1/identifier-strategy/") ||
            path.startsWith("/api/v1/auth/login") ||
            path.startsWith("/api/v1/auth/me") ||
            path.startsWith("/api/v1/auth/allow-registration") ||
            // 启动就绪探针：只返回 ready 布尔值、不含任何业务数据。未初始化时也必须
            // 可达——前端启动等待页与数据源自动重启的启动器都依赖它判断“启动完成”，
            // 若在未初始化阶段被 499 拦截，重启事务将永远等不到就绪信号。
            path.equals("/api/v1/startup/status") ||
            // 语言字典只读列表：初始化向导无账号状态下拉取语言选项（仅 GET，与 SecurityConfig 白名单一致）
            (path.equals("/api/v1/languages") && "GET".equalsIgnoreCase(request.getMethod())) ||
            path.contains("/swagger-ui") ||
            path.contains("/v3/api-docs") ||
            path.endsWith("/swagger-ui.html")) {
            return true;
        }

        // If the system is not initialized, block and return uninitialized status code (499)
        // 判定经 InitializationStatusProvider：新架构下读本地状态记录（零数据库查询），
        // 关闭时退化为数据库判定（既有行为）。
        if (statusProvider != null && !statusProvider.isInitialized()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":499,\"message\":\"System uninitialized. Redirecting to wizard.\"}");
            return false;
        }

        return true;
    }
}
