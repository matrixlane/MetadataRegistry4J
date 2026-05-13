package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult;
import io.github.matrixlane.metadataregistry.web.entity.DataQualityTask;
import io.github.matrixlane.metadataregistry.web.service.dataquality.DataQualityAssessmentService;
import io.github.matrixlane.metadataregistry.web.service.dataquality.JdbcDataQualityExecutor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 数据质量初评 REST 控制器 (WI-016c).
 *
 * <p>提供对遗留系统表的数据质量评估功能，包括空值率统计、
 * 数据分布分析和异常值检测。
 *
 * <p>遵循"轻量级初评"定位，结果与元数据草稿关联存储。
 */
@RestController
@RequestMapping("/api/v1/data-quality")
@Tag(name = "16c. 数据质量初评", description = "WI-016c — 数据质量初评模块")
public class DataQualityController {

    private static final Logger log = LoggerFactory.getLogger(DataQualityController.class);

    private final DataQualityAssessmentService assessmentService;

    public DataQualityController(
            DataQualityAssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    // ─────────────────────────────────────────────────────────────────
    // 1. 同步评估（小表）
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/assess")
    @Operation(summary = "同步评估数据质量",
            description = "对小表（< " + (10000) + " 行）执行同步数据质量评估，" +
                    "返回空值率统计、数据分布分析和异常值检测结果。需提供目标数据库连接信息。")
    public ApiResponse<DataQualityAssessmentResult> assess(
            @RequestBody AssessRequest request,
            @AuthenticationPrincipal UserDetails user) {

        if (request.connectionUrl == null || request.connectionUrl.isBlank()) {
            return ApiResponse.error(ApiCodes.BAD_REQUEST, "connectionUrl is required");
        }

        JdbcDataQualityExecutor executor = createExecutor(request);
        DataQualityAssessmentResult result = assessmentService.assess(executor, request.request);
        return ApiResponse.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. 异步评估（大表）
    // ─────────────────────────────────────────────────────────────────

    @PostMapping("/assess/async")
    @Operation(summary = "创建异步评估任务",
            description = "对大表创建异步评估任务，返回任务 ID。可通过 GET /tasks/{id} 查询进度。")
    public ApiResponse<DataQualityTask> createAsyncAssessment(
            @RequestBody AssessRequest request,
            @AuthenticationPrincipal UserDetails user) {

        if (request.connectionUrl == null || request.connectionUrl.isBlank()) {
            return ApiResponse.error(ApiCodes.BAD_REQUEST, "connectionUrl is required");
        }

        JdbcDataQualityExecutor executor = createExecutor(request);
        UUID userId = parseUserId(user);

        DataQualityTask task = assessmentService.createAsyncAssessment(
                executor, request.request, userId);

        // Trigger async execution
        assessmentService.executeAsyncAssessment(task.getId(), executor);

        return ApiResponse.ok(task);
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. 任务进度查询
    // ─────────────────────────────────────────────────────────────────

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "查询评估任务状态",
            description = "查询异步评估任务的进度和状态。完成时 resultJson 包含评估结果。")
    public ApiResponse<DataQualityTask> getTask(@PathVariable UUID taskId) {
        DataQualityTask task = assessmentService.getTask(taskId);
        return ApiResponse.ok(task);
    }

    @GetMapping("/tasks")
    @Operation(summary = "查询用户的所有评估任务",
            description = "查询当前用户的所有数据质量评估任务，按创建时间倒序排列。")
    public ApiResponse<List<DataQualityTask>> getUserTasks(
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = parseUserId(user);
        List<DataQualityTask> tasks = assessmentService.getUserTasks(userId);
        return ApiResponse.ok(tasks);
    }

    // ─────────────────────────────────────────────────────────────────
    // Request DTO
    // ─────────────────────────────────────────────────────────────────

    /**
     * 评估请求（含数据库连接信息）.
     */
    public record AssessRequest(
            String connectionUrl,
            String username,
            String password,
            DataQualityAssessmentRequest request
    ) {}

    // ─────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────

    private JdbcDataQualityExecutor createExecutor(AssessRequest request) {
        return new JdbcDataQualityExecutor(
                request.connectionUrl(),
                request.username() != null ? request.username() : "",
                request.password() != null ? request.password() : "",
                JdbcDataQualityExecutor.DatabaseType.SQL_SERVER);
    }

    private UUID parseUserId(UserDetails user) {
        if (user == null) {
            return UUID.randomUUID(); // fallback for unauthenticated access
        }
        // In MVP, use a deterministic UUID based on username
        // Full integration with auth module to be done in V1
        return UUID.nameUUIDFromBytes(user.getUsername().getBytes());
    }
}
