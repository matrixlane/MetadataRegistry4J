package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.web.auth.dto.ChangePasswordRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.LoginRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.LoginResponse;
import io.github.matrixlane.metadataregistry.web.auth.dto.UserProfileResponse;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "1. 认证", description = "登录、个人资料、修改密码")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "用户名密码登录，返回 JWT token")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户信息")
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(authService.profile(user.id()));
    }

    @PostMapping("/change-password")
    @Operation(summary = "修改当前用户密码")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.id(), request);
        return ApiResponse.ok();
    }
}
