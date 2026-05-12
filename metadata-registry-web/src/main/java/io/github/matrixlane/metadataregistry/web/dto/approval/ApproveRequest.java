package io.github.matrixlane.metadataregistry.web.dto.approval;

import jakarta.validation.constraints.Size;

public record ApproveRequest(
        @Size(max = 2000, message = "审批意见最长 2000 字符") String opinion) {
}
