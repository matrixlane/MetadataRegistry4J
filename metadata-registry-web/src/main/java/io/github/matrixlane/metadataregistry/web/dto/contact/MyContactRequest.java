package io.github.matrixlane.metadataregistry.web.dto.contact;

import java.util.UUID;

/**
 * "我的"自建联系人身份请求。
 *
 * <p>普通（非 Registrar）Contact：organizationId 必填；individualId/roleId 可空（创建时
 * 会从账号自身的 Individual 自动回填）。
 *
 * <p>Registrar 行：individualId/roleId/organizationId 均可编辑；organizationId 在 Registrar
 * 上下文中 MUST NOT 为空。
 */
public record MyContactRequest(
        @jakarta.validation.constraints.NotNull UUID organizationId,
        UUID roleId,
        UUID individualId) {
}
