package io.github.matrixlane.metadataregistry.web.dto.common;

import io.github.matrixlane.metadataregistry.ext.identification.IdentifierCreationMode;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.List;
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
        UUID stewardshipOrganizationId,
        /** 该 AdministeredItem 经引用关联（11179-3:2023 §9.5.2）关联的参考文档 id；按页批量取回。 */
        List<UUID> referenceDocumentIds,
        /** 该 AdministeredItem 关联的 SubmissionRecord 列表（按 id 降序；align-submission-record-to-iso）。
         *  编辑界面"提交记录"只读面板的数据源；历史脏数据标 SYSTEM_DRAFT，新条目均 USER_SUBMISSION。 */
        List<SubmissionRecordSummary> submissionRecordSummaries) {
}