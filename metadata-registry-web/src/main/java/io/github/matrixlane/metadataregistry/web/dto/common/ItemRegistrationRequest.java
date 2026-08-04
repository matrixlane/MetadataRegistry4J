package io.github.matrixlane.metadataregistry.web.dto.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ItemRegistrationRequest(
        UUID namespaceId,
        String identifier,
        String version,
        @NotEmpty(message = "designations 不能为空") @Valid
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
