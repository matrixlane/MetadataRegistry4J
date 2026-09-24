package io.github.matrixlane.metadataregistry.web.dto.common;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierCreationMode;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ItemRegistrationRequest(
        UUID namespaceId,
        String identifier,
        String version,
        // 顶层请求体已携带指称并各自校验；此处保留字段仅为兼容旧调用方，不再强制非空。
        @Valid
        List<TextLine> designations,
        @Valid
        List<TextLine> definitions,
        /** Segment-template VARIABLE values supplied by the registration form. */
        Map<String, String> variables,
        /** 创建时显式声明的标识符生成方式（可选；NULL 表示由后端按 identifier 字段反推）。 */
        IdentifierCreationMode identifierMode,
        /** 创建时显式声明的版本生成方式（可选；NULL 表示由后端按 version 字段反推）。 */
        IdentifierCreationMode versionMode,
        /** ISO/IEC 11179-3:2023 表 22 registration_status；NULL = 不变更（创建落默认 INCOMPLETE，
         *   生命周期入口态）。显式值仅限 APPLICATION/HISTORICAL 文档态入口标定
         *  （仅限真正新建；其余初始状态一律 1125 拒绝，须经推进流程获得）。 */
        RegistrationStatus registrationStatus,
        /** 表 22 administrative_status；保留在 DTO 中但不再生效，由工作空间流程系统治理。 */
        String administrativeStatus,
        /** 表 22 effective_date；NULL = 不变更（创建落当前时间）。 */
        LocalDateTime effectiveDate,
        /** 表 22 until_date；NULL = 不变更。 */
        LocalDateTime untilDate,
        /** 表 22 administrative_note；NULL = 不变更，空串 = 清空。 */
        String administrativeNote,
        /** 表 22 unresolved_issue；NULL = 不变更，空串 = 清空。 */
        String unresolvedIssue,
        /** 条目解释性注释（explanatory comment）；NULL = 不变更，空串 = 清空。 */
        String explanatoryComment,
        /** 提交信息（SubmissionRecord 联系人与所属组织）；NULL = 服务端按提交者默认联系人预填。 */
        @Valid
        SubmissionInfo submission,
        /** 主管信息（Stewardship_Record 联系人与所属组织）；NULL = 不变更（不清除）。 */
        @Valid
        StewardshipInfo stewardship) {

    /** Backward-compatible constructor without segment variables.
     *  归属注册机构不在 API 请求体中携带（active-registrar-ownership）：
     *  归属由活动 Registrar 身份推导，或系统/联邦路径显式指定。 */
    public ItemRegistrationRequest(UUID namespaceId, String identifier, String version,
                                   List<TextLine> designations, List<TextLine> definitions) {
        this(namespaceId, identifier, version, designations, definitions, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    /** Backward-compatible constructor with segment variables but no creation modes. */
    public ItemRegistrationRequest(UUID namespaceId, String identifier, String version,
                                   List<TextLine> designations, List<TextLine> definitions,
                                   Map<String, String> variables) {
        this(namespaceId, identifier, version, designations, definitions, variables, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    /** Backward-compatible constructor without submission info (no creation modes). */
    public ItemRegistrationRequest(UUID namespaceId, String identifier, String version,
                                   List<TextLine> designations, List<TextLine> definitions,
                                   Map<String, String> variables, IdentifierCreationMode identifierMode,
                                   IdentifierCreationMode versionMode, RegistrationStatus registrationStatus,
                                   String administrativeStatus, LocalDateTime effectiveDate, LocalDateTime untilDate,
                                   String administrativeNote, String unresolvedIssue, String explanatoryComment) {
        this(namespaceId, identifier, version, designations, definitions, variables, identifierMode, versionMode,
                registrationStatus, administrativeStatus, effectiveDate, untilDate, administrativeNote,
                unresolvedIssue, explanatoryComment, null, null);
    }

    /**
     * 反推标识符/版本的创建方式：用户未显式提供值时按 AUTO 处理（命名空间策略负责生成），
     * 否则按 MANUAL 处理（保留用户的显式输入）。供后端在请求未携带 mode 时使用。
     */
    public static IdentifierCreationMode resolveMode(String userSupplied) {
        return (userSupplied == null || userSupplied.isBlank())
                ? IdentifierCreationMode.AUTO
                : IdentifierCreationMode.MANUAL;
    }
}