package io.github.matrixlane.metadataregistry.web.dto.organization;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record OrganizationRequest(
        @NotBlank(message = "name 不能为空")
        String name,
        String mailAddress,
        List<String> emailAddress,
        List<String> phoneNumber,
        String webAddress) {
}
