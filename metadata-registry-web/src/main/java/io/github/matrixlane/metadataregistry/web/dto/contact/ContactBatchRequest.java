package io.github.matrixlane.metadataregistry.web.dto.contact;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ContactBatchRequest(
    @NotEmpty(message = "用户ID列表不能为空")
    List<UUID> appUserIds,
    
    @NotNull(message = "组织ID不能为空")
    UUID organizationId,
    
    @NotNull(message = "角色名不能为空")
    String roleName // Steward, Submitter, etc.
) {}
