package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceConfigRequest;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceConfigView;
import io.github.matrixlane.metadataregistry.web.dto.datasource.DatasourceTestResult;
import io.github.matrixlane.metadataregistry.web.service.DatasourceConfigService;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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
    private final SystemSettingService systemSettingService;

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

    /**
     * 初始化向导阶段（未初始化）匿名可用；已初始化后必须是超级管理员。
     */
    private void guardWhenInitialized() {
        if (!systemSettingService.isInitialized()) {
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
