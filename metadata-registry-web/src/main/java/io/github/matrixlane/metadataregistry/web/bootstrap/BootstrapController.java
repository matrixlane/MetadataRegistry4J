package io.github.matrixlane.metadataregistry.web.bootstrap;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.service.ApplicationRestartExecutor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据库初始化引导的运行时交互（init-phase-database-free）。
 *
 * <p>引导失败时应用保持运行，本控制器提供两个动作：
 * <ul>
 *   <li>{@code POST /api/v1/bootstrap/retry}——复用同一引导入口重新执行；</li>
 *   <li>{@code POST /api/v1/bootstrap/exit}——用户确认退出：调度后端退出，
 *       启动器随之结束托管，应用完全停止。</li>
 * </ul>
 *
 * <p>两个端点 MUST 仅允许本机回环访问（含口令/路径信息不得对外暴露），且仅在
 * 新架构完整相下有意义；其余情形返回明确错误。
 */
@RestController
@RequestMapping("/api/v1/bootstrap")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "0. 系统管理 - 初始化引导", description = "数据库初始化引导的重试与退出")
public class BootstrapController {

    private final BootstrapMode mode;
    private final DatabaseBootstrapService bootstrapService;
    private final ApplicationRestartExecutor exitExecutor;

    @PostMapping("/retry")
    @Operation(summary = "重试数据库初始化引导", description = "复用启动期同一引导入口重新执行全部步骤；仅本机回环可用")
    public ApiResponse<BootstrapOutcome> retry(HttpServletRequest request) {
        assertLoopback(request);
        if (!mode.enabled() || mode.phase() != BootstrapPhase.FULL) {
            throw new BusinessException(ApiCodes.FEATURE_NOT_ENABLED, "当前启动方式不支持重试初始化");
        }
        BootstrapOutcome current = bootstrapService.currentOutcome();
        if (current.ready()) {
            return ApiResponse.ok(current);
        }
        log.info("用户触发重试数据库初始化引导（此前失败：{} {}）",
                current.failure().code(), current.failure().message());
        return ApiResponse.ok(bootstrapService.bootstrap());
    }

    @PostMapping("/exit")
    @Operation(summary = "确认退出应用", description = "调度后端退出，启动器随之结束托管；仅本机回环可用")
    public ResponseEntity<ApiResponse<Void>> exit(HttpServletRequest request) {
        assertLoopback(request);
        log.info("用户确认退出应用（初始化失败后的显式退出）");
        exitExecutor.scheduleExit();
        return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(null));
    }

    private void assertLoopback(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if ("127.0.0.1".equals(remote) || "::1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote)) {
            return;
        }
        throw new BusinessException(ApiCodes.FORBIDDEN, "该操作仅允许从本机发起");
    }
}
