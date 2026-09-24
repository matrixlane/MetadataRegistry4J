package io.github.matrixlane.metadataregistry.web.dto.mapping;

import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingDegree;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 条目映射创建/更新请求。
 *
 * <p>忠实标准 11.4 多对多关联类形态：主体/客体各为 1..* 个绑定，
 * 每个绑定为「条目 + 可选映射变量名（转换规则中的变量引用）」。
 *
 * @param subjects                   主体绑定列表（≥1，条目须已存在）
 * @param objects                    客体绑定列表（≥1）
 * @param degree                     映射度（必填，标准小写值见 {@link MappingDegree}）
 * @param description                描述（可空）
 * @param transformationRule         转换规则（可空）
 * @param transformationRuleNotation 转换规则记法（rule 存在时必填，服务端校验）
 * @param registration               可选注册请求；携带时为映射条目建立受管条目外壳
 */
public record ItemMappingRequest(
        @NotEmpty @Size(max = 100) @Valid List<Endpoint> subjects,
        @NotEmpty @Size(max = 100) @Valid List<Endpoint> objects,
        @NotNull MappingDegree degree,
        @Size(max = 2000) String description,
        @Size(max = 4000) String transformationRule,
        @Size(max = 255) String transformationRuleNotation,
        @Valid ItemRegistrationRequest registration
) {

    /**
     * 一个端点绑定：条目 + 可选映射变量名。
     *
     * @param itemId       条目 UUID（必填，须为已存在条目）
     * @param variableName 该条目在转换规则中的映射变量名（mapping_variable_name，可空）
     */
    public record Endpoint(
            @NotNull UUID itemId,
            @Size(max = 255) String variableName
    ) {}
}
