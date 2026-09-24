package io.github.matrixlane.metadataregistry.web.dto.mapping;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;

import java.util.List;
import java.util.UUID;

/**
 * 条目映射响应。
 *
 * @param id                       映射条目 UUID
 * @param degree                   映射度（标准小写值）
 * @param description              描述
 * @param transformationRule       转换规则
 * @param transformationRuleNotation 转换规则记法
 * @param subjects                 主体绑定列表（各含条目与映射变量名）
 * @param objects                  客体绑定列表
 * @param registrationStatus       映射条目自身的注册状态（未注册为 null）
 */
public record ItemMappingResponse(
        UUID id,
        MappingDegree degree,
        String description,
        String transformationRule,
        String transformationRuleNotation,
        List<EndpointRef> subjects,
        List<EndpointRef> objects,
        RegistrationStatus registrationStatus
) {

    /**
     * 映射端点绑定（Subject_Mapping/Object_Mapping 关联类）。
     *
     * @param itemId       条目 UUID
     * @param variableName 转换规则中的映射变量名（mapping_variable_name，可空）
     */
    public record EndpointRef(
            UUID itemId,
            String variableName
    ) {}
}
