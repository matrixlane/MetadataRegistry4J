package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.ext.registration.RegistrationAuthorityExtension;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardCommitRequest;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSetupRequest;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSpecAttributes;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardStatusResponse;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityExtensionRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.seed.StaticDataPresetService;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseCleaner;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseVendor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 5-step transactional init-wizard service.
 *
 * Step 1-4 (running mode → registry spec → registration authority → admin &
 * namespace) is the "setup" phase. The wizard writes business data during setup
 * but does NOT flip {@code system.initialized} — the system is still
 * considered un-initialised until the admin completes step 5 (set admin
 * password) which calls {@link #commit}. Any interruption (page reload,
 * tab switch, manual abort) must be safe to recover from, so the
 * {@link #abort} operation removes all business data written by setup and
 * resets the admin password to {@code NO_PASSWORD}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InitWizardService {

    private final SystemSettingService systemSettingService;
    private final io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStatusProvider statusProvider;
    private final RegistrarService registrarService;
    private final AccountRepository accountRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final RegistrationAuthorityExtensionRepository registrationAuthorityExtensionRepository;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceProvisioningService namespaceProvisioningService;
    private final RegistrySpecificationService registrySpecificationService;
    private final LanguageIdentificationRepository languageRepository;
    private final LanguageIdentifierResolver languageIdentifierResolver;
    private final StaticDataPresetService staticDataPresetService;
    private final ContactAccountService contactAccountService;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleaner databaseCleaner;
    private final RegistryContextBootstrapService registryContextBootstrapService;
    private final jakarta.persistence.EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseEnvironment databaseEnvironment;

    /**
     * WS 基准治理常量（baseline-data-init-rebuild，与 data/scripts 基准脚本同值）：
     * {@code ws-} 命名空间、WJW 标委会与统计信息中心两家机构；基准注册机构
     * （WJW）与同名 organization 行同 id。
     */
    private static final UUID BASELINE_WS_NAMESPACE_ID =
            UUID.fromString("2ACC2197-2BE5-49E4-BB16-3BFBEC5746F4");
    private static final UUID BASELINE_ORG_WJW_ID =
            UUID.fromString("6E486DAE-E5B0-42D7-A8E7-3C9C511BFD93");
    private static final UUID BASELINE_ORG_STATS_ID =
            UUID.fromString("9F649768-513A-4E42-910D-41B588F68E19");

    /**
     * 向导清理保留集（baseline-data-init-rebuild）：静态预置表 + 基准脚本
     * （通用 {@code data/scripts/common/<方言>/00-common.sql}；WS
     * {@code data/scripts/data-standards/ws/<方言>/10-main.sql + 20-derived.sql}）
     * 承载的内容链与衍生表 + 同链 FK 相关表 + 需排除式清空的治理表
     * （organization/registration_authority/namespace 系，其非基准行由
     * {@link #purgeNonBaselineGovernanceRows()} 清除）。
     *
     * <p>未初始化前所有写 API 被 498 拦截，向导窗口内这些表只可能存在基准行，
     * 整表保留不危及用户数据。</p>
     */
    private static final Set<String> WIZARD_CLEANUP_PRESERVED_TABLES = Set.of(
            // 静态预置与系统表
            "account", "system_setting", "flyway_schema_history",
            "language_identification", "ext_language_identification",
            // 内容链（基准脚本实际写入 + 同链 FK 相关表）
            "item", "concept", "conceptual_domain", "described_conceptual_domain",
            "enumerated_conceptual_domain", "local_enumerated_conceptual_domain",
            "value_domain", "described_value_domain", "enumerated_value_domain",
            "local_enumerated_value_domain", "data_element", "data_element_concept",
            "object_class", "property", "data_type", "data_type_scheme",
            "value_meaning", "permissible_value", "permissible_value_set",
            "permissible_value_meaning", "permissible_value_pvm",
            "designation", "designation_context", "designation_namespace",
            "definition", "definition_context", "designation_definition_pairing",
            "slot", "slot_value", "context",
            // 受管链与衍生
            "registered_item", "administered_item", "attached_item", "registration_state",
            "reference_document", "reference_document_language", "reference_document_provider",
            "registered_item_reference_document",
            "stewardship_record", "submission_record", "registered_item_submission_record",
            "contact", "scoped_identifier", "scoped_identifier_lifecycle",
            // 治理表：基准常量行保留，非基准行由 purge 补刀
            "organization", "organization_name", "organization_email", "organization_phone",
            "registration_authority", "registration_authority_doc_language",
            "ext_registration_authority",
            "namespace", "namespace_identifier_settings");

    /**
     * 查询初始化状态。新架构下判定来自本地状态记录（零数据库行为），
     * 因此本方法 MUST NOT 声明事务——@Transactional 会在进入方法时即向
     * 连接池取连接，破坏"向导相零数据库连接"的不变量。
     */
    public InitWizardStatusResponse getStatus() {
        // 状态判定经 statusProvider：新架构下读本地状态记录（零数据库查询），
        // 关闭时退化为数据库判定。setup/commit/abort 等完整相操作仍读库。
        DeploymentEdition edition = statusProvider.getEdition();
        return new InitWizardStatusResponse(
                statusProvider.isInitialized(),
                edition == null ? null : edition.name()
        );
    }

    /**
     * Discovers the current state of the wizard. Used by the frontend to
     * decide whether to resume a previous attempt, restart, or skip the
     * wizard entirely.
     *
     *   NOT_STARTED — system is fully initialised
     *   IN_PROGRESS — system is un-initialised and business data exists
     *   READY       — system is un-initialised, no business data yet
     */
    @Transactional(readOnly = true)
    public String getWizardState() {
        if (systemSettingService.isInitialized()) {
            return "NOT_STARTED";
        }
        // We treat "in progress" as: no business RA / namespace / spec yet,
        // but the admin account exists (which it always does after the first
        // DataInitializer run, so this heuristic is "is there any RA row?")
        // 预置基准库的基准治理常量行（ws- 命名空间 / WJW 注册机构）不构成
        // "向导进行中"（baseline-data-init-rebuild）：计数时排除基准常量 id。
        long raCount = registrationAuthorityRepository.findAll().stream()
                .filter(ra -> !BASELINE_ORG_WJW_ID.equals(ra.getId()))
                .count();
        long nsCount = namespaceRepository.findAll().stream()
                .filter(ns -> !BASELINE_WS_NAMESPACE_ID.equals(ns.getId()))
                .count();
        boolean specExists = registrySpecificationService.exists();
        if (raCount > 0 || nsCount > 0 || specExists) {
            return "IN_PROGRESS";
        }
        return "READY";
    }

    /**
     * Step 1-4: write the wizard's business data. Does NOT flip
     * {@code system.initialized} — that only happens in {@link #commit}.
     *
     * If any prior wizard attempt left dirty data, this method first cleans
     * it up (delegating to {@link #abort}) so that the wizard is idempotent
     * against repeated submissions.
     */
    @Transactional
    public void setup(InitWizardSetupRequest request, UUID appUserId) {
        if (systemSettingService.isInitialized()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "系统已经完成初始化");
        }

        // 部署版本解析与校验（CLOUD/未知值在此被拒绝）
        DeploymentEdition edition = DeploymentEdition.parse(request.edition());

        // Clear any leftover dirty data from a previous aborted attempt.
        clearBusinessDataOnly();

        // 静态预置统一入口（幂等兜底）：语言代码与中文名称、
        // 全局默认标识策略、占位管理员、标准注册流转规则。静态预置数据正常已由
        // 启动期（数据库设置完成并重启后）就绪，此处兜底保证 setup 重复提交、
        // 恢复出厂后重新初始化等路径下静态数据完整可用。
        staticDataPresetService.presetAll();

        // Guarantee commit() preconditions no matter how the system arrived at
        // the uninitialized state. E.g. a factory reset re-creates the admin
        // with a real password hash, which would otherwise make commit()
        // reject every attempt with no way to recover through the wizard.
        resetSuperAdminToNoPassword();

        // 持久化版本草稿；commit 时随 initialized 一起生效，abort 时清除。
        systemSettingService.setEdition(edition);

        Account account = accountRepository.findById(appUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", appUserId));

        // 版本驱动的有效配置：PERSONAL 名称类字段缺省时取默认值，治理布尔开关
        // 缺省（null）按 true 处理以保持向后兼容。命名空间为必选（D7：初始化
        // 必须创建注册机构的自有命名空间），显式 false 返回业务校验错误；
        // 注册规范同为必选。TEAM 使用表单值并校验必填。
        String raName = request.registrationAuthorityName();
        // selfAsRegistrar 缺省（null）视为关闭：API 入参保持简洁，省略即"不担任"。
        // 界面 MUST 显式提交该开关，其缺省呈现为开启（见 InitWizard.vue 的 form.selfAsRegistrar）。
        // PERSONAL 版另在下方无条件置 true（该版本不提供此开关）。
        boolean selfAsRegistrar = Boolean.TRUE.equals(request.selfAsRegistrar());
        String registryName = request.registryName();
        // createNamespace 语义改为强制 true：缺省与显式 true 正常执行，显式 false 拒绝
        if (Boolean.FALSE.equals(request.createNamespace())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "初始化必须创建注册机构的自有命名空间，不能跳过命名空间步骤");
        }
        boolean createNamespace = true;
        // 注册管理员三要素（individualName/roleName/organizationId）的版本差异化默认值。
        // 所属组织不来自请求：RegistrationAuthority extends Organization 且 JOINED 共表，
        // RA 本身即组织，初始化阶段系统内不存在任何其他组织。
        String adminIndividualName = request.adminIndividualName();
        String adminRoleName = request.adminRoleName();
        if (edition == DeploymentEdition.PERSONAL) {
            if (raName == null || raName.isBlank()) {
                // 注册机构(Registration_Authority)是组织机构层级；个人版下默认名为
                // "默认注册机构"。Registrar 是 admin 兼任的注册管理员，名称由绑定流程另赋，
                // 不应与组织机构同名。
                raName = "默认注册机构";
            }
            selfAsRegistrar = true;
            if (registryName == null || registryName.isBlank()) {
                // 注册规范名称：与注册机构名称解耦，使用稳定的默认字面值，避免
                // 跟着 RA 名称拼接成不可控的长串。
                registryName = "默认注册规范";
            }
            // PERSONAL 版三要素默认值：姓名留空（不创建 Individual，由用户后续在
            // "我的"中填写并由绑定流程回填 Registrar）+ 角色"注册管理员"+ 组织 = 新建 RA。
            // 姓名 MUST NOT 兜底——admin 未填写有效个人信息时系统中不应存在以其名义
            // 创建的 Individual（spec registrar-identity-elements：清空 → 不创建 Individual）。
            if (adminRoleName == null || adminRoleName.isBlank()) {
                adminRoleName = "注册管理员";
            }
        } else if (raName == null || raName.isBlank()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "注册机构名称不能为空");
        }
        // 标识符：表 8 international_code_designator 与 organization_identifier 标准 1 必备；
        // PERSONAL 缺省时兜底 "0000" / "RA"，TEAM 必须显式提交。
        String raIcd = request.registrationAuthorityIcd() != null && !request.registrationAuthorityIcd().isBlank()
                ? request.registrationAuthorityIcd().trim() : null;
        String raOrgId = request.registrationAuthorityOrganizationIdentifier() != null && !request.registrationAuthorityOrganizationIdentifier().isBlank()
                ? request.registrationAuthorityOrganizationIdentifier().trim() : null;
        if (edition == DeploymentEdition.PERSONAL) {
            if (raIcd == null) raIcd = "0000";
            if (raOrgId == null) raOrgId = "RA";
        }
        // 文档语言：表 24 documentation_language_identifier 标准 1..*；
        // 缺省时落到系统默认 zh-CN。
        java.util.List<String> requestedDocLangs = request.registrationAuthorityDocumentLanguages() != null
                ? request.registrationAuthorityDocumentLanguages().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList()
                : java.util.List.of();
        if (requestedDocLangs.isEmpty()) {
            requestedDocLangs = java.util.List.of("zh-CN");
        }
        // TEAM 版注册机构标识与文档语言必填（与是否创建 Registrar 无关）
        if (edition == DeploymentEdition.TEAM) {
            if (raIcd == null) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED, "注册机构国际代码标识符不能为空");
            }
            if (raOrgId == null) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED, "注册机构组织标识符不能为空");
            }
            if (requestedDocLangs.isEmpty()) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED, "注册机构文档语言不能为空");
            }
        }
        // 注册管理员角色仅在确实要创建 Registrar 时才必填：selfAsRegistrar 关闭即不创建
        // Registrar（PERSONAL 版已在上方强制为 true），此时不应要求其要素。
        // 所属组织不设校验也不接受请求参数——RegistrationAuthority extends Organization
        // 且 JOINED 共表，注册机构本身即组织，初始化阶段不存在其他组织可选。
        if (selfAsRegistrar && (adminRoleName == null || adminRoleName.isBlank())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "注册管理员角色不能为空");
        }
        // 注册规范为数据治理先决条件，两个版本均必选：向导设独立"配置注册规范"步骤
        // （治理步骤第一步，完整属性表单随 setup 提交，缺省值由表单预填、后端再兜底）；
        // 显式 false 视为契约违规，仍被拒绝。
        if (Boolean.FALSE.equals(request.createRegistrySpecification())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "注册规范为必选项，不能跳过创建");
        }

        // 1. 注册规范（必选，注册表固有对象，注册治理先决条件——先于注册机构创建）：
        // 向导步骤可携带表 28 全量标量属性，缺省时由 createDefault 按系统默认值兜底
        // （字符集 UTF-8、标准 ISO/IEC 11179-3:2023、时区 +0800、默认语言 zh-CN）。
        InitWizardSpecAttributes specAttrs = request.registrySpecificationAttributes();
        registrySpecificationService.createDefault(
                registryName,
                request.defaultLanguage() != null ? request.defaultLanguage() : "zh-CN",
                request.registryDescription(),
                specAttrs != null ? specAttrs.webAddress() : null,
                specAttrs != null ? specAttrs.characterRepertoire() : null,
                specAttrs != null ? specAttrs.standard() : null,
                specAttrs != null ? specAttrs.degreeOfConformance() : null,
                specAttrs != null ? specAttrs.conformanceLevel() : null,
                specAttrs != null ? specAttrs.conformanceLabel() : null,
                specAttrs != null ? specAttrs.includedFeatures() : null,
                specAttrs != null ? specAttrs.excludedFeatures() : null,
                specAttrs != null ? specAttrs.defaultTimeZone() : null,
                specAttrs != null ? specAttrs.alternativeLanguages() : null,
                specAttrs != null ? specAttrs.referenceDocumentIdentifierKinds() : null,
                specAttrs != null ? specAttrs.representationClassSchemeId() : null);

        // 2. Create RegistrationAuthority（初始化创建的注册机构即 Primary 注册机构）
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.getName().add(Sign.of(raName));
        if (request.registrationAuthorityEnglishName() != null && !request.registrationAuthorityEnglishName().isBlank()) {
            ra.getName().add(Sign.of(request.registrationAuthorityEnglishName()));
        }
        if (request.registrationAuthorityEmail() != null && !request.registrationAuthorityEmail().isBlank()) {
            ra.getEmailAddress().add(request.registrationAuthorityEmail());
        }

        RegistrationAuthorityIdentifier rai = new RegistrationAuthorityIdentifier();
        // 表 8:international_code_designator 与 organization_identifier 由向导提交值决定；
        // PERSONAL 缺省时上方已兜底 "0000" / "RA"（ISO/IEC 6523 未分配段，部署方应在 RA 设置页改为真实值）。
        rai.setInternationalCodeDesignator(raIcd);
        rai.setOrganizationIdentifier(raOrgId);
        ra.setRegistrationAuthorityIdentifier(rai);

        ra = registrationAuthorityRepository.save(ra);
        // Primary 标志为 ext 卫星行（mdr 实体不含该列，move-ra-primary-to-ext）：
        // 初始化创建的机构即 Primary，与 RA 同一事务写入。
        registrationAuthorityExtensionRepository.save(new RegistrationAuthorityExtension(ra.getId(), true));

        // 3. Assign self as registrar if checked
        if (selfAsRegistrar) {
            // Registrar 的组织恒为本次创建的 RA：RegistrationAuthority extends Organization
            // 且 JOINED 共表，注册机构本身即组织；初始化阶段无其他组织可选。
            Registrar adminRegistrar = registrarService.createRegistrarForUser(
                    account.getId(),
                    account.getUsername(),
                    adminIndividualName,
                    adminRoleName,
                    ra.getId(),
                    ra
            );
            // 首个 Registrar 身份自动标记为默认注册人员（ext.registrar.is_default）
            contactAccountService.ensureDefaultRegistrarWhenFirst(account.getId(), adminRegistrar.getId());
        }

        // 4. 表 24:documentation_language_identifier (1..*)。静态预置已保证内置 G20 语言
        // 存在，按 BCP 47 标识符命中复用既有 LanguageIdentification；仅当用户提交了
        // 非内置语言时才新建实体并 save。
        for (String langTag : requestedDocLangs) {
            LanguageIdentification lang = languageIdentifierResolver.resolve(langTag)
                    .orElseGet(() -> languageRepository.save(buildLanguageIdentification(langTag)));
            if (!ra.getDocumentationLanguageIdentifier().contains(lang)) {
                ra.getDocumentationLanguageIdentifier().add(lang);
            }
        }
        registrationAuthorityRepository.save(ra);

        // 5. Create default namespace if checked
        if (createNamespace) {
            // 标识生成策略白名单前置校验：UUID/顺序号/旧模板等旧值在开通命名空间前
            // 即拒绝，与原块在策略配置创建内拒绝等价（同一事务内回滚）。
            NamespaceProvisioningService.validateIdentifierStrategy(request.identifierStrategy());
            Namespace nsSaved = namespaceProvisioningService.provisionOwnNamespace(
                    ra,
                    request.namespaceName(),
                    request.versionPolicy(),
                    NamespaceProvisioningService.normalizeInputMode(request.identifierInputMode()),
                    NamespaceProvisioningService.normalizeInputMode(request.versionInputMode()),
                    request.identifierTemplate());
            ra.setNamespace(nsSaved);
            registrationAuthorityRepository.save(ra);
        }

        // NOTE: system.initialized stays FALSE; admin password is NOT reset
        // here. Those happen in commit().
    }

    /**
     * Step 5: the final commit. Sets {@code system.initialized=true} and —
     * 仅 TEAM 版 — persists the admin's real password. This is the only
     * operation that can take the system out of "un-initialised" state.
     *
     * <p>PERSONAL 版无登录概念（personal-no-login-registrar-default）：不接收、
     * 不校验、不持久化任何密码，admin 保持 NO_PASSWORD 占位；仅翻转初始化标志。
     */
    @Transactional
    public void commit(InitWizardCommitRequest request) {
        if (systemSettingService.isInitialized()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "系统已经完成初始化");
        }
        DeploymentEdition edition = systemSettingService.getEdition();
        if (edition == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "请先完成初始化向导配置（setup）");
        }
        if (edition == DeploymentEdition.PERSONAL) {
            // 个人版：密码概念不存在——忽略请求体中的密码字段（即便携带也不落库）。
            systemSettingService.setInitialized(true);
            markInitializedInStateRecord(edition);
            return;
        }

        if (request == null || request.newPassword() == null || request.newPassword().isBlank()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "新密码不能为空");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "两次输入的密码不一致");
        }
        if (request.newPassword().length() < 8 || request.newPassword().length() > 64) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "密码长度需在 8-64 个字符之间");
        }

        // The admin must currently be in the "NO_PASSWORD" state, since
        // setup() did not reset it. This guards against a stray commit call
        // after a normal admin session.
        UUID adminId = accountRepository.findAll().stream()
                .filter(a -> a.getSysRole() == io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole.SUPER_ADMIN)
                .findFirst()
                .orElseThrow(() -> new BusinessException("找不到超级管理员账户"))
                .getId();
        Account admin = accountRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", adminId));
        if (!"NO_PASSWORD".equals(admin.getPasswordHash())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "管理员当前不在 NO_PASSWORD 状态，请先通过 setup 重新发起向导");
        }

        // Persist the real password and flip the system flag. This is the
        // point of no return — after this, abort() is no longer valid.
        admin.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        admin.setForcePasswordReset(false);
        admin.setTempPasswordUsed(false);
        admin.setLastLoginAt(null); // fresh start
        accountRepository.save(admin);

        systemSettingService.setInitialized(true);
        markInitializedInStateRecord(edition);
    }

    /**
     * commit 成功后同步本地初始化状态记录（init-phase-database-free）：
     * "是否已初始化"由此不再依赖数据库查询。写入失败不阻断提交，但下次启动
     * 会按未初始化处理（用户重新提交一次即可，幂等）。
     */
    private void markInitializedInStateRecord(DeploymentEdition edition) {
        try {
            io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStateStore store =
                    new io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStateStore(
                            DatasourceConfigService.localConfigPath().resolveSibling("initialization-state.yml"));
            io.github.matrixlane.metadataregistry.web.bootstrap.InitializationState current =
                    store.read().orElse(io.github.matrixlane.metadataregistry.web.bootstrap.InitializationState.fresh());
            store.write(current
                    .withInitialized(true)
                    .withEdition(edition == null ? null : edition.name()));
        } catch (Exception e) {
            log.warn("初始化状态记录写入失败（下次启动将按未初始化处理）: {}", e.getMessage());
        }
    }

    /**
     * 初始化向导完成后创建/回填 Registry Context（注册表上下文，语境自指）。
     *
     * <p>必须在 {@link #commit} 事务提交之后、无外层事务时调用（Controller 组合调用）：
     * Registry Context 的创建需要独立事务——SQLite 连接池为单连接，嵌套事务必然
     * 死锁；且在向导事务内创建既对独立事务不可见（setup 数据未提交），失败又会
     * 把外层事务标记为 rollback-only。失败仅 log warn，不阻断初始化；重启后由
     * 启动补建路径兜底。
     */
    public void createRegistryContextAfterInitialization() {
        try {
            registrationAuthorityRepository.findAll().stream()
                    .findFirst()
                    .ifPresentOrElse(
                            registryContextBootstrapService::ensureRegistryContext,
                            () -> log.warn("Registry Context 创建跳过：系统中不存在注册机构"));
        } catch (Exception e) {
            log.warn("Registry Context 创建失败，可在重启后自动补建: {}", e.getMessage(), e);
        }
    }

    /**
     * Abort the wizard. Removes all business data written by setup() but
     * preserves the admin account (reset to NO_PASSWORD). Safe to call
     * multiple times.
     */
    @Transactional
    public void abort() {
        if (systemSettingService.isInitialized()) {
            // Already initialised — nothing to abort.
            return;
        }
        clearBusinessDataOnly();
        // 清理会连带删除全局默认标识策略等静态预置行
        // （language 两表在保留集内），此处按统一入口幂等恢复，保证 abort 后
        // 静态预置数据完整可用。
        staticDataPresetService.presetAll();
        // 清除版本草稿，回到完全未初始化状态。
        systemSettingService.clearEdition();
        // Reset admin back to NO_PASSWORD.
        resetSuperAdminToNoPassword();
    }

    /**
     * Reset the SUPER_ADMIN account back to the NO_PASSWORD bootstrap state.
     * Used by both {@link #setup} (to guarantee commit preconditions) and
     * {@link #abort} (the user-driven interrupt path).
     */
    private void resetSuperAdminToNoPassword() {
        accountRepository.findAll().stream()
                .filter(a -> a.getSysRole() == io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole.SUPER_ADMIN)
                .forEach(admin -> {
                    admin.setPasswordHash("NO_PASSWORD");
                    admin.setForcePasswordReset(false);
                    admin.setTempPasswordUsed(false);
                    admin.setLastLoginAt(null);
                    // clearBusinessDataOnly 已清空 individual 等业务表，解除可能悬空的
                    // Individual 关联，避免重试 setup / abort 后出现外键悬挂。
                    admin.setIndividual(null);
                    accountRepository.save(admin);
                });
    }

    /**
     * Remove business data created by setup() while preserving the admin
     * account, the Flyway schema history, and the WS baseline data carried by
     * 预置基准库 / 基准脚本（baseline-data-init-rebuild）。Used by both
     * {@link #setup} (to clear dirty state from previous aborted attempts)
     * and {@link #abort} (the user-driven interrupt path).
     *
     * <p>内容链/衍生表整表保留（未初始化前写 API 均被 498 拦截，向导窗口内
     * 这些表只可能存在基准行）；治理表（organization/registration_authority/
     * namespace 系）带基准常量排除清空，见 {@link #purgeNonBaselineGovernanceRows()}。</p>
     */
    private void clearBusinessDataOnly() {
        // account 是保留表，但其 individual_id 外键指向被清空的 individual 业务表。
        // 方言策略只对参与清空的表停用约束（SQL Server 的 NOCHECK CONSTRAINT ALL
        // 按表下发），保留表上的 FK 仍处于启用状态，会在 DELETE FROM individual 时
        // 冲突——setup 首次成功后 account 已被绑定 Individual（registrar 创建流程），
        // 因此"下一步→上一步→下一步"的重试与 abort 都会在清空阶段 500。
        // 先解除保留表对业务表的引用再清空，保证两条路径幂等。
        detachAccountIndividualReferences();
        // Dialect-portable cleanup: table discovery via JDBC metadata and
        // FK handling via the active vendor's strategy (see DatabaseCleaner).
        // 保留集见 WIZARD_CLEANUP_PRESERVED_TABLES：语言/系统表、基准内容与衍生表、
        // 待排除式清空的治理表；其余业务表清空后由 presetAll() 幂等兜底恢复静态行。
        databaseCleaner.clearTables(WIZARD_CLEANUP_PRESERVED_TABLES);
        purgeNonBaselineGovernanceRows();
        // 裸 JDBC DELETE 绕开持久化上下文：被清空表中的托管实体不会随之脱离。
        // 残留实例既可能在同一事务内重复 setup 时以相同 id 阻塞 re-persist
        // （SQLite rowid 清空后复用，典型如 IdentifierStrategyConfig），也会让
        // 后续读取拿到已删除的行。清空后统一驱逐，后续代码全部按全新状态加载。
        entityManager.clear();
    }

    /**
     * 治理表的排除式清空（baseline-data-init-rebuild）：删除向导 setup/半途留下的
     * 非基准治理行，保留基准常量行（ws- 命名空间、WJW/统计信息中心机构、WJW 注册机构）。
     *
     * <p>FK 顺序约束：Namespace extends Item（JOINED，item 父行须连带删除）；
     * registration_authority.namespace_id → namespace 且 namespace.registration_authority_id
     * → registration_authority 形成环，先删 RA 侧解除对 namespace 的引用；registration_authority
     * 与 organization JOINED 共主键，RA 删净后方可删 organization。方言感知经
     * {@link DatabaseEnvironment} 限定表名与 UUID 绑定（SQLite/达梦 BLOB、SQL Server 字符串）。</p>
     */
    private void purgeNonBaselineGovernanceRows() {
        Object keepNs = databaseEnvironment.toJdbcUuid(BASELINE_WS_NAMESPACE_ID);
        Object keepRa = databaseEnvironment.toJdbcUuid(BASELINE_ORG_WJW_ID);
        Object keepOrgWjw = databaseEnvironment.toJdbcUuid(BASELINE_ORG_WJW_ID);
        Object keepOrgStats = databaseEnvironment.toJdbcUuid(BASELINE_ORG_STATS_ID);

        // 1. 非基准 namespace（先捕获 id 清单：namespace 主行删除后 item 父行仍须按
        //    同一清单补删，子查询届时已查不到行）
        List<Object> deadNsIds = jdbcTemplate.queryForList(
                "SELECT id FROM " + tStd("namespace") + " WHERE id <> ?", Object.class, keepNs);
        if (!deadNsIds.isEmpty()) {
            String in = String.join(", ", java.util.Collections.nCopies(deadNsIds.size(), "?"));
            Object[] nsArgs = deadNsIds.toArray();
            // designation/definition/slot 整表在保留集内：只删 item_id 指向死
            // namespace 的行，基准 ws- 命名空间的指称/槽不受影响
            jdbcTemplate.update("DELETE FROM " + tStd("designation") + " WHERE item_id IN (" + in + ")", nsArgs);
            jdbcTemplate.update("DELETE FROM " + tStd("definition") + " WHERE item_id IN (" + in + ")", nsArgs);
            jdbcTemplate.update("DELETE FROM " + tStd("slot") + " WHERE item_id IN (" + in + ")", nsArgs);
            jdbcTemplate.update("DELETE FROM " + tAuto("namespace_identifier_settings")
                    + " WHERE namespace_id IN (" + in + ")", nsArgs);
        }

        // 2. 非基准 RA 侧（registration_authority.namespace_id → namespace，先删 RA
        //    解除其对 namespace 的引用；集合表与 ext 卫星先行）
        String deadRa = "SELECT id FROM " + tStd("registration_authority") + " WHERE id <> ?";
        jdbcTemplate.update("DELETE FROM " + tStd("registration_authority_doc_language")
                + " WHERE registration_authority_id IN (" + deadRa + ")", keepRa);
        jdbcTemplate.update("DELETE FROM " + tRaExtension() + " WHERE id IN (" + deadRa + ")", keepRa);
        jdbcTemplate.update("DELETE FROM " + tStd("registration_authority") + " WHERE id <> ?", keepRa);

        // 3. 非基准 namespace 主行与 item 父行（ns.registration_authority_id → RA 已清）
        if (!deadNsIds.isEmpty()) {
            String in = String.join(", ", java.util.Collections.nCopies(deadNsIds.size(), "?"));
            Object[] nsArgs = deadNsIds.toArray();
            jdbcTemplate.update("DELETE FROM " + tStd("namespace") + " WHERE id IN (" + in + ")", nsArgs);
            jdbcTemplate.update("DELETE FROM " + tStd("item") + " WHERE id IN (" + in + ")", nsArgs);
        }

        // 4. 非基准 organization 侧（RA 已删，JOINED 共主键不再阻塞；集合表先行）
        String deadOrg = "SELECT id FROM " + tStd("organization") + " WHERE id NOT IN (?, ?)";
        jdbcTemplate.update("DELETE FROM " + tStd("organization_name")
                + " WHERE organization_id IN (" + deadOrg + ")", keepOrgWjw, keepOrgStats);
        jdbcTemplate.update("DELETE FROM " + tStd("organization_email")
                + " WHERE organization_id IN (" + deadOrg + ")", keepOrgWjw, keepOrgStats);
        jdbcTemplate.update("DELETE FROM " + tStd("organization_phone")
                + " WHERE organization_id IN (" + deadOrg + ")", keepOrgWjw, keepOrgStats);
        jdbcTemplate.update("DELETE FROM " + tStd("organization") + " WHERE id NOT IN (?, ?)",
                keepOrgWjw, keepOrgStats);
    }

    /** 标准 schema 表限定名（SQLite 为裸名）。 */
    private String tStd(String table) {
        return databaseEnvironment.qualifyStandard(table);
    }

    /** EXT 表限定名（ext schema 路由；SQLite 为裸名）。 */
    private String tAuto(String table) {
        return databaseEnvironment.qualifyAuto(table);
    }

    /**
     * RA Primary 标志卫星表限定名（V20261008001）：团队库与标准表同名
     * （ext.registration_authority），SQLite 物理名 ext_registration_authority。
     * 与 RegistrationAuthorityDeletionService 同名表路由约束一致。
     */
    private String tRaExtension() {
        return databaseEnvironment.vendor() == DatabaseVendor.SQLITE
                ? databaseEnvironment.qualify("ext_registration_authority")
                : databaseEnvironment.qualifyExt("registration_authority");
    }

    private void detachAccountIndividualReferences() {
        accountRepository.findAll().forEach(account -> {
            if (account.getIndividual() != null) {
                account.setIndividual(null);
                accountRepository.save(account);
            }
        });
        // save() 只把实体标脏、并不写库；UPDATE 必须先行落库，
        // 随后的裸 DELETE 才不会撞上 account.individual_id 上仍启用中的外键。
        entityManager.flush();
    }

    /**
     * 把 BCP 47 语言标签解析为最小可用 {@link LanguageIdentification} 实体并落库。
     * 仅在用户提交的文档语言未在系统内置语言表中时调用——主流程已存在的内置语言
     * （zh-CN / en-US / ja-JP 等 G20 国家）由 V20260919003 预置脚本写入，
     * 此处只负责新建向导中用户首次引入、且非系统预置的语言。
     */
    private LanguageIdentification buildLanguageIdentification(String langTag) {
        LanguageIdentification lang = new LanguageIdentification();
        lang.setLanguageIdentifier(langTag);
        // script 与 region 留空：未在 G20 预置范围内的语言仅记 languageIdentifier，
        // 后续 LanguageGovernanceService 激活时再补齐。
        return lang;
    }
}
