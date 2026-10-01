package io.github.matrixlane.metadataregistry.web.dto.individual;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record IndividualRequest(
        @NotBlank(message = "name 不能为空")
        String name,
        String title,
        String mailAddress,
        List<String> emailAddress,
        List<String> phoneNumber) {
}
