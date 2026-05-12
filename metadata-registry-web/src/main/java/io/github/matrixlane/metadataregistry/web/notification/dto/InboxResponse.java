package io.github.matrixlane.metadataregistry.web.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InboxResponse {

    private List<NotificationRecordResponse> records;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private long unreadCount;
}
