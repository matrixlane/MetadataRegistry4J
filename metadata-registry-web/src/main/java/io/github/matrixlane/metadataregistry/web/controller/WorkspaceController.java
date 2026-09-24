package io.github.matrixlane.metadataregistry.web.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.common.PageResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionInfo;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceItemResponse;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceItemView;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSubmitRequest;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSubmitResult;
import io.github.matrixlane.metadataregistry.web.dto.workspace.WorkspaceSummaryResponse;
import io.github.matrixlane.metadataregistry.web.service.ActiveRegistrarService;
import io.github.matrixlane.metadataregistry.web.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 工作空间控制器。
 */
@RestController
@RequestMapping("/api/v1/workspace")
@RequiredArgsConstructor
@Tag(name = "9. 工作空间", description = "提交者与注册人员的工作空间视图与行级操作")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final ActiveRegistrarService activeRegistrarService;

    @GetMapping("/summary")
    @Operation(summary = "工作空间三视图计数", description = "返回草稿、待审核、已拒绝三个视图的计数")
    public ApiResponse<WorkspaceSummaryResponse> summary() {
        return ApiResponse.ok(workspaceService.getSummary(activeRegistrarService.currentAccountIdOrNull()));
    }

    @GetMapping("/items")
    @Operation(summary = "工作空间列表", description = "按视图（DRAFTS/PENDING/REJECTED）分页返回管理项行")
    public ApiResponse<PageResponse<WorkspaceItemResponse>> items(
            @RequestParam WorkspaceItemView view,
            Pageable pageable) {
        return ApiResponse.ok(workspaceService.listItems(view, pageable,
                activeRegistrarService.currentAccountIdOrNull()));
    }

    @PostMapping("/submit")
    @Operation(summary = "批量提交草稿管理项", description = "仅管理状态为 DRAFT 且归属当前用户的管理项可提交；返回逐条结果")
    public ApiResponse<List<WorkspaceSubmitResult>> submit(
            @Valid @RequestBody WorkspaceSubmitRequest request) {
        return ApiResponse.ok(workspaceService.submit(request,
                activeRegistrarService.currentAccountIdOrNull()));
    }

    @PostMapping("/request-candidate/{itemId}")
    @Operation(summary = "请求成为候选", description = "INCOMPLETE 状态、具有主管记录且归属当前用户的管理项可直接推进为 CANDIDATE")
    public ApiResponse<Void> requestCandidate(
            @PathVariable UUID itemId,
            @RequestBody(required = false) SubmissionInfo submission) {
        workspaceService.requestCandidate(itemId, submission,
                activeRegistrarService.currentAccountIdOrNull());
        return ApiResponse.ok();
    }

    @PostMapping("/rollback/{itemId}")
    @Operation(summary = "回退管理项", description = "将归属当前用户的管理项回退到最近基线快照")
    public ApiResponse<Void> rollback(@PathVariable UUID itemId) {
        workspaceService.rollback(itemId, activeRegistrarService.currentAccountIdOrNull());
        return ApiResponse.ok();
    }
}
