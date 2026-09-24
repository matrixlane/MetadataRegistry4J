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
    InitWizardSpecAttributes registrySpecificationAttributes,

    /** 注册管理员姓名（Individual.name）；PERSONAL 默认"注册管理员"；TEAM 显式输入，可空。 */
    String adminIndividualName,
    /** 注册管理员角色（Role.roleName）；任一版本必填，PERSONAL 默认"注册管理员"。 */
    String adminRoleName,
    /** 注册管理员所属组织（Organization.id）；任一版本必填，PERSONAL 默认 = 新建 RA 的 id。 */
    java.util.UUID adminOrganizationId
) {}
