package io.github.matrixlane.metadataregistry.web.dto.common;

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
        Map<String, String> variables) {

    /** Backward-compatible constructor without segment variables. 
     *  归属注册机构不在 API 请求体中携带（active-registrar-ownership）：
     *  归属由活动 Registrar 身份推导，或系统/联邦路径显式指定。 */
    public ItemRegistrationRequest(UUID namespaceId, String identifier, String version,
                                   List<TextLine> designations, List<TextLine> definitions) {
        this(namespaceId, identifier, version, designations, definitions, null);
    }
}
