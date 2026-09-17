package io.github.matrixlane.metadataregistry.web.dto.initwizard;

import jakarta.validation.constraints.NotBlank;

public record InitWizardSetupRequest(
    @NotBlank(message = "部署版本不能为空")
    String edition,

    String runningMode,

    String registrationAuthorityName,
    
    String registrationAuthorityEnglishName,
    
    String registrationAuthorityEmail,
    
    Boolean selfAsRegistrar,
    
    Boolean createNamespace,

    String namespaceName,

    String identifierStrategy,

    String identifierTemplate,

    String identifierInputMode,

    String versionInputMode,

    String versionPolicy,

    Boolean createRegistrySpecification,
    String registryName,
    String defaultLanguage,
    String registryDescription,
    InitWizardSpecAttributes registrySpecificationAttributes
) {}
