package io.github.matrixlane.metadataregistry.web.dto.scopedidentifier;

import io.github.matrixlane.metadataregistry.ext.identification.VersionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VersionTransitionRequest(
        @NotNull(message = "目标状态不能为空")
        VersionStatus toStatus,

        @Size(max = 1024, message = "变更摘要长度不能超过 1024")
        String changeSummary
) {
}
