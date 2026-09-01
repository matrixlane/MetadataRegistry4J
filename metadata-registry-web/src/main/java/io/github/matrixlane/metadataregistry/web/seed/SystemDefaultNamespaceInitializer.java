package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;

import java.util.UUID;

/**
 * WI-028-C Phase 1: system-default Namespace 初始化
 *
 * <p>系统首次启动时自动创建 system-default Namespace。
 * 幂等：重复启动不重复创建。
 * 初始化失败不阻止应用启动（catch + log warn）。
 *
 * <p>属性：
 * <ul>
 *   <li>shorthand_prefix = "sys:"</li>
 *   <li>scheme_reference = "urn:metadata-registry:system-default"</li>
 *   <li>naming_authority = "system"</li>
 * </ul>
 *
 * <p>该 Namespace 仅用于系统内部对象和用户"试用"阶段，
 * 不强制 NamingConvention 校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SystemDefaultNamespaceInitializer implements ApplicationRunner {

    /** system-default Namespace 的唯一标识前缀 */
    public static final String SYSTEM_DEFAULT_PREFIX = "sys:";

    /** 内部技术 Namespace 的 URN 方案引用 */
    public static final String SYSTEM_DEFAULT_SCHEME = "urn:metadata-registry:system-default";

   private final NamespaceRepository namespaceRepository;
   private final LanguageIdentificationRepository languageRepository;
   private final OrganizationRepository organizationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseEnvironment databaseEnvironment;

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        initializeSystemDefaultNamespace();
    }

    @Transactional
    public void initializeSystemDefaultNamespace() {
        try {
            if (namespaceRepository.existsByShorthandPrefix(SYSTEM_DEFAULT_PREFIX)) {
                log.info("system-default Namespace 已存在，跳过初始化（幂等）");
                return;
            }

           Namespace ns = new Namespace();
           ns.setShorthandPrefix(SYSTEM_DEFAULT_PREFIX);
           ns.setSchemeReference(Sign.of(SYSTEM_DEFAULT_SCHEME));
            UUID systemOrgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            Organization systemOrg = organizationRepository.findById(systemOrgId)
                    .orElseGet(() -> {
                        jdbcTemplate.update(
                                "INSERT INTO " + databaseEnvironment.qualify("organization") + " (id) VALUES (?)",
                                databaseEnvironment.toJdbcUuid(systemOrgId));
                        Organization org = organizationRepository.findById(systemOrgId)
                                .orElseThrow(() -> new IllegalStateException("system organization not found after insert"));
                        org.getName().add(Sign.of("system"));
                        return organizationRepository.save(org);
                    });
           ns.setNamingAuthority(systemOrg);

            // 获取语言标识（默认取 en-US）
            LanguageIdentification langEn = createLanguage("en-US", "Latn", "US");
            LanguageIdentification langZh = createLanguage("zh-CN", "Hans", "CN");

            // 添加多语言 Designation
            io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation dEn =
                    new io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation();
            dEn.setItem(ns);
            dEn.setSign(Sign.of("system-default"));
            dEn.setLanguage(langEn);
            ns.getDesignations().add(dEn);

            io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation dZh =
                    new io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation();
            dZh.setItem(ns);
            dZh.setSign(Sign.of("系统默认命名空间"));
            dZh.setLanguage(langZh);
            ns.getDesignations().add(dZh);

            namespaceRepository.save(ns);
            log.info("WI-028-C: system-default Namespace 初始化成功 [shorthand_prefix={}]", SYSTEM_DEFAULT_PREFIX);
        } catch (Exception e) {
            log.warn("WI-028-C: system-default Namespace 初始化失败，不阻止应用启动: {}", e.getMessage(), e);
        }
    }

    private LanguageIdentification createLanguage(String identifier, String script, String territory) {
        return languageRepository.findByLanguageIdentifier(identifier)
                .orElseGet(() -> {
                    LanguageIdentification lang = new LanguageIdentification();
                    lang.setLanguageIdentifier(identifier);
                    lang.setScriptIdentifier(script);
                    lang.setGeopoliticalTerritoryIdentifier(territory);
                    return languageRepository.save(lang);
                });
    }
}
