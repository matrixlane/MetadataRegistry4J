package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.system.FactoryResetRequest;
import io.github.matrixlane.metadataregistry.web.service.FactoryResetService;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Tag(name = "0. 系统管理", description = "系统级运维管理接口")
public class SystemController {

    private final FactoryResetService factoryResetService;
    private final SystemSettingService systemSettingService;

    @PostMapping("/factory-reset")
    @Operation(summary = "恢复出厂设置")
    public ApiResponse<Void> factoryReset(@Valid @RequestBody FactoryResetRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        factoryResetService.executeFactoryReset(user.id(), request.password());
        return ApiResponse.ok();
    }

    @GetMapping("/settings/new-user-require-audit")
    @Operation(summary = "获取新注册用户是否需要审核的开关状态")
    public ApiResponse<Boolean> getNewUserRequireAudit() {
        return ApiResponse.ok(systemSettingService.isNewUserRequireAudit());
    }

    @PutMapping("/settings/new-user-require-audit")
    @Operation(summary = "设置新注册用户是否需要审核的开关状态")
    public ApiResponse<Void> setNewUserRequireAudit(@RequestParam boolean requireAudit) {
        systemSettingService.setNewUserRequireAudit(requireAudit);
        return ApiResponse.ok();
    }

    @GetMapping("/settings/allow-user-registration")
    @Operation(summary = "获取是否允许用户自主注册的开关状态")
    public ApiResponse<Boolean> getAllowUserRegistration() {
        return ApiResponse.ok(systemSettingService.isAllowUserRegistration());
    }

    @PutMapping("/settings/allow-user-registration")
    @Operation(summary = "设置是否允许用户自主注册的开关状态")
    public ApiResponse<Void> setAllowUserRegistration(@RequestParam boolean allowRegistration) {
        systemSettingService.setAllowUserRegistration(allowRegistration);
        return ApiResponse.ok();
    }
}
