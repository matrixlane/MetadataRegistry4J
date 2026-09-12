package io.github.matrixlane.metadataregistry.web.inference;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceJobRequest;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceJobResponse;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceProviderOption;
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
 * AI 元数据推断端点。安全规则由既有 SecurityConfig 自动覆盖
 * （GET 需登录，POST/DELETE 需 REGISTRAR/SUPER_ADMIN）。
 *
 * <p>{@code mdr.inference.enabled=false} 时所有端点返回功能未启用错误。
 */
@RestController
@RequestMapping("/api/v1/inference")
@RequiredArgsConstructor
@Tag(name = "12. AI 元数据推断", description = "以既有元数据推断缺失定义/指称候选（异步作业 + 轮询）")
public class InferenceController {

    private final InferenceJobService jobService;

    @GetMapping("/providers")
    @Operation(summary = "查询推断提供方选项（仅已启用且可用）")
    public ApiResponse<List<InferenceProviderOption>> providers() {
        return ApiResponse.ok(jobService.availableProviderOptions());
    }

    @PostMapping("/items/{itemId}/jobs")
    @Operation(summary = "创建推断作业（异步执行，返回作业标识）")
    public ApiResponse<Map<String, UUID>> createJob(@PathVariable UUID itemId,
                                                    @Valid @RequestBody InferenceJobRequest request) {
        UUID jobId = jobService.createJob(itemId, request);
        return ApiResponse.ok(Map.of("jobId", jobId));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "查询推断作业状态与候选结果（仅创建者或 SUPER_ADMIN）")
    public ApiResponse<InferenceJobResponse> getJob(@PathVariable UUID jobId) {
        return ApiResponse.ok(InferenceJobResponse.of(jobService.getJob(jobId)));
    }

    @DeleteMapping("/jobs/{jobId}")
    @Operation(summary = "取消推断作业（仅创建者或 SUPER_ADMIN，RUNNING 时终止进程）")
    public ApiResponse<InferenceJobResponse> cancelJob(@PathVariable UUID jobId) {
        return ApiResponse.ok(InferenceJobResponse.of(jobService.cancelJob(jobId)));
    }
}
