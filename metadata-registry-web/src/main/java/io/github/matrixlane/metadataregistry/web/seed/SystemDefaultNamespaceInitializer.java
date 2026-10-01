package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import io.github.matrixlane.metadataregistry.web.service.LanguageIdentifierResolver;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;

import java.util.List;
import java.util.UUID;

/**
 * WI-028-C Phase 1: system-default Namespace 初始化
 *
 * <p>由 {@link StaticDataPresetService} 统一编排执行（启动期 / 恢复出厂 /
 * 初始化向导 setup 与 abort 兜底，复用同一顺序），不再作为独立启动 Runner。
 * 幂等：重复执行不重复创建。
 * 失败向上抛出、由调用方事务回滚——不再静默吞异常，避免"返回成功但静态预置
 * 半缺失"（统一入口其余步骤均为 fail-fast 语义）。
 *
 * <p>属性：
 * <ul>
 *   <li>shorthand_prefix = "sys:"</li>
 *   <li>scheme_reference = "urn:metadata-registry:system-default"</li>
 *   <li>naming_authority = 初始化创建的注册机构（默认注册机构）；未初始化时留空，
 *       setup 再次调用本入口时完成挂接</li>
 * </ul>
 *
 * <p>该 Namespace 仅用于系统内部对象和用户"试用"阶段，
 * 不强制 NamingConvention 校验。
 *
 * <p>历史系统组织（固定 UUID …0001）自 2026-10 起不再预置创建：
 * NamingAuthority 直接挂到注册机构；存量库由 {@link #initializeSystemDefaultNamespace()}
 * 内的调和逻辑改挂注册机构并移除无引用的历史系统组织行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemDefaultNamespaceInitializer {

    /** system-default Namespace 的唯一标识前缀 */
    public static final String SYSTEM_DEFAULT_PREFIX = "sys:";

    /** 内部技术 Namespace 的 URN 方案参考 */
    public static final String SYSTEM_DEFAULT_SCHEME = "urn:metadata-registry:system-default";

    /** 历史系统组织固定 UUID（2026-10 起不再预置创建；存量无引用行由启动调和移除）。
     *  OrganizationService 的列表过滤与删除保护仍引用此值，为尚未完成调和的存量库兜底。 */
    public static final UUID SYSTEM_ORGANIZATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

   private final NamespaceRepository namespaceRepository;
   private final LanguageIdentificationRepository languageRepository;
   private final LanguageIdentifierResolver languageIdentifierResolver;
   private final OrganizationRepository organizationRepository;
   private final RegistrationAuthorityRepository registrationAuthorityRepository;
   private final JdbcTemplate jdbcTemplate;
   private final DatabaseEnvironment databaseEnvironment;

    @Transactional
    public void initializeSystemDefaultNamespace() {
        Namespace ns = namespaceRepository.findByShorthandPrefix(SYSTEM_DEFAULT_PREFIX).orElse(null);
        if (ns == null) {
            ns = buildNamespace();
            ns = namespaceRepository.save(ns);
            log.info("WI-028-C: system-default Namespace 初始化成功 [shorthand_prefix={}]", SYSTEM_DEFAULT_PREFIX);
        }
        reconcileNamingAuthority(ns);
        removeLegacySystemOrganizationIfOrphaned();
    }

    private Namespace buildNamespace() {
       Namespace ns = new Namespace();
       ns.setShorthandPrefix(SYSTEM_DEFAULT_PREFIX);
       ns.setSchemeReference(Sign.of(SYSTEM_DEFAULT_SCHEME));

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

        return ns;
    }

    /**
     * NamingAuthority 调和：sys: 命名空间的 NamingAuthority 默认挂到注册机构
     * （初始化向导创建的"默认注册机构"）。未初始化（无注册机构）时留空，
     * 向导 setup 第 3.6 步再次调用本入口时完成挂接。历史库若仍指向系统组织，
     * 一并改挂注册机构。
     */
    private void reconcileNamingAuthority(Namespace ns) {
        List<RegistrationAuthority> authorities = registrationAuthorityRepository.findAll();
        if (authorities.isEmpty()) {
            return;
        }
        RegistrationAuthority ra = authorities.get(0);
        Organization current = ns.getNamingAuthority();
        boolean missing = current == null;
        boolean legacySystem = current != null && SYSTEM_ORGANIZATION_ID.equals(current.getId());
        if (missing || legacySystem) {
            ns.setNamingAuthority(ra);
            namespaceRepository.save(ns);
            log.info("sys: 命名空间 NamingAuthority 已挂接注册机构 [raId={}]", ra.getId());
        }
    }

    /**
     * 移除历史系统组织预置行：仅当 system 组织已无任何业务引用
     * （sys: 命名空间 NamingAuthority、联系人所属组织）时删除；
     * 名称/邮箱/电话等子表行随组织行一并删除，避免外键冲突。
     */
    private void removeLegacySystemOrganizationIfOrphaned() {
        if (!organizationRepository.existsById(SYSTEM_ORGANIZATION_ID)) {
            return;
        }
        Object uuid = databaseEnvironment.toJdbcUuid(SYSTEM_ORGANIZATION_ID);
        Integer nsRefs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + databaseEnvironment.qualify("namespace")
                        + " WHERE naming_authority_id = ?",
                Integer.class, uuid);
        Integer contactRefs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + databaseEnvironment.qualify("contact")
                        + " WHERE organization_id = ?",
                Integer.class, uuid);
        if (nsRefs == null || nsRefs > 0 || contactRefs == null || contactRefs > 0) {
            return;
        }
        jdbcTemplate.update("DELETE FROM " + databaseEnvironment.qualify("organization_name")
                + " WHERE organization_id = ?", uuid);
        jdbcTemplate.update("DELETE FROM " + databaseEnvironment.qualify("organization_email")
                + " WHERE organization_id = ?", uuid);
        jdbcTemplate.update("DELETE FROM " + databaseEnvironment.qualify("organization_phone")
                + " WHERE organization_id = ?", uuid);
        jdbcTemplate.update(
                "DELETE FROM " + databaseEnvironment.qualify("organization") + " WHERE id = ?",
                uuid);
        log.info("已移除历史系统组织预置行（sys: 命名空间 NamingAuthority 已由注册机构接管）");
    }

    private LanguageIdentification createLanguage(String identifier, String script, String territory) {
        return languageIdentifierResolver.resolve(identifier)
                .orElseGet(() -> {
                    LanguageIdentification lang = new LanguageIdentification();
                    lang.setLanguageIdentifier(identifier);
                    lang.setScriptIdentifier(script);
                    lang.setGeopoliticalTerritoryIdentifier(territory);
                    return languageRepository.save(lang);
                });
    }
}
