package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.registration.RegistrationProcessResponse;
import io.github.matrixlane.metadataregistry.web.service.RegistrationProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册流程定义只读端点（registration-process-designer）。
 *
 * <p>面向注册流程展示页：当前注册机构的完整流程定义（状态 + 迁移 + 审核配置），
 * 所有登录用户可读（SecurityConfig：GET /api/v1/** authenticated）。</p>
 */
@RestController
@RequestMapping("/api/v1/registration-process")
@RequiredArgsConstructor
@Tag(name = "8. 注册项生命周期", description = "AdministeredItem 列表与状态迁移")
public class RegistrationProcessController {

    private final RegistrationProcessService registrationProcessService;

    @GetMapping
    @Operation(summary = "当前注册机构的注册流程定义",
            description = "返回迁移规则全集（含审核点、审核人模式、通过规则），供注册流程页只读展示；登录即可读")
    public ApiResponse<RegistrationProcessResponse> get() {
        return ApiResponse.ok(registrationProcessService.getCurrentProcess());
    }
}
