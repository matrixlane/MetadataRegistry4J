package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierInputMode;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineContextEntry;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.identifier.NamespaceIdentifierPolicyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Registry Context 初始化引导服务。
 *
 * <p>ISO/IEC 11179-3:2023 要求所有指称与定义必须至少关联一个上下文；当该条目本身是
 * 上下文时，标准允许通过自指（8.5.1.1 注、8.5.2.1 注）或设立一个 registry context 来避免
 * 无限回归。本服务按标准附录 C 的 {@code Context1} 示例创建注册表上下文
 * （Registry Context），其指称/定义的语境自指，并回填 RegistrySpecification.context。
 *
 * <p>创建时机：必须晚于初始化向导中标识/版本策略的配置，因此仅在向导 setup 末尾或
 * 已初始化系统的启动补建路径中调用。
 *
 * <p>除 Registry Context 外，本服务还承担存量已初始化库的注册机构 Primary 标志
 * 启动调和（{@link #backfillPrimaryAuthorityFlag()}）：旧库无 Primary 概念，启动时
 * 把 id 字典序最小的既有机构补标为 Primary（幂等）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryContextBootstrapService {

    /** Registry Context 的固定主键（仅作幂等键与系统级引用，不是 scoped identifier）。 */
    public static final UUID REGISTRY_CONTEXT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final String REGISTRY_CONTEXT_IDENTIFIER = "registry-context";
    private static final String REGISTRY_CONTEXT_VERSION = "1.0";
    /** 当前中文指称；早期版本为「注册系统上下文」，启动归一时自动更名 */
    private static final String REGISTRY_CONTEXT_ZH_DESIGNATION = "注册表上下文";
    private static final String LEGACY_ZH_DESIGNATION = "注册系统上下文";

    private final ContextRepository contextRepository;
    private final LanguageIdentificationRepository languageRepository;
    private final LanguageIdentifierResolver languageIdentifierResolver;
    private final ItemAdministrationService itemAdministrationService;
    private final RegistrySpecificationRepository registrySpecificationRepository;
    private final SystemSettingService systemSettingService;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final DesignationRepository designationRepository;
    private final NamespaceIdentifierPolicyResolver policyResolver;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseEnvironment databaseEnvironment;
    private final AdministeredItemRepository administeredItemRepository;
    /** 自注入代理：backfillIfInitialized 经代理调用 ensureRegistryContext，
     *  使 REQUIRES_NEW 事务传播生效（自调用绕过代理会导致创建非原子）。 */
    private final org.springframework.beans.factory.ObjectProvider<RegistryContextBootstrapService> selfProvider;

    /**
     * 幂等创建 Registry Context。
     *
     * @param ra 归属注册机构；必须已经拥有自己的命名空间（{@code ra.getNamespace()}）
     * @return 已存在则返回既有实体，否则返回新建实体
     * @throws IllegalStateException 当 {@code ra.getNamespace()} 为 null 时
     *
     * <p>调用方须保证在无外层事务时调用（向导 commit 事务提交后、启动期补建），
     * 使创建在独立事务中原子完成，且不污染调用方事务（内层失败不会把外层标记为
     * rollback-only）。SQLite 连接池为单连接，嵌套事务必然死锁。
     */
    @Transactional
    public Context ensureRegistryContext(RegistrationAuthority ra) {
        Namespace ns = ra.getNamespace();
        if (ns == null) {
            throw new IllegalStateException("registration authority has no namespace");
        }

        Optional<Context> existing = contextRepository.findById(REGISTRY_CONTEXT_ID);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 固定主键需要绕过 @GeneratedValue：先以原生 INSERT 创建 item/context 行，
        // 再加载为受管实体进行后续填写。
        insertRegistryContextSkeleton();
        Context managed = contextRepository.findById(REGISTRY_CONTEXT_ID)
                .orElseThrow(() -> new IllegalStateException("Registry Context _skeleton 插入后无法加载"));

        LanguageIdentification zh = findOrCreateLanguage("zh-CN", "Hans", "CN");
        LanguageIdentification en = findOrCreateLanguage("en-US", "Latn", "US");

        List<TextLine> designations = List.of(
                new TextLine(null, en.getId(), "Registry Context", null,
                        selfContext(), null, null, Acceptability.PREFERRED),
                new TextLine(null, zh.getId(), REGISTRY_CONTEXT_ZH_DESIGNATION, null,
                        selfContext(), null, null, Acceptability.PREFERRED)
        );
        List<TextLine> definitions = List.of(
                new TextLine(null, en.getId(),
                        "The default context for this registry: the setting in which designations are used to designate and definitions are used to define registry items.",
                        null, selfContext(), null, null, Acceptability.PREFERRED),
                new TextLine(null, zh.getId(),
                        "本注册表的上下文：指称用于指称注册项、定义用于定义注册项的语境。",
                        null, selfContext(), null, null, Acceptability.PREFERRED)
        );

        itemAdministrationService.applyDesignations(managed, designations, ns.getId());
        itemAdministrationService.applyDefinitions(managed, definitions);

        IdentifierInputMode inputMode = policyResolver.resolve(ns).inputMode();
        IdentifierInputMode versionInputMode = policyResolver.resolve(ns).versionInputMode();
        String identifier = inputMode == IdentifierInputMode.MANUAL_ONLY ? REGISTRY_CONTEXT_IDENTIFIER : null;
        String version = versionInputMode == IdentifierInputMode.MANUAL_ONLY ? REGISTRY_CONTEXT_VERSION : null;

        ItemRegistrationRequest req = new ItemRegistrationRequest(
                ns.getId(), identifier, version, designations, definitions);
        AdministeredItem ai = itemAdministrationService.ensureAdministeredItem(managed, req, ra);
        // 系统预置数据，不进入用户提交流程：注册状态直接为 Recorded（已由注册机构
        // 完整登记——标识/版本齐备；Incomplete 面向的是用户提交后逐步完善的工作流），
        // 管理状态置空（而非 ensureAdministeredItem 默认的 DRAFT），也不生成
        // SubmissionRecord（无人提交它）。工作空间草稿/待审核/已拒绝三视图均按
        // 管理状态过滤，置空后 Registry Context 自然排除。
        ai.getRegistrationState().setRegistrationStatus(
                io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus.RECORDED);
        ai.getRegistrationState().setAdministrativeStatus(null);
        administeredItemRepository.save(ai);
        contextRepository.save(managed);

        registrySpecificationRepository.findAll().stream()
                .findFirst()
                .ifPresent(spec -> {
                    if (spec.getContext() == null) {
                        spec.setContext(managed);
                        registrySpecificationRepository.save(spec);
                    }
                });

        return managed;
    }

    /**
     * 启动期存量调和入口（系统已初始化时执行）：先回填注册机构 Primary 标志
     * （任何已初始化库，幂等），再于 Registry Context 缺失时补建之。
     * 失败仅记录 warn，不阻止应用启动。
     */
    public void backfillIfInitialized() {
        if (!systemSettingService.isInitialized()) {
            return;
        }
        // 存量库 Primary 标志回填与 Registry Context 补建相互独立：上下文已存在的
        // 库也要执行，故先于存在性检查调用
        selfProvider.getObject().backfillPrimaryAuthorityFlag();
        if (contextRepository.existsById(REGISTRY_CONTEXT_ID)) {
            // 经代理调用使 @Transactional 生效（本方法会触碰 LAZY 集合）
            selfProvider.getObject().normalizeSystemPresetStatus();
            return;
        }

        Optional<RegistrationAuthority> raOpt = registrationAuthorityRepository.findAll().stream().findFirst();
        if (raOpt.isEmpty()) {
            log.warn("Registry Context 启动补建失败：系统中不存在注册机构");
            return;
        }

        RegistrationAuthority ra = raOpt.get();
        Namespace ns = ra.getNamespace();
        if (ns == null) {
            log.warn("Registry Context 启动补建失败：注册机构 {} 没有自有命名空间", ra.getId());
            return;
        }

        try {
            selfProvider.getObject().ensureRegistryContext(ra);
        } catch (Exception e) {
            log.warn("Registry Context 启动补建失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 存量归一：早期版本创建的 Registry Context 可能带 DRAFT 管理状态、INCOMPLETE
     * 注册状态并生成 SubmissionRecord（会出现在用户工作空间草稿中）；系统预置数据
     * 的注册状态必须为 Recorded、管理状态必须为空。中文指称由「注册系统上下文」
     * 更名为「注册表上下文」（与 RegistrySpecification.context 的语义一致）。
     * 重启时自动修正。须经代理调用以启用事务。
     */
    @Transactional
    public void normalizeSystemPresetStatus() {
        try {
            administeredItemRepository.findByItem_Id(REGISTRY_CONTEXT_ID).ifPresent(ai -> {
                var state = ai.getRegistrationState();
                if (state == null) {
                    return;
                }
                boolean changed = false;
                if (state.getAdministrativeStatus()
                        == io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus.DRAFT) {
                    state.setAdministrativeStatus(null);
                    changed = true;
                }
                if (state.getRegistrationStatus()
                        == io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus.INCOMPLETE) {
                    state.setRegistrationStatus(
                            io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus.RECORDED);
                    changed = true;
                }
                if (changed) {
                    administeredItemRepository.save(ai);
                    log.info("Registry Context 状态已归一（注册状态 Recorded、管理状态空，系统预置数据不进入提交流程）");
                }
            });

            contextRepository.findById(REGISTRY_CONTEXT_ID).ifPresent(ctx -> {
                for (var designation : ctx.getDesignations()) {
                    String sign = designation.getSign() == null ? null : designation.getSign().toString();
                    if (LEGACY_ZH_DESIGNATION.equals(sign)) {
                        designation.setSign(Sign.of(REGISTRY_CONTEXT_ZH_DESIGNATION));
                        designationRepository.save(designation);
                        log.info("Registry Context 中文指称已由「{}」更名为「{}」",
                                LEGACY_ZH_DESIGNATION, REGISTRY_CONTEXT_ZH_DESIGNATION);
                    }
                }
            });

            // getDefault() 以 RegistrySpecification.context 为默认上下文来源：老库若缺失则回填
            registrySpecificationRepository.findAll().stream().findFirst().ifPresent(spec -> {
                if (spec.getContext() == null) {
                    contextRepository.findById(REGISTRY_CONTEXT_ID).ifPresent(ctx -> {
                        spec.setContext(ctx);
                        registrySpecificationRepository.save(spec);
                        log.info("Registry Specification 默认上下文已回填为 Registry Context");
                    });
                }
            });
        } catch (Exception e) {
            log.warn("Registry Context 状态归一失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 存量库 Primary 标志回填（authority-contact-governance D1）：系统已初始化但
     * registration_authority 没有任何 Primary 行时，把 id 字典序最小的既有机构补标
     * Primary。选择口径与 {@code LanguageIdentifierResolver} 一致——UUID 规范文本的
     * 字典序等价于三方言 SQL {@code MIN(id)} 的 128 位无符号字节序。存量库通常只有
     * 一个机构（个人版历史 SINGLE 限建 1 个），多机构时该口径给出确定结果。
     *
     * <p>幂等：已有 Primary 行即跳过，二次启动不再改动。无机构行时静默跳过。
     * 异常仅记 warn，不阻止启动。须经代理调用以启用事务（REQUIRED，单事务，
     * SQLite 单连接池下禁止 REQUIRES_NEW 嵌套）。
     */
    @Transactional
    public void backfillPrimaryAuthorityFlag() {
        try {
            List<RegistrationAuthority> authorities = registrationAuthorityRepository.findAll();
            if (authorities.isEmpty() || authorities.stream().anyMatch(RegistrationAuthority::isPrimary)) {
                return;
            }
            RegistrationAuthority primary = authorities.stream()
                    .min(java.util.Comparator.comparing(
                            ra -> Optional.ofNullable(ra.getId()).map(UUID::toString).orElse("")))
                    .orElseThrow(() -> new IllegalStateException("注册机构列表非空但无最小 id 行"));
            primary.setPrimary(true);
            registrationAuthorityRepository.save(primary);
            log.info("存量注册机构已回填 Primary 标志：id={}（共 {} 个机构，原无任何 Primary 行）",
                    primary.getId(), authorities.size());
        } catch (Exception e) {
            log.warn("Primary 标志回填失败: {}", e.getMessage(), e);
        }
    }

    private void insertRegistryContextSkeleton() {
        Object uuid = databaseEnvironment.toJdbcUuid(REGISTRY_CONTEXT_ID);
        jdbcTemplate.update("INSERT INTO " + databaseEnvironment.qualify("item") + " (id) VALUES (?)", uuid);
        jdbcTemplate.update("INSERT INTO " + databaseEnvironment.qualify("context") + " (id) VALUES (?)", uuid);
    }

    private LanguageIdentification findOrCreateLanguage(String identifier, String script, String territory) {
        return languageIdentifierResolver.resolve(identifier).orElseGet(() -> {
            LanguageIdentification lang = new LanguageIdentification();
            lang.setLanguageIdentifier(identifier);
            lang.setScriptIdentifier(script);
            lang.setGeopoliticalTerritoryIdentifier(territory);
            return languageRepository.save(lang);
        });
    }

    private List<TextLineContextEntry> selfContext() {
        return List.of(new TextLineContextEntry(
                ItemAdministrationService.SELF_CONTEXT_REFERENCE, Acceptability.PREFERRED));
    }
}
