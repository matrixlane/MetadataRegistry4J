package io.github.matrixlane.metadataregistry.web.dto.export;

import com.fasterxml.jackson.annotation.JsonFilter;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 注册元数据导出项 DTO
 *
 * <p>采用 JSON Schema v1 结构，字段命名遵循 camelCase。
 * 支持字段白名单筛选（通过 Jackson {@code @JsonFilter}）。
 */
@JsonFilter("exportFilter")
public record AdministeredItemExport(
        UUID id,
        String itemType,
        String itemLabel,
        String itemDefinition,
        List<ScopedIdentifierExport> identifiers,
        RegistrationStateExport registrationState,
        RegistrationAuthorityExport registrationAuthority,
        StewardshipExport stewardship,
        List<String> submissionOrganizations,
        LocalDateTime creationDateTime,
        LocalDateTime lastChangeDateTime,
        String changeDescription,
        String explanatoryComment,
        String origin
) {

    public record ScopedIdentifierExport(
            String identifier,
            String version,
            UUID namespaceId,
            String namespaceLabel
    ) {
    }

    public record RegistrationStateExport(
            RegistrationStatus status,
            LocalDateTime effectiveDate,
            LocalDateTime untilDate,
            String administrativeNote,
            String unresolvedIssue,
            String administrativeStatus
    ) {
    }

    public record RegistrationAuthorityExport(
            UUID id,
            String name
    ) {
    }

    public record StewardshipExport(
            UUID organizationId,
            String organizationName,
            UUID contactId,
            String contactName
    ) {
    }
}
