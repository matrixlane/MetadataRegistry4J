package io.github.matrixlane.metadataregistry.web.dto.common;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierCreationMode;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;

import java.util.UUID;

public record ItemMetaResponse(
        UUID itemId,
        String preferredDesignation,
        String preferredDefinition,
        String scopedIdentifier,
        UUID namespaceId,
        String namespaceLabel,
        String identifier,
        String version,
        RegistrationStatus registrationStatus,
        UUID administeredItemId,
        UUID registrationAuthorityId,
        String registrationAuthorityName,
        /** 创建时用户选择的标识符生成方式；NULL = 既存记录 / 外部命名空间。 */
        IdentifierCreationMode identifierCreationMode,
        /** 创建时用户选择的版本生成方式；NULL = 既存记录 / 外部命名空间。 */
        IdentifierCreationMode versionCreationMode) {
}