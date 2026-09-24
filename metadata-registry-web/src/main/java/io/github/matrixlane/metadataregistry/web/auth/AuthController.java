package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.web.auth.dto.ChangePasswordRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.LoginRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.LoginResponse;
import io.github.matrixlane.metadataregistry.web.auth.dto.AccountProfileResponse;
import io.github.matrixlane.metadataregistry.web.auth.dto.AccountCreateRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.AvatarRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.AvatarResponse;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import io.github.matrixlane.metadataregistry.web.dto.individual.IndividualRequest;
import io.github.matrixlane.metadataregistry.web.dto.individual.IndividualResponse;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "1. 认证", description = "登录、个人资料、修改密码")
public class AuthController {

    private final AuthService authService;
    private final SystemSettingService systemSettingService;

    @PostMapping("/login")
    @Operation(summary = "用户名密码登录，返回 JWT token")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户信息")
    public ApiResponse<AccountProfileResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        AccountProfileResponse profile = authService.profile(user.id());
        AccountProfileResponse sessionProfile = new AccountProfileResponse(
                profile.id(),
                profile.username(),
                profile.sysRole(),
                profile.enabled(),
                profile.createdAt(),
                profile.lastLoginAt(),
                profile.individualId(),
                profile.isRegistrar(),
                profile.hasContact(),
                profile.profilePrompt(),
                user.forceChangePassword(),
                user.emergencyLogin(),
                user.tempPasswordUsed()
        );
        return ApiResponse.ok(sessionProfile);
    }

    @PostMapping("/register")
    @Operation(summary = "用户自助注册")
    public ApiResponse<AccountProfileResponse> register(@Valid @RequestBody AccountCreateRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @GetMapping("/me/individual")
    @Operation(summary = "获取当前登录用户的个人信息")
    public ApiResponse<IndividualResponse> getMyIndividual(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(authService.getMyIndividual(user.id()));
    }

    @PutMapping("/me/individual")
    @Operation(summary = "设置或更新当前登录用户的个人信息")
    public ApiResponse<IndividualResponse> updateMyIndividual(@AuthenticationPrincipal AuthenticatedUser user,
                                                               @Valid @RequestBody IndividualRequest request) {
        return ApiResponse.ok(authService.updateMyIndividual(user.id(), request));
    }

    @GetMapping("/me/individual/avatar")
    @Operation(summary = "获取当前登录用户的头像（dataUrl，未设置时为 null）")
    public ApiResponse<AvatarResponse> getMyAvatar(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(new AvatarResponse(authService.getMyAvatar(user.id())));
    }

    @PutMapping("/me/individual/avatar")
    @Operation(summary = "上传当前登录用户的头像（data URL，PNG/JPEG/WebP，≤2MB）")
    public ApiResponse<Void> updateMyAvatar(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody AvatarRequest request) {
        authService.updateMyAvatar(user.id(), request.dataUrl());
        return ApiResponse.ok();
    }

    @DeleteMapping("/me/individual/avatar")
    @Operation(summary = "删除当前登录用户的头像")
    public ApiResponse<Void> deleteMyAvatar(@AuthenticationPrincipal AuthenticatedUser user) {
        authService.deleteMyAvatar(user.id());
        return ApiResponse.ok();
    }

    @PostMapping("/change-password")
    @Operation(summary = "修改当前用户密码")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.id(), request, user.emergencyLogin());
        return ApiResponse.ok();
    }

    @GetMapping("/allow-registration")
    @Operation(summary = "获取当前系统是否允许自主注册的开关状态")
    public ApiResponse<Boolean> getAllowRegistration() {
        return ApiResponse.ok(systemSettingService.isAllowUserRegistration());
    }
}
