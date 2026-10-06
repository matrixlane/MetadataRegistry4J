package io.github.matrixlane.metadataregistry.web.dto.registrationauthority;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record RegistrationAuthorityRequest(
        @NotBlank(message = "name 不能为空")
        String name,
        String englishName,
        String mailAddress,
        List<String> emailAddress,
        List<String> phoneNumber,
        String webAddress,
        @NotBlank(message = "internationalCodeDesignator 不能为空")
        String internationalCodeDesignator,
        @NotBlank(message = "organizationIdentifier 不能为空")
        String organizationIdentifier,
        String organizationPartIdentifier,
        String opiSource,
        UUID namespaceId,
        List<UUID> documentationLanguageIds,
        /** ra-creation-wizard：同时担任注册人员开关。TEAM 团队版（含版本未设置）显式 true 时为当前账号建立 Registrar；缺省/false 不建立。PERSONAL 个人版忽略该值，恒建立。 */
        Boolean selfAsRegistrar,
        /** ra-creation-wizard：创建时是否同事务开通注册机构的自有命名空间。 */
        Boolean createNamespace,
        /** 自有命名空间名称，缺省 "默认命名空间"。 */
        String namespaceName,
        /** 标识输入模式（AUTO_ONLY/AUTO_WITH_OVERRIDE/MANUAL_ONLY），缺省 AUTO_WITH_OVERRIDE。 */
        String identifierInputMode,
        /** 版本输入模式（AUTO_ONLY/AUTO_WITH_OVERRIDE/MANUAL_ONLY），缺省 AUTO_WITH_OVERRIDE。 */
        String versionInputMode,
        /** 版本生成策略，缺省 SEMVER。 */
        String versionPolicy,
        /** 段模板标识策略 JSON，空则取系统默认模板。 */
        String identifierTemplate) {
}
