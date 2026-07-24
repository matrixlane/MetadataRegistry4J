package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.web.auth.dto.AccountCreateRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.AccountUpdateRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.AccountProfileResponse;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "9. 账户管理", description = "本地账号管理（仅 SUPER_ADMIN 可写）")
public class AccountController {

    private final AuthService authService;

    @GetMapping
    @Operation(summary = "分页查询本地账号")
    public ApiResponse<PageResponse<AccountProfileResponse>> list(Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(authService.listUsers(pageable)));
    }

    @PostMapping
    @Operation(summary = "创建账号（仅 SUPER_ADMIN）")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AccountProfileResponse> create(@Valid @RequestBody AccountCreateRequest request) {
        return ApiResponse.ok(authService.createUser(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新账号（仅 SUPER_ADMIN）")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AccountProfileResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody AccountUpdateRequest request,
                                                   @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser.id().equals(id) && !request.enabled()) {
            throw new BusinessException("不能禁用自己的账号");
        }
        return ApiResponse.ok(authService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除账号（仅 SUPER_ADMIN，不允许删除最后一个管理员）")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable UUID id,
                                    @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser.id().equals(id)) {
            throw new BusinessException("不能删除自己的账号");
        }
        authService.deleteUser(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/audit")
    @Operation(summary = "审核与启用账号（仅 SUPER_ADMIN）")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AccountProfileResponse> audit(@PathVariable UUID id, 
                                                     @RequestParam boolean enabled,
                                                     @AuthenticationPrincipal AuthenticatedUser currentUser) {
        if (currentUser.id().equals(id) && !enabled) {
            throw new BusinessException("不能禁用自己的账号");
        }
        return ApiResponse.ok(authService.auditAccount(id, enabled));
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "重置普通用户密码（仅 SUPER_ADMIN）")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> resetPassword(@PathVariable UUID id) {
        String tempPassword = authService.resetPassword(id);
        return ApiResponse.ok(tempPassword);
    }
}
