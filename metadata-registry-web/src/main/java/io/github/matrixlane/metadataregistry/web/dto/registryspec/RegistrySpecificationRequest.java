package io.github.matrixlane.metadataregistry.web.dto.registryspec;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegistrySpecificationRequest(
    @NotBlank(message = "注册表名称不能为空")
    String registryName,
    
    String identifierPrefix,
    
    @NotNull(message = "是否自动生成标识符不能为空")
    Boolean autoGenerateIdentifier,
    
    String identifierPattern,
    
    String versioningPolicy,
    
    @NotNull(message = "是否强制分类不能为空")
    Boolean mandatoryClassification,
    
    @NotBlank(message = "默认语言不能为空")
    String defaultLanguage,
    
    String description
) {}
