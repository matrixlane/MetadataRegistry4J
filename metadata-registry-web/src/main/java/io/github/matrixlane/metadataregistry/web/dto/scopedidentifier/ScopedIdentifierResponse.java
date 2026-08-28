package io.github.matrixlane.metadataregistry.web.dto.scopedidentifier;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierCreationMode;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.VersionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScopedIdentifierResponse(
        UUID id,
        UUID namespaceId,
        String namespaceLabel,
        String identifier,
        String version,
        VersionStatus versionStatus,
        UUID identifiedItemId,
        String identifiedItemType,
        String identifiedItemLabel,
        LocalDateTime createdAt,
        LocalDateTime publishedAt,
        LocalDateTime deprecatedAt,
        LocalDateTime obsoleteAt,
        /** 创建时用户选择的标识符生成方式；NULL = 既存记录 / 外部命名空间。 */
        IdentifierCreationMode identifierCreationMode,
        /** 创建时用户选择的版本生成方式；NULL = 既存记录 / 外部命名空间。 */
        IdentifierCreationMode versionCreationMode) {
}