package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStatusProvider;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceApplyRequest;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceConfirmResponse;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceConfigRequest;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceConfigView;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceRestartAcceptedResponse;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceRestartCapabilityResponse;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceRestartStatusResponse;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceTestResult;
import io.github.matrixlane.metadataregistry.web.service.DatasourceConfigService;
import io.github.matrixlane.metadataregistry.web.service.DatasourceRestartConflictException;
import io.github.matrixlane.metadataregistry.web.service.DatasourceRestartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 数据源配置接口。
 *
 * <p>连接测试在未初始化时匿名可用（初始化向导需要）；已初始化后仅超级管理员。
 * 配置的读取与变更始终仅超级管理员（SecurityConfig URL 规则）。
 */
@RestController
@RequestMapping("/api/v1/datasource")
@RequiredArgsConstructor
@Tag(name = "0. 系统管理 - 数据源配置", description = "数据库连接测试与数据源配置接口")
public class DatasourceConfigController {

    private final DatasourceConfigService datasourceConfigService;
    private final DatasourceRestartService datasourceRestartService;
    private final io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStatusProvider statusProvider;

    @PostMapping("/test-connection")
    @Operation(summary = "数据源连接测试（不落地）")
    public ApiResponse<DatasourceTestResult> testConnection(@Valid @RequestBody DatasourceConfigRequest request) {
        guardWhenInitialized();
        return ApiResponse.ok(datasourceConfigService.testConnection(request));
    }

    @GetMapping("/config")
    @Operation(summary = "查看当前数据源配置（脱敏）")
    public ApiResponse<DatasourceConfigView> currentConfig() {
        guardWhenInitialized();
        return ApiResponse.ok(datasourceConfigService.currentConfig());
    }

    @PutMapping("/config")
    @Operation(summary = "保存数据源配置（写入 application-local.yml，重启生效）")
    public ApiResponse<Void> saveConfig(@Valid @RequestBody DatasourceConfigRequest request) {
        guardWhenInitialized();
        datasourceConfigService.saveConfig(request);
        return ApiResponse.ok();
    }

    @GetMapping("/restart-capability")
    @Operation(summary = "查询当前环境是否支持保存后自动重启")
    public ApiResponse<DatasourceRestartCapabilityResponse> restartCapability(HttpServletRequest request) {
        guardWhenInitialized();
        return ApiResponse.ok(datasourceRestartService.capability(request.getRemoteAddr()));
    }

    @PostMapping("/config/apply")
    @Operation(summary = "保存数据源配置并由受管启动器自动重启")
    public ResponseEntity<ApiResponse<DatasourceRestartAcceptedResponse>> applyConfig(
            @Valid @RequestBody DatasourceApplyRequest request,
            HttpServletRequest httpRequest) {
        guardWhenInitialized();
        guardNotProxied(httpRequest);
        DatasourceRestartAcceptedResponse accepted = datasourceRestartService.apply(
                request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(accepted));
    }

    /**
     * 初始化向导"保持当前数据源并继续"的路径（init-phase-database-free）。
     *
     * <p>数据源已确认（状态记录 datasourceConfigured=true 且指纹与当前生效配置
     * 一致，含完整相与向导相第 2 步重启确认后的状态）：直接返回无需重启，
     * 向导继续。未确认（向导相首次、配置漂移）：以当前生效配置创建重启事务
     * ——保存即确认（写状态记录与指纹），由随后的自动重启完成数据库初始化。
     * 凭据不回传，使用凭据的数据库需改走"配置新的数据源"。
     */
    @PostMapping("/config/confirm-current")
    @Operation(summary = "确认当前数据源（保持当前数据源并继续）",
            description = "已确认直接返回无需重启；未确认走自动重启完成数据库初始化")
    public ResponseEntity<ApiResponse<DatasourceConfirmResponse>> confirmCurrent(
            @RequestParam(required = false) String edition,
            HttpServletRequest httpRequest) {
        guardWhenInitialized();
        guardNotProxied(httpRequest);
        if (statusProvider.bootstrapEnabled() && statusProvider.isDatasourceConfirmed()) {
            // 已确认（含向导相第 2 步保存并重启成功后的状态）：放行，不再重启。
            // 缺失此分支时向导相每次点击都会重复重启，永远走不出第 2 步。
            return ResponseEntity.ok(ApiResponse.ok(new DatasourceConfirmResponse(false, null)));
        }
        DatasourceConfigView current = datasourceConfigService.currentConfig();
        DatasourceConfigRequest config = new DatasourceConfigRequest(
                current.vendor(),
                current.host(),
                current.port(),
                current.database(),
                current.filePath(),
                null,
                null,
                current.schema(),
                edition);
        DatasourceRestartAcceptedResponse accepted = datasourceRestartService.apply(
                new DatasourceApplyRequest(java.util.UUID.randomUUID(), config),
                httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(new DatasourceConfirmResponse(true, accepted.restartId())));
    }

    @GetMapping("/restarts/{restartId}")
    @Operation(summary = "查询数据源自动重启事务状态")
    public ApiResponse<DatasourceRestartStatusResponse> restartStatus(
            @PathVariable UUID restartId, HttpServletRequest request) {
        guardWhenInitialized();
        guardNotProxied(request);
        return ApiResponse.ok(datasourceRestartService.status(restartId, request.getRemoteAddr()));
    }

    /**
     * 自动应用只允许本机直连发起。经本机反向代理或端口转发进来的远程请求，
     * 对端地址同样会呈现为 127.0.0.1；带转发头的请求一律拒绝，避免远程借代理
     * 获得"改数据源并重启后端"的能力。
     */
    private void guardNotProxied(HttpServletRequest request) {
        if (request.getHeader("X-Forwarded-For") != null
                || request.getHeader("Forwarded") != null
                || request.getHeader("X-Real-IP") != null) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "不接受经代理转发的数据源请求");
        }
    }

    @ExceptionHandler(DatasourceRestartConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleRestartConflict(DatasourceRestartConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ApiCodes.CONFLICT, ex.getMessage()));
    }

    /**
     * 初始化向导阶段（未初始化）匿名可用；已初始化后必须是超级管理员。
     * 未初始化判定经 statusProvider：新架构下读本地状态记录（零数据库查询）。
     */
    private void guardWhenInitialized() {
        if (!statusProvider.isInitialized()) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isSuperAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
        if (!isSuperAdmin) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "无权访问此资源");
        }
    }
}
