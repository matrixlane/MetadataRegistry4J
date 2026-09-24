package io.github.matrixlane.metadataregistry.web.dto.registrar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * add-registrar-three-elements：注册人员三要素编辑契约。
 *
 * <p>{@code organizationId} 必填（Registrar 必须挂在某个组织下）。
 * <p>{@code individualId} 可空：null 时保留现有关联；若同时传 {@code individualName}，按需创建/复用 Individual。
 * <p>{@code individualName} 可空：非空时按 individualName 创建新 Individual 或改现有 Individual.name；空时不影响。
 * <p>{@code roleId} 可空：null 时清空（角色为描述性元数据，add-submitter-contact-identity 已允许清空）。
 * <p>{@code registrationAuthorityId} 可空：null 时保留现有 RA 关联（编辑表单通常不修改 RA）。
 */
public record RegistrarRequest(
        @NotBlank(message = "标识符不能为空") String identifier,
        UUID registrationAuthorityId,
        UUID userId,
        @NotNull(message = "所属组织不能为空") UUID organizationId,
        UUID individualId,
        String individualName,
        UUID roleId) {
}
