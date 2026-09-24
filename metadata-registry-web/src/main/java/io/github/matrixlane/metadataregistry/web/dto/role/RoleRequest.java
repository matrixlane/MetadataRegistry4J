package io.github.matrixlane.metadataregistry.web.dto.role;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Role 属性的应用层收紧：
 * role_name 标准多重性为 0..1，管理 API 要求必填且唯一（见 design.md 偏离记录）。
 */
public record RoleRequest(
        @NotBlank(message = "角色名称不能为空") String roleName,
        String mailAddress,
        List<String> emailAddress,
        List<String> phoneNumber) {
}
