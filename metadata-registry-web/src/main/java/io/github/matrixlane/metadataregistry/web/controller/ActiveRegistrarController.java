package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.activeregistrar.ActiveRegistrarResponse;
import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/active-registrar")
@RequiredArgsConstructor
@Tag(name = "3. 注册管理 - 活动 Registrar 身份", description = "当前账号的 Registrar 身份查询（激活状态保存在浏览器端）")
public class ActiveRegistrarController {

    private final ActiveRegistrarService activeRegistrarService;

    @GetMapping
    @Operation(summary = "查询当前账号的 Registrar 身份列表与默认注册人员")
    public ApiResponse<ActiveRegistrarResponse> current() {
        UUID accountId = requireAccountId();
        return ApiResponse.ok(new ActiveRegistrarResponse(
                activeRegistrarService.defaultRegistrarIdOf(accountId).orElse(null),
                activeRegistrarService.listIdentities(accountId)));
    }

    private UUID requireAccountId() {
        UUID accountId = activeRegistrarService.currentAccountIdOrNull();
        if (accountId == null) {
            throw new AccessDeniedException("Access denied: Unauthenticated.");
        }
        return accountId;
    }
}
