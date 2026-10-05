package io.github.matrixlane.metadataregistry.web.dto.initwizard;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record InitWizardSetupRequest(
    @NotBlank(message = "部署版本不能为空")
    String edition,

    String registrationAuthorityName,

    String registrationAuthorityEnglishName,

    String registrationAuthorityEmail,

    /** ISO/IEC 11179-3:2023 表 8:international_code_designator (1, 必备)。个人版默认 "0000"。 */
    String registrationAuthorityIcd,

    /** ISO/IEC 11179-3:2023 表 8:organization_identifier (1, 必备)。个人版默认 "RA"。 */
    String registrationAuthorityOrganizationIdentifier,

    /** ISO/IEC 11179-3:2023 表 24:documentation_language_identifier (1..*, Language_Identification)。 */
    List<String> registrationAuthorityDocumentLanguages,

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

    /** 注册管理员姓名（Individual.name）；可空，空则初始化不创建 Individual（两版本一致）。 */
    String adminIndividualName,
    /** 注册管理员角色（Role.roleName）；创建 Registrar 时必填，PERSONAL 默认"注册管理员"。 */
    String adminRoleName
) {}
