package io.github.matrixlane.metadataregistry.web.dto.organization;

import java.util.UUID;

/**
 * 组织选项（add-registrar-three-elements）：用于"注册人员"编辑表单"组织"下拉。
 * 字段：(id, 主名)。英文名/标识等扩展元数据不暴露在前端下拉选项中。
 */
public record OrganizationOption(UUID id, String name) {
}
