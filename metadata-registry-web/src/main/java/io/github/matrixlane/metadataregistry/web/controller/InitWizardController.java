package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.auth.AuthService;
import io.github.matrixlane.metadataregistry.web.auth.dto.LoginResponse;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardCommitRequest;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSetupRequest;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardStatusResponse;
import io.github.matrixlane.metadataregistry.web.service.InitWizardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/init-wizard")
@RequiredArgsConstructor
@Tag(name = "0. 系统管理 - 初始化向导", description = "系统首次运行的向导配置接口")
public class InitWizardController {

    private final InitWizardService initWizardService;
    private final AuthService authService;

    @GetMapping("/status")
    @Operation(summary = "查询系统初始化状态")
    public ApiResponse<InitWizardStatusResponse> getStatus() {
        return ApiResponse.ok(initWizardService.getStatus());
    }

    /**
     * Returns the current state of the wizard: NOT_STARTED, IN_PROGRESS, or
     * READY. Used by the frontend to decide whether to resume a previous
     * attempt or start fresh.
     */
    @GetMapping("/wizard-state")
    @Operation(summary = "查询初始化向导状态：NOT_STARTED / IN_PROGRESS / READY")
    public ApiResponse<String> getWizardState() {
        return ApiResponse.ok(initWizardService.getWizardState());
    }

    /**
     * Step 1-4: submit the wizard configuration. Writes business data
     * (registration authority, namespace, registry spec, demo language) but
     * does NOT mark the system as initialized — that requires step 5.
     *
     * If a previous attempt left dirty data, setup() cleans it up first,
     * making this endpoint idempotent.
     *
     * Returns an admin session (JWT with forceChangePassword=true) so the
     * frontend can move to step 5 without a separate login.
     */
    @PostMapping("/setup")
    @Operation(summary = "提交初始化向导配置（步骤 1-4），写入业务数据但不标记为已初始化")
    public ApiResponse<LoginResponse> setup(@Valid @RequestBody InitWizardSetupRequest request) {
        UUID adminId = authService.findAdminId();
        initWizardService.setup(request, adminId);
        // 个人版免登录（personal-no-login-registrar-default）：不签发向导会话令牌，
        // 前端直接进入应用；团队版沿用"免登录+强制改密"的向导会话。
        String edition = initWizardService.getStatus().edition();
        if ("PERSONAL".equals(edition)) {
            return ApiResponse.ok(null);
        }
        return ApiResponse.ok(authService.establishSessionAfterInitialization(adminId));
    }

    /**
     * Step 5: commit. The only operation that flips
     * {@code system.initialized=true} and — TEAM 版 — stores the admin's
     * real password. PERSONAL 版无密码概念，请求体可省略。
     */
    @PostMapping("/commit")
    @Operation(summary = "提交第 5 步：设置管理员密码（团队版）并启用系统")
    public ApiResponse<Void> commit(@RequestBody(required = false) InitWizardCommitRequest request) {
        initWizardService.commit(request);
        // commit 事务已提交、连接已释放后创建 Registry Context（独立事务，失败不阻断）
        initWizardService.createRegistryContextAfterInitialization();
        return ApiResponse.ok();
    }

    /**
     * Abort the wizard. Discards any business data written by setup() and
     * resets the admin password to NO_PASSWORD. Safe to call multiple
     * times, or when the system is fully initialized (no-op).
     */
    @PostMapping("/abort")
    @Operation(summary = "放弃初始化向导：清除已写入的业务数据并重置管理员密码")
    public ApiResponse<Void> abort() {
        initWizardService.abort();
        return ApiResponse.ok();
    }
}
