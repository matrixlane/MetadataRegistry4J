package io.github.matrixlane.metadataregistry.web.dto.contact;

import java.util.UUID;

/** "我的"自建联系人身份请求（organizationId 必填；roleId 可选，纯描述性元数据——
 *  Registrar 身份由 Contact 子类 Registrar 实例确定，与 Role 无关）。 */
public record MyContactRequest(@jakarta.validation.constraints.NotNull UUID organizationId, UUID roleId) {
}
