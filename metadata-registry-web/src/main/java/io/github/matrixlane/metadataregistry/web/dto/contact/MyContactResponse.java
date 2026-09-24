package io.github.matrixlane.metadataregistry.web.dto.contact;

import java.util.UUID;

/**
 * "我的"联系人身份视图（add-submitter-contact-identity）：账号绑定的全部 Contact
 * 身份（含 Registrar 行），含默认标志与 Registrar 所属注册机构摘要。
 */
public record MyContactResponse(
        UUID id,
        UUID organizationId,
        String organizationName,
        UUID individualId,
        String individualName,
        UUID roleId,
        String roleName,
        boolean isRegistrar,
        UUID registrationAuthorityId,
        String registrationAuthorityName,
        boolean isDefault) {
}
