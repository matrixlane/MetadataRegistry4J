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
 *
 * <p>kind 仅创建时生效：kind=REGISTRAR 时创建 Registrar 实体（需超级管理员权限，
 * 两版本一致）；缺省按 CONTACT 处理。编辑时类型不可变更（类型即身份）。
 */
public record MyContactRequest(
        @jakarta.validation.constraints.NotNull UUID organizationId,
        UUID roleId,
        UUID individualId,
        ContactIdentityKind kind) {
}
