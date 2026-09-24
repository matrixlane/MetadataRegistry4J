package io.github.matrixlane.metadataregistry.web.dto.common;

import io.github.matrixlane.metadataregistry.ext.identification.IdentifierCreationMode;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;

import java.time.LocalDateTime;
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
        IdentifierCreationMode versionCreationMode,
        /** Registration_State（11179-3:2023 表 22）回显：生效日期。 */
        LocalDateTime stateEffectiveDate,
        /** Registration_State 回显：截止日期。 */
        LocalDateTime stateUntilDate,
        /** Registration_State 回显：管理状态（自由文本）。 */
        String administrativeStatus,
        /** Registration_State 回显：管理备注。 */
        String administrativeNote,
        /** Registration_State 回显：未决问题。 */
        String unresolvedIssue,
        /** Stewardship_Record 回显：主管记录 ID。 */
        UUID stewardshipRecordId,
        /** Stewardship_Record 回显：主管联系人 ID。 */
        UUID stewardshipContactId,
        /** Stewardship_Record 回显：主管组织 ID。 */
        UUID stewardshipOrganizationId) {
}