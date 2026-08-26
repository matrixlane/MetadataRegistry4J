package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.activeregistrar.ActiveRegistrarResponse;
import io.github.matrixlane.metadataregistry.web.dto.activeregistrar.ActiveRegistrarSelectRequest;
import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/active-registrar")
@RequiredArgsConstructor
@Tag(name = "3. 注册管理 - 活动 Registrar 身份", description = "当前账号的活动 Registrar 身份查询与切换")
public class ActiveRegistrarController {

    private final ActiveRegistrarService activeRegistrarService;

    @GetMapping
    @Operation(summary = "查询当前账号的活动 Registrar 身份与全部身份")
    public ApiResponse<ActiveRegistrarResponse> current() {
        UUID accountId = requireAccountId();
        var active = activeRegistrarService.resolveActiveIdentity(accountId).orElse(null);
        return ApiResponse.ok(new ActiveRegistrarResponse(active,
                activeRegistrarService.listIdentities(accountId)));
    }

    @PutMapping
    @Operation(summary = "切换活动 Registrar 身份")
    public ApiResponse<ActiveRegistrarResponse> select(@Valid @RequestBody ActiveRegistrarSelectRequest request) {
        UUID accountId = requireAccountId();
        var active = activeRegistrarService.selectActiveRegistrar(accountId, request.registrarId());
        return ApiResponse.ok(new ActiveRegistrarResponse(active,
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
