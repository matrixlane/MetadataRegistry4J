package io.github.matrixlane.metadataregistry.web.dto.common;

import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierCreationMode;
import jakarta.validation.Valid;

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
        IdentifierCreationMode versionMode) {

    /** Backward-compatible constructor without segment variables.
     *  归属注册机构不在 API 请求体中携带（active-registrar-ownership）：
     *  归属由活动 Registrar 身份推导，或系统/联邦路径显式指定。 */
    public ItemRegistrationRequest(UUID namespaceId, String identifier, String version,
                                   List<TextLine> designations, List<TextLine> definitions) {
        this(namespaceId, identifier, version, designations, definitions, null, null, null);
    }

    /** Backward-compatible constructor with segment variables but no creation modes. */
    public ItemRegistrationRequest(UUID namespaceId, String identifier, String version,
                                   List<TextLine> designations, List<TextLine> definitions,
                                   Map<String, String> variables) {
        this(namespaceId, identifier, version, designations, definitions, variables, null, null);
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