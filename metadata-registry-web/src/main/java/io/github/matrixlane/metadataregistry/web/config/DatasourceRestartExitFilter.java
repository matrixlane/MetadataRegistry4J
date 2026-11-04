package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.web.service.ApplicationRestartExecutor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 在"保存并自动应用"响应写出之后才退出旧进程。
 *
 * <p>数据源自动应用要求后端先返回 202，再退出自己，由常驻启动器拉起新进程。
 * 若在响应写出前退出，客户端拿不到重启标识，只能靠超时猜测；因此退出的触发
 * 放在过滤器链结束之后：此时响应体已经写完并 flush 到连接。
 *
 * <p>触发路径有两条：「配置新的数据源」{@code /config/apply} 与初始化向导的
 * 「保持当前数据源并继续」{@code /config/confirm-current}——后者内部复用同一
 * 重启事务流程（写好事务文件后返回 202）。两条路径缺任一个，事务都会永远
 * 停在 PENDING：后端不退出，受管启动器等不到退出信号（2026-10-27 事故）。
 */
@Component
public class DatasourceRestartExitFilter extends OncePerRequestFilter {

    static final String APPLY_PATH = "/api/v1/datasource/config/apply";
    static final String CONFIRM_CURRENT_PATH = "/api/v1/datasource/config/confirm-current";

    private final ObjectProvider<ApplicationRestartExecutor> exitExecutorProvider;

    public DatasourceRestartExitFilter(ObjectProvider<ApplicationRestartExecutor> exitExecutorProvider) {
        this.exitExecutorProvider = exitExecutorProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        chain.doFilter(request, response);
        boolean restartInitiatingPath = APPLY_PATH.equals(request.getRequestURI())
                || CONFIRM_CURRENT_PATH.equals(request.getRequestURI());
        if (!restartInitiatingPath
                || response.getStatus() != HttpServletResponse.SC_ACCEPTED) {
            return;
        }
        if (!response.isCommitted()) {
            response.flushBuffer();
        }
        ApplicationRestartExecutor exitExecutor = exitExecutorProvider.getIfAvailable();
        if (exitExecutor != null) {
            exitExecutor.scheduleExit();
        }
    }
}
