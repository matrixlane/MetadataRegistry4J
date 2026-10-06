package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfig;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.config.IdentifierStrategyLoader;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierStrategyConfigRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册机构自有命名空间开通服务：从 {@link InitWizardService} setup 第 5 步
 * （"Create default namespace if checked" 整段）抽取，实现逐行不变，供初始化
 * 向导与注册机构创建（ra-creation-wizard）在同一事务内复用。
 *
 * <p>语义与原块完全一致：名称缺省"默认命名空间"；versionPolicy 缺省 "SEMVER"；
 * identifierInputMode/versionInputMode 由调用方传规范化后的值（
 * {@link #normalizeInputMode}）；MANUAL_ONLY 输入模式下不创建标识策略配置；
 * 返回保存后的命名空间，{@code ra.setNamespace} 回挂由调用方完成并保存 RA
 * （与抽取前 InitWizardService 的行为一致：RA 的持久化节奏由调用方掌控）。</p>
 */
@Service
@RequiredArgsConstructor
public class NamespaceProvisioningService {

    private final NamespaceRepository namespaceRepository;
    private final NamespaceIdentifierSettingsService namespaceIdentifierSettingsService;
    private final IdentifierStrategyConfigRepository identifierStrategyConfigRepository;
    private final IdentifierStrategyLoader identifierStrategyLoader;

    /**
     * 为注册机构创建自有命名空间：指称、registrationAuthority/namingAuthority 双挂、
     * 标识设置（版本生成策略、标识/版本输入模式）写入卫星实体，非 MANUAL_ONLY 时再
     * 创建命名空间级 SEGMENT_TEMPLATE 标识策略配置（PUBLISHED、version=1、即生效）。
     *
     * @param ra 所属注册机构（已持久化）
     * @param namespaceName 命名空间名称，缺省 "默认命名空间"
     * @param versionPolicy 版本生成策略，缺省 "SEMVER"
     * @param identifierInputMode 标识输入模式，调用方传规范化后的值
     *                            （AUTO_ONLY/AUTO_WITH_OVERRIDE/MANUAL_ONLY）
     * @param versionInputMode 版本输入模式，调用方传规范化后的值
     * @param identifierTemplate 段模板 JSON，空则取系统默认模板
     * @return 保存后的命名空间（未回挂 ra.setNamespace，由调用方决定并保存）
     */
    @Transactional
    public Namespace provisionOwnNamespace(RegistrationAuthority ra, String namespaceName,
            String versionPolicy, String identifierInputMode, String versionInputMode,
            String identifierTemplate) {
        Namespace ns = new Namespace();

        String effectiveNamespaceName = namespaceName != null && !namespaceName.isBlank()
                ? namespaceName : "默认命名空间";
        Designation d = new Designation();
        d.setItem(ns);
        d.setSign(Sign.of(effectiveNamespaceName));
        ns.getDesignations().add(d);

        ns.setRegistrationAuthority(ra);
        ns.setNamingAuthority(ra);
        String versioningPolicy = versionPolicy != null && !versionPolicy.isBlank()
                ? versionPolicy : "SEMVER";

        Namespace nsSaved = namespaceRepository.save(ns);
        // 标识符治理扩展属性写入卫星实体(标准实体 Namespace 不再持有)
        namespaceIdentifierSettingsService.mutate(nsSaved.getId(), settings -> {
            settings.setVersioningPolicy(versioningPolicy);
            settings.setIdentifierInputMode(identifierInputMode);
            settings.setVersionInputMode(versionInputMode);
        });

        // MANUAL_ONLY 命名空间禁用自动生成，不创建策略配置
        if (!"MANUAL_ONLY".equals(identifierInputMode)) {
            createNamespaceStrategyConfig(nsSaved, null, identifierTemplate);
        }

        return nsSaved;
    }

    /**
     * 为创建的命名空间生成命名空间级标识生成策略配置
     * （PUBLISHED、version=1、即生效），并刷新策略注册表使其立即可用。
     */
    private void createNamespaceStrategyConfig(Namespace ns, String identifierStrategy,
            String identifierTemplate) {
        // 标识生成策略收敛为段模板唯一选择；UUID/顺序号/旧模板等旧值一律拒绝
        validateIdentifierStrategy(identifierStrategy);
        String raw = identifierTemplate;
        String configJson = raw == null || raw.isBlank()
                ? defaultSegmentTemplateJson() : raw.trim();
        validateSegmentTemplate(configJson);
        IdentifierStrategyConfig config = new IdentifierStrategyConfig("SEGMENT_TEMPLATE");
        config.setNamespace(ns);
        config.setConfigJson(configJson);
        config.setVersion(1);
        config.setIsGlobalDefault(false);
        config.setCreatedAt(java.time.LocalDateTime.now());
        config.setCreatedBy("init-wizard");
        config.publish();
        identifierStrategyConfigRepository.save(config);
        identifierStrategyLoader.refreshRegistry();
    }

    /**
     * 校验标识生成策略白名单：策略收敛为段模板唯一合法值，
     * UUID/顺序号/旧模板等旧值一律拒绝。
     *
     * <p>独立成静态方法供初始化向导在开通命名空间前预先拒绝旧策略值
     * （与原块在策略配置创建内拒绝等价：同一事务内回滚）。</p>
     */
    static void validateIdentifierStrategy(String identifierStrategy) {
        String strategy = identifierStrategy == null || identifierStrategy.isBlank()
                ? "SEGMENT_TEMPLATE" : identifierStrategy.trim().toUpperCase();
        if (!"SEGMENT_TEMPLATE".equals(strategy)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "标识生成策略仅支持段模板（SEGMENT_TEMPLATE），不支持的策略值: " + strategy);
        }
    }

    /**
     * 归一化输入模式值：空白或非法值回退 AUTO_WITH_OVERRIDE（兼容缺省调用方）。
     */
    static String normalizeInputMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "AUTO_WITH_OVERRIDE";
        }
        String normalized = raw.trim().toUpperCase();
        return java.util.Set.of("AUTO_ONLY", "AUTO_WITH_OVERRIDE", "MANUAL_ONLY").contains(normalized)
                ? normalized : "AUTO_WITH_OVERRIDE";
    }

    /**
     * 默认段模板：对象缩写 + 分隔符 + 顺序号（示例 DE.00001）；
     * 顺序号作用域自动按对象类型区分。
     */
    private static String defaultSegmentTemplateJson() {
        return """
                {"segments":[
                  {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":5,"overflow":"REJECT"}}
                ]}""";
    }

    private static void validateSegmentTemplate(String templateJson) {
        try {
            io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTemplate template =
                    io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTemplate.fromJson(templateJson);
            java.util.List<String> errors =
                    new io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTemplateEngine()
                            .validate(template);
            if (!errors.isEmpty()) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "段模板校验失败：" + String.join("；", errors));
            }
        } catch (io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTemplateException e) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "段模板解析失败：" + e.getMessage());
        }
    }
}
