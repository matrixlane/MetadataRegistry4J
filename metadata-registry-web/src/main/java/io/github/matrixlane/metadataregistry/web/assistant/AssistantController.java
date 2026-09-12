package io.github.matrixlane.metadataregistry.web.assistant;

import io.github.matrixlane.metadataregistry.web.assistant.dto.AssistantJobRequest;
import io.github.matrixlane.metadataregistry.web.assistant.dto.AssistantJobResponse;
import io.github.matrixlane.metadataregistry.web.assistant.dto.AssistantTaskView;
import io.github.matrixlane.metadataregistry.web.assistant.dto.ExchangeView;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 协作助手端点。安全规则由既有 SecurityConfig 自动覆盖
 * （GET 需登录，POST/DELETE 需 REGISTRAR/SUPER_ADMIN）。
 *
 * <p>{@code mdr.inference.enabled=false} 时所有端点返回功能未启用错误。
 */
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
@Tag(name = "12. AI 元数据推断", description = "协作任务清单、统一异步作业（多态结果）与生成来源记录")
public class AssistantController {

    private final AssistantJobService jobService;

    @GetMapping("/tasks")
    @Operation(summary = "查询协作任务清单（id、显示名、输入规格、输出种类）")
    public ApiResponse<List<AssistantTaskView>> tasks() {
        return ApiResponse.ok(jobService.tasks().stream()
                .map(task -> new AssistantTaskView(task.id(), task.displayName(),
                        task.inputSpec(), task.outputKind()))
                .toList());
    }

    @PostMapping("/jobs")
    @Operation(summary = "创建协作作业（异步执行，返回作业标识）")
    public ApiResponse<Map<String, UUID>> createJob(@Valid @RequestBody AssistantJobRequest request) {
        UUID jobId = jobService.createJob(request.taskId(), request.itemId(), request.selection(),
                request.params(), request.provider());
        return ApiResponse.ok(Map.of("jobId", jobId));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "查询协作作业状态与多态结果（仅创建者或 SUPER_ADMIN）")
    public ApiResponse<AssistantJobResponse> getJob(@PathVariable UUID jobId) {
        return ApiResponse.ok(AssistantJobResponse.of(jobService.getJob(jobId)));
    }

    @DeleteMapping("/jobs/{jobId}")
    @Operation(summary = "取消协作作业（仅创建者或 SUPER_ADMIN，RUNNING 时终止进程）")
    public ApiResponse<AssistantJobResponse> cancelJob(@PathVariable UUID jobId) {
        return ApiResponse.ok(AssistantJobResponse.of(jobService.cancelJob(jobId)));
    }

    @GetMapping("/debug/exchanges")
    @Operation(summary = "查询最近 AI 交互调试记录（倒序 50 条；本人或 SUPER_ADMIN 全览）")
    public ApiResponse<List<ExchangeView>> debugExchanges() {
        return ApiResponse.ok(jobService.recentExchanges().stream().map(ExchangeView::of).toList());
    }
}
