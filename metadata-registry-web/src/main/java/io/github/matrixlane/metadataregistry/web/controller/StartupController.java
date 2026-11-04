package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.startup.StartupStatusResponse;
import io.github.matrixlane.metadataregistry.web.startup.StartupReadinessIndicator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用启动就绪状态（前端启动等待界面使用）。
 *
 * <p>Endpoint: {@code GET /api/v1/startup/status}（匿名可访问）。
 * init-phase-database-free 之后，数据库初始化引导失败时额外返回 failure
 * （阶段、原因码与可读描述），调用方可区分"仍在启动"与"初始化失败"。
 */
@RestController
@RequestMapping("/api/v1/startup")
@RequiredArgsConstructor
@Tag(name = "0. 系统管理 - 启动状态", description = "应用启动就绪状态（前端启动等待界面使用）")
public class StartupController {

    private final StartupReadinessIndicator readinessIndicator;

    @GetMapping("/status")
    @Operation(summary = "查询应用启动就绪状态",
            description = "ready=true 表示应用启动全部阶段（含数据初始化）完成；"
                    + "数据库初始化引导失败时返回 ready=false 与 failure（阶段、原因码、可读描述）；匿名可访问")
    public ApiResponse<StartupStatusResponse> getStatus() {
        return ApiResponse.ok(new StartupStatusResponse(
                readinessIndicator.isReady(), readinessIndicator.failure()));
    }
}
