package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.web.auth.dto.AppUserCreateRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.AppUserUpdateRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.UserProfileResponse;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/app-users")
@RequiredArgsConstructor
@Tag(name = "9. 用户管理", description = "本地账号管理（仅 ADMIN 角色可写）")
public class AppUserController {

    private final AuthService authService;

    @GetMapping
    @Operation(summary = "分页查询本地账号")
    public ApiResponse<PageResponse<UserProfileResponse>> list(Pageable pageable) {
        return ApiResponse.ok(PageResponse.of(authService.listUsers(pageable)));
    }

    @PostMapping
    @Operation(summary = "创建账号（仅 ADMIN）")
    public ApiResponse<UserProfileResponse> create(@Valid @RequestBody AppUserCreateRequest request) {
        return ApiResponse.ok(authService.createUser(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新账号（仅 ADMIN）")
    public ApiResponse<UserProfileResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody AppUserUpdateRequest request) {
        return ApiResponse.ok(authService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除账号（仅 ADMIN，不允许删除最后一个管理员）")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        authService.deleteUser(id);
        return ApiResponse.ok();
    }
}
