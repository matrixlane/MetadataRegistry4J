package io.github.matrixlane.metadataregistry.web.notification.controller;

import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import io.github.matrixlane.metadataregistry.web.notification.dto.InboxResponse;
import io.github.matrixlane.metadataregistry.web.notification.dto.NotificationPreferenceRequest;
import io.github.matrixlane.metadataregistry.web.notification.dto.NotificationPreferenceResponse;
import io.github.matrixlane.metadataregistry.web.notification.dto.NotificationRecordResponse;
import io.github.matrixlane.metadataregistry.web.notification.entity.NotificationRecord;
import io.github.matrixlane.metadataregistry.web.notification.service.NotificationPreferenceService;
import io.github.matrixlane.metadataregistry.web.notification.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "通知管理", description = "站内信收件箱与通知偏好设置")
public class NotificationController {

    private final NotificationQueryService queryService;
    private final NotificationPreferenceService preferenceService;

    @GetMapping("/inbox")
    @Operation(summary = "查询收件箱", description = "分页查询当前用户的站内信通知")
    public ApiResponse<InboxResponse> getInbox(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = currentUserId(auth);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<NotificationRecord> recordPage = queryService.getInbox(userId, pageable);
        long unreadCount = queryService.countUnread(userId);

        List<NotificationRecordResponse> records = recordPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        InboxResponse response = new InboxResponse(
                records,
                recordPage.getTotalElements(),
                recordPage.getTotalPages(),
                recordPage.getNumber(),
                unreadCount
        );
        return ApiResponse.ok(response);
    }

    @GetMapping("/unread/count")
    @Operation(summary = "未读通知数量", description = "查询当前用户的未读站内信数量")
    public ApiResponse<Long> getUnreadCount(Authentication auth) {
        String userId = currentUserId(auth);
        return ApiResponse.ok(queryService.countUnread(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "通知详情", description = "查询单条通知详情")
    public ApiResponse<NotificationRecordResponse> getNotification(@PathVariable UUID id) {
        NotificationRecord record = queryService.getNotification(id);
        return ApiResponse.ok(toResponse(record));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记已读", description = "将通知标记为已读")
    public ApiResponse<NotificationRecordResponse> markAsRead(@PathVariable UUID id) {
        NotificationRecord record = queryService.markAsRead(id);
        return ApiResponse.ok(toResponse(record));
    }

    @GetMapping("/preferences")
    @Operation(summary = "查询通知偏好", description = "查询当前用户的所有通知偏好设置")
    public ApiResponse<List<NotificationPreferenceResponse>> getPreferences(Authentication auth) {
        String userId = currentUserId(auth);
        return ApiResponse.ok(preferenceService.getPreferences(userId));
    }

    @PutMapping("/preferences")
    @Operation(summary = "更新通知偏好", description = "更新指定通知类型和渠道的开关状态")
    public ApiResponse<NotificationPreferenceResponse> updatePreference(
            Authentication auth,
            @Valid @RequestBody NotificationPreferenceRequest request) {
        String userId = currentUserId(auth);
        return ApiResponse.ok(preferenceService.updatePreference(userId, request));
    }

    private String currentUserId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return "anonymous";
        }
        return auth.getName();
    }

    private NotificationRecordResponse toResponse(NotificationRecord record) {
        return new NotificationRecordResponse(
                record.getId(),
                record.getRecipientId(),
                record.getNotificationType(),
                record.getChannel(),
                record.getTitle(),
                record.getContent(),
                record.getStatus(),
                record.getRetryCount(),
                record.getSentAt(),
                record.getCreatedAt(),
                record.getRelatedBusinessId()
        );
    }
}
