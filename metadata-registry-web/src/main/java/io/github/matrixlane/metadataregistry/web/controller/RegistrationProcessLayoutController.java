package io.github.matrixlane.metadataregistry.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.service.RegistrationProcessLayoutService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册流程画布布局端点。
 *
 * <p>安全由 SecurityConfig 显式规则覆盖：GET 登录可读（用于渲染恢复），
 * PUT 限 SUPER_ADMIN（保存入口仅系统管理员可见）。</p>
 */
@RestController
@RequestMapping("/api/v1/registration-process/layout")
@RequiredArgsConstructor
public class RegistrationProcessLayoutController {

    private final RegistrationProcessLayoutService layoutService;

    @GetMapping
    @Operation(summary = "读取注册流程画布布局（登录可读；文件由启动播种预置，可手工编辑）")
    public ApiResponse<JsonNode> get() {
        return ApiResponse.ok(layoutService.read());
    }

    @PutMapping
    @Operation(summary = "保存注册流程画布布局（仅 SUPER_ADMIN）")
    public ApiResponse<Void> save(@RequestBody JsonNode body) {
        layoutService.save(body);
        return ApiResponse.ok();
    }
}
