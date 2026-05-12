package io.github.matrixlane.metadataregistry.web.dto.classification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新分类方案的请求体。
 */
@Data
public class ClassificationSchemeRequest {

    @NotBlank(message = "方案名称不能为空")
    @Size(max = 255, message = "方案名称长度不能超过255字符")
    private String schemeName;

    @Size(max = 4000, message = "描述长度不能超过4000字符")
    private String description;

    @Size(max = 50, message = "版本号长度不能超过50字符")
    private String version;

    @Size(max = 20, message = "状态长度不能超过20字符")
    private String status;
}
